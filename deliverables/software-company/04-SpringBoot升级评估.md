# OPT-29：Spring Boot 升级评估

> 对象工程：`project-analysis-java`（内网 Java 方法调用链静态分析工具，纯源码工程、零出站、只读分析）
> 评估性质：**纯评估，未修改任何源码 / pom / 测试**；本文件为唯一产出物。
> 评估日期：2026-09-17
> 评估人：高见远（架构师）

## 标注约定

- `<已验证>`：结论来自本仓库文件 / 本机环境实测，附 `文件:行号` 或命令证据。
- `<推断>`：结论来自 Spring 官方路线图 / 生态版本知识，**未能从本仓库或内网私服直接验证**，落地前必须按第 4 章清单核对。
- 本轮**未执行**任何 `mvn` 编译 / 依赖下载 / 私服联通测试（任务要求纯评估），故"依赖可用性"一律按 `<推断>` 处理并单列确认清单。

---

## 0. 结论先行（TL;DR）

| 项 | 结论 |
|---|---|
| **当前基线** | Spring Boot **2.7.18**（2.x 末版）+ Java **11** + Maven 3.6.3 `<已验证>` |
| **Path B（升 2.7.x 最新补丁）** | **无意义**——2.7.18 已是 2.7.x 最终版，不存在更高的 2.7.x。B ≡ A `<推断>` |
| **Path C（升 Boot 3.x）** | 代码侧改动**极小**（`javax.*` 仅 4 处 `@PreDestroy`），真正的工作量与环境改造、依赖私服可得性 |
| **Path D（升 Boot 4.0）** | **本轮不推荐**：需 Java 17 + Jackson 3 包名重构 + JUnit 6 + Maven 3.9.9+，迁移面数倍于 Path C `<推断>` |
| **推荐** | **短期留 2.7.18（Path A，自带安全补丁机制）**；满足第 4 章前置条件后，以 **Path C→Boot 3.5.x + Java 17** 作为正式现代化目标；Boot 4 作为更后期的独立课题 |
| **最大不确定性** | 内网私服能否提供 Boot 3.5.x 全套构件 + **Java 17 JDK**（唯一的非 Maven 构件，需单独确认）|

---

## 1. 现状

### 1.1 版本矩阵 `<已验证>`

证据：`pom.xml`

| 构件 | 版本 | 位置 |
|---|---|---|
| `spring-boot-starter-parent` | **2.7.18** | `pom.xml:10` |
| `java.version` / `maven.compiler.release` | **11** | `pom.xml:21-24` |
| `org.ow2.asm:asm` | **9.7**（显式固定） | `pom.xml:25,39-42` |
| `org.apache.poi:poi-ooxml` | **5.2.5**（显式固定） | `pom.xml:26,46-49` |
| `org.eclipse.jgit:org.eclipse.jgit` | **5.1.3.201810200350-r**（显式固定，注释标"Java 8 兼容"） | `pom.xml:51-56` |
| `spring-boot-starter-web` | 由 parent 管理 | `pom.xml:32-35` |
| `spring-boot-starter-test` | 由 parent 管理，`scope=test` | `pom.xml:59-63` |
| 构建插件 | 仅 `spring-boot-maven-plugin` | `pom.xml:69-72` |

### 1.2 构建 / 运行环境 `<已验证>`

实测（PowerShell）：

```
DATE          = 2026-09-17
JAVA_HOME     = X:\JDK\jdk11
java          = 11.0.8 2020-07-14 LTS (Oracle)
mvn           = Apache Maven 3.6.3
platform enc  = GBK (Default locale: zh_CN)
maven wrapper = 不存在（无 mvnw / mvnw.cmd）
```

`settings.xml`（脱敏，仅结构）`<已验证>`：`localRepository = X:\Maven\repository`，私服镜像 `http://10.0.71.6:8094/nexus/content/groups/public`（另配 aliyun / oschina 镜像）。**该内网 nexus 是本工程唯一的真实构件来源**，也是本次评估的关键约束。

### 1.3 "2.7.18 已 EOL" 的确切含义 `<推断>`

- 2.7.18 是 **Spring Boot 2.7.x 的最后一个版本**，发布于 2023-11-23；其 **OSS 免费支持已于 2023-06-30 结束**。
- 但在 Spring **商业支持（Tanzu / 企业订阅）** 下，2.7.x 的支持窗口延续至 **2029-06-30**。
- 对**内网自用工具**而言，这意味着：① 不再有公开的 OSS 安全补丁；② 若走商业订阅仍可持续获得补丁；③ 否则需**自行对传递依赖打补丁**（见 5.1 Path A）。
- **注意（对路线选择影响重大）**：`<推断>` 截至评估日（2026-09），**Boot 3.5.x 的 OSS 支持窗口已于 2026-06-30 结束**，当前 OSS 支持线为 **Boot 4.0.x**。即"想拿免费 OSS 支持"这件事已无法靠 3.x 满足——这是 Path C 的一个真实缺点，必须在决策时权衡。

### 1.4 Java 11 硬约束 `<已验证>`

`pom.xml:21-24` 将 `java.version`、`maven.compiler.source/target/release` 全部钉在 **11**；`JAVA_HOME` 指向 `X:\JDK\jdk11`。**Boot 3.x / 4.x 均要求 Java 17**，因此任何 Path C/D 都必须先解决 JDK 17 的取得与 CI/构建机改造——这是**唯一的非 Maven 前置条件**。

---

## 2. 升级路径对比

### 2.1 Path A：留在 2.7.18 + 人工打安全补丁

- **做法**：维持 Boot 2.7.18 + Java 11 不动，通过 `pom.xml` 的 `<dependencyManagement>` 对已知存在 CVE 的传递依赖做版本覆盖（如 spring-framework 5.3.x 末版、tomcat-embed 9.0.x 末版、snakeyaml、logback 等），并定期人工跟踪。
- **代码改动**：0。
- **工作量**：低（一次性梳理传递依赖 + 建立补丁台账）。
- **风险**：中低。风险点在于"人工补丁"需要持续跟进而非一劳永逸；但**不引入任何回归风险**。
- **适用**：短期维稳 / 无 Java 17 前置条件时的默认选择。

### 2.2 Path B：升到 2.7.x 最新补丁版

- **结论：不成立。** 2.7.18 已是 2.7.x 最终版本，**不存在 2.7.19+**。`<推断>`
- 因此 **Path B 与 Path A 等价**，无需单列路线。若任务方原意是"升 2.7 系列内的安全补丁"，则其目标已由 Path A 的依赖覆盖实现。

### 2.3 Path C：升 Boot 3.x（需 Java 17 + `javax.*`→`jakarta.*`）

- **做法**：parent 升到 **3.5.x**（3.x 末线）；Java 升 17；`javax.annotation.PreDestroy` 等改 `jakarta.annotation.PreDestroy`；内嵌容器由 Tomcat 9（`javax.servlet`）切到 Tomcat 10.1（`jakarta.servlet`）。
- **关键利好（本工程特例）**：`javax.*` 命名空间迁移面**异常小**——全仓仅 **4 处** 真正需要迁移的 `javax.annotation.PreDestroy`，其余 `javax.xml.*` / `javax.tools.*` 是 **JDK 自带 API，与 Jakarta EE 命名空间迁移无关**（详见 3.1）。且**全仓无任何 `jakarta.*` 引用、无任何 `HttpServletRequest/Response/ServletContext` 直接使用**。
- **工作量**：中。代码改动小，主体在**环境（JDK17）+ 依赖版本对齐 + 私服可得性**。
- **风险**：中。主要风险为私服构件缺失与 Java 17 运行期行为差异（见 3.8）。

### 2.4 Path D：升 Boot 4.0（不推荐，作为对照）

- **做法**：parent 升 **4.0.x**；Java 17+；**Jackson 由 2.x 升 3.x（包名 `com.fasterxml.jackson.*`→`tools.jackson.*`）**；测试框架升 **JUnit 6**；构建需 **Maven 3.9.9+**；移除的 `@MockBean/@SpyBean`（本工程未用，无影响）。`<推断>`
- **工作量**：**高**。除 Path C 的全部工作量外，**额外叠加 Jackson 3 与 JUnit 6 两处大迁移**。
- **风险**：**高**。Jackson 3 的**异常签名变更**会让现有 `catch(IOException)` 静默失效（详见 3.6），属"编译通过但运行期悄悄不捕获"的隐蔽型回归，排查成本高。

### 2.5 路径对比表

| 维度 | A（留 2.7.18） | B（2.7.x 最新） | C（Boot 3.5 + Java17） | D（Boot 4.0）`<推断>` |
|---|---|---|---|---|
| Java 要求 | 11 | 11 | **17** | **17+** |
| Maven 要求 | 3.6.3 | 3.6.3 | 3.6.3 | **3.9.9+** |
| `javax`→`jakarta` | 不需 | 不需 | 仅 4 处 `@PreDestroy` | 同 C |
| 内嵌容器 | Tomcat 9 | Tomcat 9 | Tomcat 10.1 | Tomcat 11 |
| Jackson | 2.x | 2.x | **2.x（不变）** | **3.x（包名重构）** |
| 测试框架 | JUnit 5 | JUnit 5 | JUnit 5 | **JUnit 6** |
| OSS 支持 | 已 EOL | 已 EOL | 已 EOL(3.5) | **进行中** |
| 代码改动量 | 0 | 0 | **极小** | 大 |
| 综合工作量 | 低 | 不适用 | 中 | 高 |
| 回归风险 | 低 | — | 中 | 高 |

---

## 3. 影响清单（含 `文件:行号`）

### 3.1 `javax.*` 使用点 `<已验证>`（全仓 grep `^import javax\.`）

| 类别 | 位置 | 是否需迁移 |
|---|---|---|
| `javax.annotation.PreDestroy` | `service/BatchAnalyzeService.java:17`、`service/EntryScanService.java:17`、`service/GitRefService.java:11`、`service/GitPrepareService.java:11` | **是**（仅此 4 处，改 `jakarta.annotation.PreDestroy`）|
| `javax.xml.*`（`XMLConstants`/`DocumentBuilder`/`DocumentBuilderFactory`） | `engine/MavenRepoLocator.java:7-9`、`engine/PomDependencyResolver.java:7-9` | **否**（JDK 自带，Jakarta 迁移不涉及）|
| `javax.tools.*`（`JavaCompiler`/`ToolProvider`/…） | `service/JavacCompileService.java:5-10`；测试 `testsupport/Fixtures.java:5-7` | **否**（JDK 编译器 API）|
| `javax.annotation.processing.Processor` 字符串 | `service/JavacCompileService.java:88`（仅 `META-INF/services/...` 字符串字面量，非 import） | **否** |

补充：全仓 **`jakarta.*` 引用数为 0** `<已验证>`（grep `jakarta\.` 无匹配）；唯一与 Servlet 沾边的 `config/StaticResourceConfig.java:5,12`（`WebMvcConfigurer`）与 `web/NoiseRuleController.java:11,132`（`MultipartFile`）均为 Spring 原生 API，**Boot 3 天然兼容，无需改**。

> 结论：Path C 的"命名空间迁移"在本工程里几乎是**零成本**，与一般 Boot 2→3 项目"遍地 javax"的印象不同。

### 3.2 `spring-boot-starter-*` 清单 `<已验证>`

| starter | 位置 | Boot 3 影响 |
|---|---|---|
| `spring-boot-starter-web` | `pom.xml:32-35` | 换 parent 版本即可，无 API 变更（本工程未直接使用 servlet API）|
| `spring-boot-starter-test` | `pom.xml:59-63` | JUnit 5 保留；Boot 3 仍管理 JUnit 5.x，无废 API 使用点 |

仅此 2 个 starter，无 security / data / validation / actuator 等 starter——**升级面天然小**。

### 3.3 第三方库 Boot 3 兼容性

| 库 | 现版本 | Boot 3 兼容性 | 说明 |
|---|---|---|---|
| **ASM** `org.ow2.asm:asm` | 9.7 `pom.xml:25,39-42` | **兼容** `<已验证>`（无 Spring 依赖）| ASM 9.7 支持 Java 22/23 class 文件，Java 17 字节码绰绰有余；与 Boot 3 内置的 `org.springframework.asm`（repackaged）**包名不同，不冲突** `<推断>` |
| **POI** `poi-ooxml` | 5.2.5 `pom.xml:26,46-49` | **兼容** `<已验证>`（POI 无 Spring 依赖）| POI 5.2.5 可运行于 Java 17；无需升级即可过渡。可选后续升 5.4.x |
| **JGit** `org.eclipse.jgit` | **5.1.3.201810200350-r**（2018 年）`pom.xml:51-56` | **可运行但需升级** `<推断>` | 5.x 为 Java 8 字节码，Jakarta 迁移**不直接影响它**（它不用 servlet）；但若同时升 Java 17，应借机升到 **JGit 6.10.x（Java 11+）**，一并修掉 **CVE-2023-4759**（任意文件覆盖，5.x 全系未修）。这是一条**独立于 Boot 的安全债**，见 `01-架构与可维护性评审.md` C-4 |

### 3.4 `application.yml` 配置项影响 `<已验证>`（`src/main/resources/application.yml`）

| 配置 | 行号 | Boot 3 影响 |
|---|---|---|
| `server.address: ${CG_ADDR:127.0.0.1}` | `:5` | 不变 |
| `server.port: ${CG_PORT:8080}` | `:7` | 不变 |
| `spring.application.name` | `:10-11` | 不变 |
| `spring.jackson.default-property-inclusion: non_null` | `:12-13` | **Boot 3 不变**（3.x 仍用 Jackson 2.x）；**Boot 4/Jackson 3 下该键语义/路径可能调整** `<推断>` |
| `callgraph.git.compile-policy` / `compile-allowed-hosts` | `:22-23` | 自定义键，经 `@Value` 绑定（`GitPrepareService.java:81-83`），**Boot 3 不变** |
| `logging.level.root` | `:26-28` | 不变 |

> 配置文件整体**迁移成本近乎为零**，无 `spring.factories`、无 Boot 2.x 特有废弃键。

### 3.5 内嵌容器 `<已验证/推断>`

- Boot 2.7 = **Tomcat 9**（`javax.servlet`）→ Boot 3.5 = **Tomcat 10.1**（`jakarta.servlet`）。
- 本工程**未直接引用任何 servlet 类型**（无 `HttpServletRequest`/`Response`/`ServletContext`/`Filter`/`@WebServlet`），故容器替换**对代码透明** `<已验证>`。
- 唯一相关：`MultipartFile`（`NoiseRuleController.java:132`）是 Spring 抽象，Boot 3 兼容 `<已验证>`。

### 3.6 Jackson 使用与风险 `<已验证>`

- 使用方式（全部 `com.fasterxml.jackson.databind.ObjectMapper`）：`AnalysisCacheService.java:3-5,57,60`、`NoiseRuleService.java:7,45`、`EntryListService.java:4,42,45`、`ScanStrategyService.java:6,45`、`ProjectRegistry.java:5,28`、`web/NoiseRuleController.java:4,29-31`，以及 `config/JacksonConfig.java:4,7`、`config/RegisteredProjectApiMixin.java:11`。
- **Boot 3.x：Jackson 仍为 2.x（`com.fasterxml.jackson`），以上全部无需改动** `<推断>`。
- **Boot 4/Jackson 3 隐患（仅当走 Path D）** `<推断>`：Jackson 3 将异常改为**非受检**（`JsonProcessingException`→`JacksonException extends RuntimeException`），使下列**依赖捕获 `IOException` 的逻辑会静默失去捕获效果**（编译通过、运行期行为改变）：
  - `service/ScanStrategyService.java:148`（`catch(IOException)` 包 `mapper.readValue`）
  - `service/ScanStrategyService.java:164,167`（`mapper.writeValue` + `catch(IOException)`）
  - `service/ProjectRegistry.java:40-44`（`mapper.readValue` + `catch(IOException)`）
  - 同类风险面另见 `EntryListService.java`、`AnalysisCacheService.java`（`catch(IOException)` 包裹 JSON IO）。
  - 另：`config/JacksonConfig.java:4,7`（`Jackson2ObjectMapperBuilderCustomizer` / `Jackson2ObjectMapperBuilder`）与测试 `config/RegisteredProjectApiMixinTest.java:6,22`——Boot 3/Spring 6 下**仍存在**，Boot 4/Spring 7 下被 `JsonMapper` 取代。
- **判定**：Jackson 相关风险**只在 Path D 触发**，Path C 无影响。这也是我推荐"先 C 后议 D"的技术理由之一。

### 3.7 测试影响面 `<已验证>`

| 项 | 现状 | Boot 3 影响 |
|---|---|---|
| 测试框架 | 全部 **JUnit 5**（`org.junit.jupiter.api.*`） | 无（Boot 3 仍管理 JUnit 5.x）|
| `@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvc` | `web/ProjectsControllerTest.java:38-39,54`、`web/AnalysisControllerTest.java:25-26`、`service/AnalysisStatsTest.java:28` | Boot 3 **仍支持**，无改 |
| JUnit 4 | 无 | — |
| `@MockBean` / `@SpyBean` | **全仓未使用** | 规避了 Boot 3.x 弃用 / Boot 4 移除的坑 |
| `Jackson2ObjectMapperBuilder`（测试） | `config/RegisteredProjectApiMixinTest.java:6,22` | Boot 3 保留；Boot 4 需改 |

> 测试面**干净**：无 JUnit4、无 MockBean，Path C 下测试基本零改动。

### 3.8 其他值得注意的运行时点 `<已验证>`

- **默认字符集**：`service/MavenCompileService.java:149` 用 `Charset.defaultCharset()` 读取 `mvn` 输出；本机默认即 **GBK**。JDK 17 **仍是平台默认**（JEP 400 的 UTF-8 默认在 **JDK 18** 才生效），故升到 **Java 17 不改变**该行为；但若未来越过 18，此处将发生**静默编码变化**，建议借升级顺手改为显式 `Charset`。其余 IO 大多已正确使用 `StandardCharsets.UTF_8`。
- **无 Maven wrapper**：升级后需保证构建机 Maven 版本达标（Path C 只需 3.6.3+，**Path D 需 3.9.9+**）。

---

## 4. 依赖可用性风险 —— 内网私服必须确认清单

> 全部为 `<推断>`（本轮未做私服联通测试）。**这是本评估能否落地的决定性前置**——若私服无法提供下列构件，Path C/D 直接不成立。

### 4.1 若走 Path C（Boot 3.5.x + Java 17）

| 需确认构件 | 期望坐标 | 备注 |
|---|---|---|
| Boot 3.x parent | `org.springframework.boot:spring-boot-starter-parent:3.5.x` | 决定整套受管版本 |
| Web starter | `org.springframework.boot:spring-boot-starter-web:3.5.x` | |
| Test starter | `org.springframework.boot:spring-boot-starter-test:3.5.x`（scope test） | |
| 构建插件 | `org.springframework.boot:spring-boot-maven-plugin:3.5.x` | |
| Jakarta 注解 | `jakarta.annotation:jakarta.annotation-api:2.1.1`（或 3.0.x） | 供 4 处 `@PreDestroy` |
| Spring 核心（传递） | `org.springframework:spring-core:6.2.x` 等 | 随 parent 拉取，确认私服有 6.2 线 |
| Jackson（传递） | `com.fasterxml.jackson.core:jackson-databind:2.19.x` | Boot 3.5 管理版本，确认可得 |
| ASM | `org.ow2.asm:asm:9.7` | 现已在用，确认私服含此版本 |
| POI | `org.apache.poi:poi-ooxml:5.2.5` | 现已在用；确认 |
| **Java 17 JDK** | **非 Maven 构件** | ⚠️ 必须从内网软件源取得（如 `X:\JDK\jdk17` 或内部 yum/apt/二进制包）。**这是首要阻塞项** |
| Maven | 3.6.3 即可（≥3.6.3） | Path C 无需升 Maven |

### 4.2 若走 Path D（Boot 4.0.x）—— 额外确认

| 需确认构件 | 期望坐标 |
|---|---|
| Boot 4 parent / starters | `spring-boot-starter-parent:4.0.x` 等 |
| Jackson 3 | `tools.jackson:jackson-databind:3.0.x`（**新 groupId**） |
| JUnit 6 | `org.junit.jupiter:junit-jupiter:6.x` |
| Maven | **3.9.9+**（构建机需升级） |

### 4.3 与升级正交但建议一并确认（安全债）

| 需确认构件 | 期望坐标 | 目的 |
|---|---|---|
| JGit 新版 | `org.eclipse.jgit:org.eclipse.jgit:6.10.x`（Java 11+） | 修 `CVE-2023-4759`，可与 Path C 同步 |

---

## 5. 推荐方案

### 5.1 推荐结论

**短期（现在）：维持 Path A —— 留在 Boot 2.7.18 + Java 11。**
理由：① 本工程是**内网、零出站、只读分析**的内部工具，暴露面小，2.7.18 的 EOL 风险可控；② 当前无 Java 17 前置（`JAVA_HOME` 仍 `jdk11`）；③ 可立即用 `pom.xml` 的 `<dependencyManagement>` 对高危传递依赖做版本覆盖，取得"低成本止血"。

**中期（满足前置后）：执行 Path C —— 升 Boot 3.5.x + Java 17。**
理由：① 本工程 `javax`→`jakarta` 迁移面**仅 4 处**、servlet/测试/Jackson 面**几乎零改**，是全项目性价比最高的现代化动作；② 一次性获得 Spring 6 生态、Tomcat 10.1、持续的安全维护路径。**唯一硬前置是 Java 17 与私服构件（第 4.1 节）**。

**不建议现在做 Path D（Boot 4.0）。**
理由：Boot 4 相对 Boot 3.5 多出 **Jackson 3 包名重构 + JUnit 6 + Maven 3.9.9+** 三重迁移，其中 Jackson 3 的**非受检异常**会引发 3.6 所述的隐蔽回归；风险与收益不匹配。建议**先完成 Path C，再作为独立课题评估 Path D**。

> 关于"3.5 的 OSS 支持也已结束"：若团队硬性要求"跑在免费 OSS 支持线上"，则需**直接从 2.7.18 跳到 4.0.x**（跳过 3.x），但这会把 Jackson 3 + JUnit 6 的迁移一次性前置——**不建议一步到位**。折中：接受 3.5 的商业支持/自维护现状，分两步走。

### 5.2 分级工作量 / 风险

| 步骤 | 工作量 | 风险 | 说明 |
|---|---|---|---|
| A 期间依赖补丁 | 低 | 低 | 仅 `pom.xml` 版本覆盖，零代码改动 |
| C 前置：JDK17 + Maven | 低 | 中 | 环境改造，取决于内网软件源 |
| C：parent/依赖升级 | 中 | 中 | 私服可得性为主要不确定 |
| C：`@PreDestroy` 迁移（4 处） | 极低 | 极低 | 机械替换 |
| C：回归（启动 + 接口 + 分析主链路） | 中 | 中 | 见 5.3 |
| D：Jackson 3 + JUnit 6 + Maven | 高 | 高 | 不建议现在做 |

### 5.3 分阶段路线（Path C）

1. **准备**：从内网软件源取得 **JDK 17**；确认第 4.1 节私服构件全部可解析（用 `mvn dependency:get` 或私服页面逐个核对）。
2. **Java 升级（先不动 Boot）**：`JAVA_HOME`→JDK17，`pom.xml:21-24` 改 `17`，Boot 暂留 2.7.18，跑通编译 + 全量测试，隔离"Java 17 行为差异"这一变量。
3. **Boot 升级**：parent 升 `3.5.x`；`javax.annotation.PreDestroy`→`jakarta.annotation.PreDestroy`（4 处）；`@Value` 绑定、`application.yml`、`WebMvcConfigurer`/`MultipartFile` 预期无需改。
4. **核心回归**（重点，非穷举）：
   - 应用启动（Tomcat 10.1 容器探活）；
   - `ProjectsControllerTest` / `AnalysisControllerTest` / `AnalysisStatsTest` 三个 `@SpringBootTest` 用例；
   - 端到端主链路：注册项目 → Git 拉取 → 编译 → 入口扫描 → 调用链分析 → Excel 导出（覆盖 POI/SXSSF、ASM 解析、JGit）。
5. **可选同步项**：JGit 升 `6.10.x` 修 `CVE-2023-4759`；`MavenCompileService.java:149` 显式化字符集。

---

## 6. 需确认事项 & 不建议现在做的事

### 6.1 需业务方 / 运维确认

1. **【首阻塞】能否提供 JDK 17**（`X:\JDK\jdk17` 或内部软件源）？这是 Path C 的硬门槛。
2. **内网 nexus 能否解析第 4.1 节全部构件**（尤其 `spring-boot-starter-parent:3.5.x`、`spring-core:6.2.x`、`jakarta.annotation-api`）？
3. 是否已购买 / 计划购买 **Spring Boot 2.7.x 商业支持**？——若"是"，Path A 的补丁可持续且优先；若"否"，A 期间需自建补丁台账。
4. 是否存在**外部合规 / 等保 / 漏洞扫描**要求"不得使用 EOL 框架"？——若"是"，Path C 需**提前排期**（甚至考虑直接 4.x）。
5. 构建 / CI 机器能否与开发机同步升级 JDK 与（如走 D）Maven？

### 6.2 不建议现在做的事

1. **不要现在升 Boot 4.0**：Jackson 3 包名重构 + JUnit 6 + Maven 3.9.9+，风险收益不匹配；应先做 Path C。
2. **不要为"升 2.7.x 最新补丁"做规划**：2.7.18 已是末版，不存在更高的 2.7.x。
3. **不要在没有 JDK 17 前置的情况下启动 Boot 3 升级**：会卡在半途（编译不过），先落实环境。
4. **不要忽略 Jackson 3 的异常签名陷阱**（若最终走 D）：`ScanStrategyService.java:148/164`、`ProjectRegistry.java:40-44` 的 `catch(IOException)` 需逐一复查为 `catch(JacksonException)` 或更宽的 `Exception`。
5. **不要顺手改无关文件**：本任务为纯评估；任何升级动作（改 `pom.xml`/源码/测试）应另立实施任务并走独立回归。

---

## 附录：证据与验证方法

| 证据 | 来源 / 方法 |
|---|---|
| 版本矩阵、依赖清单 | `Read pom.xml`（全 75 行）|
| `javax.*`/`jakarta.*` 面 | `Grep "^import javax\."` / `Grep "jakarta\."`（全仓 src）|
| `@PreDestroy` 4 处 | 同上 grep 结果：`BatchAnalyzeService:17`、`EntryScanService:17`、`GitRefService:11`、`GitPrepareService:11` |
| Jackson 捕获点 | `Read ScanStrategyService.java:140-170`、`Read ProjectRegistry.java:36-56`、`Grep "catch \(IOException"` |
| 测试框架 | `Grep "ObjectMapper|@SpringBootTest|@AutoConfigureMockMvc|MockBean|@Mock"` |
| 配置项 | `Read application.yml`（全 29 行）|
| 构建环境 | PowerShell：`java -version`、`mvn -v`、`Get-Date`、`Test-Path` |
| 私服 / 本地仓库 | `settings.xml`（脱敏读取，仅结构 URL）|

> ⚠️ 说明：本轮**未执行** `mvn` 构建 / 依赖下载 / 私服联通，所有"私服可得性"结论均为待验证推断，落地前须逐条核对第 4 章清单。
