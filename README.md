# project-analysis-java

Java 工程静态分析工具。以**交易入口**为起点自上而下构建方法调用链，输出调用次数分析、样板方法过滤治理与中文 Excel 报告。

纯源码静态分析（ASM 字节码 + 调用方解析），**零出站网络**，默认**仅绑定本机** `127.0.0.1`。

---

## 功能概览

### 1. 项目导入

| 方式 | 说明 |
|---|---|
| 本地目录 | 指向已编译的工程目录，或直接指向 `.jar`（fat jar 也可） |
| Git 仓库 | 自动 克隆 → `mvn compile` → 扫描交易入口；支持分支 / Tag 切换与远端更新检测 |

- 工程布局自动识别：Maven（单/多模块）、Gradle、fat jar（`BOOT-INF/*`）、`classes + lib` 目录
- 本地 Maven 仓库自动定位（`callgraph.maven.repo` → `CALLGRAPH_M2_REPO` → `~/.m2/settings.xml` → Maven 安装目录 → 默认 `~/.m2/repository`）
- 项目列表按最近打开时间倒序；移除项目**只删注册记录，不动磁盘文件**

### 2. 交易入口清单

清单是分析的真实输入，两种来源最终汇入同一份清单：

- **自动扫描**：按当前扫描策略跑全部探测器，结果以弹窗列出，由你勾选后加入
- **手动添加**：填类名 → 点「扫描」→ 按策略规则列出该类匹配到的方法 → 勾选后批量加入
- 自动 / 手动都会去重，并提示「新增 N 个、已存在 M 个」
- 支持排除（附排除原因）与恢复；清单行左侧有纯展示序号便于对照

### 3. 扫描策略（扫描方案）

决定"哪些方法算交易入口"。内置 3 个方案随 jar 打包，升级自动刷新：

| 方案 | 覆盖入口类型 |
|---|---|
| 标准扫描 | REST + Dubbo + 定时任务 + Main |
| 纯 API 扫描 | 仅 REST |
| 定时任务扫描 | 仅 ElasticJob |

- 内置方案**本体只读**（改不了也删不掉），需要调整就「复制」出自定义方案
- 自定义方案可新建 / 复制 / 删除，支持**导出 JSON 分享给他人导入**（按方案 id 合并，同 id 覆盖）
- 全局层与项目层分离，项目层存在即覆盖全局；内置方案只从代码取，不落盘

入口探测器为 SPI（`EntryPointDetector`），新增类型只需实现接口 + `@Component` 注册：
`REST` / `DUBBO` / `ELASTIC_JOB` / `MAIN`。自定义规则支持按**注解 / 包 / 接口实现 / 类名正则 / 方法名正则**匹配，并可用 `*`、`**` 通配写排除项。

### 4. 过滤规则（样板方法治理）

把 `getInstance`、`toString`、日志、getter/setter 这类样板方法从调用链和统计里剪掉。**两个层级**：

| 层级 | 位置 | 能力 |
|---|---|---|
| 全局 | 系统配置 → 过滤规则 | 7 条内置默认规则 + 自定义规则；启用/禁用；导出/导入 JSON |
| 项目级 | 分析视图按钮 | 对全局规则做**三态覆盖**（继承全局 / 强制启用 / 强制禁用）+ 本项目专属自定义规则 |

内置 7 条默认规则（随 jar 打包，升级即刷新）：

| 规则 | 匹配 |
|---|---|
| 单例/工厂 | `getInstance`、`getBean`、`getBeanFactory`、`getInstance*` |
| 构造器 | `<init>` |
| Object 方法 | `toString`、`equals`、`hashCode`、`getClass`、`clone`、`finalize` |
| 日志 | 方法名含 `info/debug/error/warn/trace/fatal` 且类名含 `Logger/Log/Slf4j` |
| Spring 生命周期 | `afterPropertiesSet`、`initMethod`、`destroy`、`initialize`、`dispose`、`onApplicationEvent`、`postProcess*` |
| 简单 getter | 0 参的 `getXxx` / `isXxx`（仅项目内方法） |
| 简单 setter | 1 参的 `setXxx`（仅项目内方法） |

规则字段：方法名正则 / 类名正则 / 来源（`ALL` `PROJECT` `DEPENDENCY` `EXTERNAL`）/ 参数个数（区分重载）/ 启用开关。

**过滤规则是视图层剪枝**：命中规则的节点及其整棵子树从调用链消失，同时从方法调用次数分析中剔除；底层调用图数据不动，随时可改规则重新渲染（结果区右上角有「⟳ 刷新过滤」）。

### 5. 交易链路分析

- 清单内全部入口批量分析，结果按入口逐行折叠；展开即查看该入口的调用链树
- 调用链节点带来源徽章（项目 / 依赖 / 外部），标注调用方式（静态 / 虚调用 / 接口 / 构造 / lambda / 接口实现分派）与行号
- **分析深度**可调（默认 20）；带节点上限与环检测
- 结果落盘缓存，重启工具无需重算；项目发生变更（Git commit、pom 改动、重新编译）自动失效
- 「开始分析」走缓存，「重新分析」强制忽略缓存重算
- **全局搜索**：查所有入口的调用链里是否调用了某方法，自动展开命中链路并高亮节点

### 6. 方法调用次数分析

- 跨入口聚合，可按来源筛选（全部 / 项目 / 依赖 / 外部）
- **调用次数口径 = 不同调用位置的计数**：同一调用位置（同一调用方的同一行）不论出现在多少个入口的调用链里都只算一次。页面数字与导出结果、Excel 报告三者口径一致
- 每行可展开调用方明细（调用方方法 + 行号）
- 每行两个快捷操作：**导出**（该方法全部调用位置 → CSV）、**过滤**（一键把该方法加进过滤规则并立即生效）

### 7. Excel 报告

报告标题为 `<项目名>工程调用链分析报告`。项目级导出包含：

| Sheet | 内容 |
|---|---|
| 总览 | 项目信息、布局、入口数、节点/项目/依赖/外部方法数、耗时、截断状态、方法调用汇总、未解析依赖、告警 |
| 方法调用分析 | 保留方法的完整调用次数列表（跨入口聚合去重口径） |
| 被过滤方法 | 被样板规则剔除的方法，**便于核对规则是否误伤** |
| 过滤规则 | 本次导出生效的规则明细（全局层 + 项目层） |
| 各入口调用链 | 每个入口方法一个 Sheet，列为 `层级 / 方法标识 / 来源 / 调用方式 / 行号 / 备注`，纯数据无缩进，可筛选与 VLOOKUP |

方法标识统一为 `全限定类名#方法名(参数类型短名)`，构造器为 `#<init>`。

### 8. 新手引导

三层轻引导，不阻塞操作、看过即不再打扰：

- 顶部「下一步」条：按当前状态提示该做什么（导入项目 → 维护入口 → 分析），被关闭后状态推进仍会提示，全流程走通自动隐藏
- 首次上手清单：项目列表页显示四项，完成项自动打勾
- 三处一次性就地微提示（入口清单为空、首次打开过滤规则、首次看到结果）
- header 右侧 ❓ 可随时清空标记重看

---

## 环境要求

| 依赖 | 是否必需 | 用途 |
|---|---|---|
| **JDK 11+**（必须是 JDK，不是 JRE） | 必需 | 运行工具；无构建工具的工程走 javac 编译路径 |
| **Maven**（`mvn` 在 PATH） | 导入项目时必需 | 编译被分析工程；定位本地仓库拼 classpath |
| **Git**（`git` 在 PATH） | Git 仓库导入时必需 | 克隆、分支/Tag 切换 |

> 工具自身零出站网络，但 Maven / Git 会访问你配置的私服与 Git 服务器 —— 这是分析他人工程的必要动作。

---

## 快速开始

### 打包

```bash
mvn clean package -DskipTests
# → target/project-analysis-java.jar（Spring Boot fat jar，含全部依赖）
```

### 启动

Windows 直接双击 `start.bat`（会自动做 Java / Maven / Git 体检、打印数据目录与访问地址、等服务就绪后打开浏览器）；Linux / macOS 用 `bash start.sh`。

也可以手工启动：

```bash
java -jar target/project-analysis-java.jar
# 浏览器打开 http://127.0.0.1:8080
```

### 跑测试

```bash
mvn test
```

> `GitPrepareServiceTest` 等用例依赖本机真实的 Git / Maven 与网络环境，在受限环境下会失败，与业务逻辑无关。

### 部署到服务器（供他人访问）

工具**默认只监听 `127.0.0.1`**，也就是说部署到服务器后，只有服务器本机能打开页面。要供其他机器访问，需要把监听地址改为 `0.0.0.0`（fat jar 内的 yml 改不了，用启动参数或环境变量覆盖）：

```bash
# 方式一：启动参数（推荐）
java -jar project-analysis-java.jar --server.address=0.0.0.0

# 方式二：环境变量
CG_ADDR=0.0.0.0 java -jar project-analysis-java.jar

# 附带换端口：
java -jar project-analysis-java.jar --server.address=0.0.0.0 --server.port=9090
```

用 `start.bat` / `start.sh` 启动时，设 `CG_ADDR=0.0.0.0` 即可，脚本会打印监听地址并给出对外访问提示。

部署后确认监听地址（应看到 `0.0.0.0` 或 `::`，而不是 `127.0.0.1`）：

```bash
ss -lntp | grep 8080                  # Linux
netstat -ano | findstr :8080          # Windows
curl http://<服务器内网IP>:8080/       # 从其他机器或本机走内网 IP 验证
```

再检查服务器防火墙/安全组是否放通该端口。

> 绑定 `0.0.0.0` 意味着同网段任何机器都能访问，请自行保证网络隔离；不需要远程访问时保持默认的 `127.0.0.1`。

---

## 使用流程

```
① 导入项目（本地目录 / Git 仓库）
        ↓
② 维护交易入口清单（自动扫描 + 手动添加 → 排除不需要的）
        ↓
③ 按需配置过滤规则（去样板方法）与扫描策略
        ↓
④ 开始分析 → 查看调用链 / 调用次数分析 → 导出 Excel
```

页面上三步有明确编号与视觉分隔，Step 2 是唯一入口汇合点，Step 3 是唯一分析入口。

---

## 数据与缓存

### 全局数据目录

默认 `D:\.callgraph`（Windows），优先级：`-Dcallgraph.home=` → 环境变量 `CALLGRAPH_HOME` → `D:\.callgraph` → `~/.callgraph` → 系统临时目录。

| 文件 / 目录 | 内容 |
|---|---|
| `projects.json` | 项目注册表（类型、路径、打开时间） |
| `noise-rules.json` | 全局过滤规则 |
| `scan-strategy.json` | 全局扫描策略（当前选中方案 + 自定义方案，内置方案不落盘） |
| `workspaces/` | Git 仓库克隆工作区 |

### 项目级数据目录 `<项目>/.callgraph/`

随项目走 —— 复制/移动项目即带走缓存。

| 文件 / 目录 | 内容 |
|---|---|
| `cache/` | 分析结果缓存（入口级 `SimpleClass#method_<hash>.json`） |
| `entries.json` | 交易入口清单 |
| `noise-rules.json` | 项目级过滤规则（全局覆盖 + 自定义规则） |
| `scan-strategy.json` | 项目级扫描策略 |

建议把 `.callgraph/` 加入被分析项目的 `.gitignore`。

---

## 配置项

| 配置 | 位置 | 默认 |
|---|---|---|
| 监听地址 | 环境变量 `CG_ADDR` 或启动参数 `--server.address` | `127.0.0.1`（仅本机） |
| 端口 | 环境变量 `CG_PORT` 或启动参数 `--server.port` | 8080 |
| 监听地址 / 端口（默认值） | `src/main/resources/application.yml` | `127.0.0.1:8080` |
| 数据目录 | `-Dcallgraph.home=` 或 `CALLGRAPH_HOME` | `D:\.callgraph` |
| 本地 Maven 仓库 | `-Dcallgraph.maven.repo=` 或 `CALLGRAPH_M2_REPO` | 自动定位 |

> 需要局域网/服务器共享时见上文「部署到服务器」，并自行保证网络隔离。

---

## 源码结构

```
src/main/java/com/spark/projectanalysis/
├─ ProjectAnalysisApplication.java        启动类
├─ config/      CallgraphPaths（数据目录）  StaticResourceConfig
├─ engine/      调用图引擎
│   ├─ CallGraphBuilder                    DFS 构树（环检测 / 深度 / 节点上限 / 同级去重）
│   ├─ ClassMetadataRegistry               类与注解元数据
│   ├─ ClasspathResolver / PomDependencyResolver / MavenRepoLocator
│   ├─ MethodCallExtractor                 ASM 提取方法调用点
│   ├─ ProjectLayout                       工程布局识别
│   ├─ entry/                              入口探测器 SPI + 4 个内置探测器 + 规则扫描器
│   └─ model/                              MethodKey / CallGraph / CallNode / GraphMethod / GraphEdge ...
├─ report/      ExcelReportGenerator       Excel 报告
├─ service/
│   ├─ AnalysisService                     分析编排
│   ├─ BatchAnalyzeService                 批量分析
│   ├─ AnalysisCacheService                结果缓存
│   ├─ EntryScanService / EntryListService 入口扫描与清单
│   ├─ GitPrepareService / GitCloneService / GitRefService   Git 导入与分支
│   ├─ MavenCompileService / JavacCompileService            编译
│   ├─ NoiseRuleService                    过滤规则（双层 + 覆盖）
│   ├─ ScanStrategyService                 扫描策略（双层 + 内置）
│   └─ ProjectRegistry                     项目注册表
└─ web/         AnalysisController / ProjectsController / EntryListController
               NoiseRuleController / ScanStrategyController / GlobalExceptionHandler

src/main/resources/
├─ application.yml
└─ static/      index.html  app.js  style.css  guide.js

sample-project/   一个用于自测的最小示例工程
docs/             技术方案与交接文档
```

---

## 技术栈

Spring Boot 2.7.18 · Java 11 · ASM 9.7 · Apache POI 5.2.5 · JGit · 原生 JS（无前端框架、无构建步骤）

---

## 已知约束

- Git 场景一期仅支持 **Maven** 工程
- JGit 版本锁定 `5.1.3.201810200350-r`，依赖内网私服，请勿升级
- Git 拉取使用固定本地目录 + 原生 Git 客户端增量更新
- 仓库 Token 不持久化，每次导入需手动填入
- 过滤规则与统计口径只影响**展示与导出**，不改动底层调用图数据
