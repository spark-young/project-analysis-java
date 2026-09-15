# Git 分支/Tag 切换与远端更新检测 技术方案

状态：实施完成

## 1. 背景与问题

当前工具已支持以 Git 仓库方式接入项目（`GitCloneService` 用 JGit 克隆 + 原生 `git` CLI 兜底），但接入后：

- **无法在工具内切换分支**：`clone()` 仅通过 `setBranch("refs/heads/"+branch)` 在克隆时指定分支，接入后想换分支只能删项目重新导入。
- **不支持 Tag**：现有实现只处理 `refs/heads/*`，无法检出 Tag 版本。
- **无法感知远端更新**：用户不知道本地工作目录是否落后于远端，需要手动 `git pull`。

**目标**：对 Git 类型项目，提供 **分支/Tag 切换** 与 **远端更新检测** 能力，并在进入项目时自动检查、展示最近检查时间。

## 2. 取舍决策（用户已确认）

| 决策点 | 选型 |
|---|---|
| 认证信息（token/username）持久化 | **方案 A：写入 registry JSON**（`projects.json`），切分支/查远端时复用，避免每次重填 |
| Tag 展示 | **展示 Tag 名称本身**（不带 `(detached)` 等后缀） |
| 远端更新检查时机 | **进入项目时自动检查**，并在 UI **展示最近检查时间** |
| 本地脏改动处理 | **自动 `git stash` / `stash pop`**：切换前 stash，切换成功后 pop |

## 3. 能力设计（GitCloneService）

在 `GitCloneService` 新增三个 native git CLI 方法（复用现有 `git(...)` 私有方法及其代理注入、`GIT_TERMINAL_PROMPT=0` 等环境隔离）：

### 3.1 `listRefs(String repoUrl, String token, String username)` → `GitRefs`

- 执行 `git ls-remote --heads --tags <authedUrl>`，解析输出：
  - `refs/heads/<name>` → 分支
  - `refs/tags/<name>` 且（`^{}` 剥离 annotated tag 的 peeled 行，同名去重）→ Tag
- 返回 DTO `GitRefs { List<String> branches; List<String> tags; String defaultBranch; }`
  - `defaultBranch` 取 `refs/heads/main` / `refs/heads/master` 优先，否则首个分支。
- 网络失败/无远端时返回空 refs（不抛异常阻断 UI），由调用方决定提示。

### 3.2 `checkRemoteUpdate(Path repoDir, String repoUrl, String ref, String refType, String token, String username)` → `RemoteStatus`

- 执行 `git -C <repoDir> ls-remote <authedUrl> <refSpec>` 拿到远端对应 ref 的 SHA。
- 本地取 `git -C <repoDir> rev-parse HEAD`（或指定 ref 的 SHA）。
- 比较：
  - 远端 SHA == 本地 SHA → `UP_TO_DATE`
  - 远端 SHA != 本地 SHA → `BEHIND`（附 `localSha`/`remoteSha` 短哈希）
  - 无法确定（网络失败/ref 不存在）→ `UNKNOWN`（附 hint）
- 返回 DTO `RemoteStatus { String status; String localSha; String remoteSha; String hint; Instant checkedAt; }`

### 3.3 `switchRef(Path repoDir, String repoUrl, String target, String targetType, String token, String username, BiConsumer<Integer,String> progress)` → `SwitchResult`

切换流程（**先 stash，再 fetch，再 checkout，最后 pop**）：

```
switchRef(repoDir, target, type):
  1. dirty = git status --porcelain 非空？
     dirty && stash: git stash push -u -m "callgraph-autoswitch"   (记录是否真的 stash 了)
  2. fetch:        git -C <repoDir> fetch --tags --prune <authedUrl>
  3. checkout:
     branch:       git -C <repoDir> checkout <target>
                   （若本地无该分支 → git checkout -b <target> origin/<target>）
     tag:          git -C <repoDir> checkout tags/<target>   （detached HEAD）
  4. pop:          stash 过 → git stash pop（失败则提示冲突，保留 stash 不丢改动）
  5. 返回 SwitchResult { ref, refType, stashed:boolean, stashPopped:boolean, conflict:boolean, output }
```

- 任一步骤失败：抛 `IllegalStateException`，携带 stderr；stash 未被 pop 时在异常信息中提示 stash 名，避免用户改动丢失。
- `switchRef` 完成后由上层触发**重新编译 + 失效缓存**（entry list / analysis cache）。

## 4. 数据模型（ProjectRegistry.RegisteredProject 扩展）

| 新增字段 | 类型 | 说明 |
|---|---|---|
| `gitToken` | String | 方案 A：持久化 token，切换/查远端复用 |
| `gitUsername` | String | 配套 username |
| `currentRef` | String | 当前所在分支名或 Tag 名 |
| `currentRefType` | String | `BRANCH` / `TAG` |
| `remoteUpdateStatus` | String | `UP_TO_DATE` / `BEHIND` / `UNKNOWN` / null |
| `lastCheckTime` | String(ISO) 或 long | 最近一次远端检查时间，用于 UI 展示 |

- 全部字段**向后兼容**：旧 `projects.json` 缺失字段时反序列化为 null，读取时按 `gitBranch` 回填 `currentRef`/`currentRefType=BRANCH`。
- `snapshot()`/`list()` 输出继续透传这些字段给前端。

## 5. 接口设计

新增端点（挂 `ProjectsController` 下，语义归属项目粒度；异步切换复用 Job + 轮询模式）：

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/projects/{id}/git/refs` | 拉取远端分支 + Tag 列表（`GitRefs`），供下拉框 |
| `POST` | `/api/projects/{id}/git/switch` | 入参 `{ ref, refType }`，启动异步切换 Job，返回 `SwitchStatus` |
| `GET` | `/api/projects/{id}/git/switch/{jobId}` | 轮询切换进度（状态机 `FETCHING→CHECKOUT→COMPILING→DONE/FAILED`） |
| `GET` | `/api/projects/{id}/git/remote-status` | 检查远端更新，更新 `remoteUpdateStatus`/`lastCheckTime` 并返回 `RemoteStatus` |

- `enterProject` 前端在进入项目后对 GIT 类型**自动调用** `remote-status`，展示结果 + 最近检查时间。
- 切换成功后：更新 registry 的 `currentRef/currentRefType`，异步触发 compile，并清理 `entries.json` 与 `.callgraph/cache` 中与旧 ref 绑定的产物（通过 entryListFingerprint 使缓存失效）。

## 6. DTO 清单（新增，包 `service.dto`）

| DTO | 字段 |
|---|---|
| `GitRefs` | `List<String> branches`、`List<String> tags`、`String defaultBranch` |
| `RemoteStatus` | `String status`、`String localSha`、`String remoteSha`、`String hint`、`long checkedAt` |
| `SwitchRequest` | `String ref`、`String refType` |
| `SwitchStatus` | `String jobId`、`String status`、`String message`、`int progress`、`String ref`、`String refType`、`boolean stashed`、`boolean conflict` |

## 7. 前端改造（原生 JS）

- **Git 信息栏**（`index.html`，位于 `#currentProjectBar` 附近，仅 GIT 项目显示）：
  - 分支下拉 `select`、Tag 下拉、`切换` 按钮
  - `检查更新` 按钮 + 状态徽标（已是最新/有更新/未知）
  - `最近检查：YYYY-MM-DD HH:mm:ss`
- **app.js**：
  - `enterProject(id)`：GIT 类型时并发拉 `git/refs` + `git/remote-status`，渲染信息栏
  - 切换按钮 → `POST git/switch` → 轮询 `git/switch/{jobId}` → 进度条 → DONE 后刷新入口列表/清空旧结果
  - 检查更新按钮 → `POST/GET remote-status` → 更新徽标与时间
  - token/username 从项目对象回填到表单
- **style.css**：信息栏样式。

## 8. 文件清单

**新增**
```
src/main/java/com/spark/callgraph/service/dto/GitRefs.java
src/main/java/com/spark/callgraph/service/dto/RemoteStatus.java
src/main/java/com/spark/callgraph/service/dto/SwitchRequest.java
src/main/java/com/spark/callgraph/service/dto/SwitchStatus.java
src/main/java/com/spark/callgraph/service/GitRefService.java        # 切换 Job 编排（可选，或并入 GitPrepareService）
src/test/java/com/spark/callgraph/service/GitCloneServiceRefTest.java
```

**修改**
```
src/main/java/com/spark/callgraph/service/GitCloneService.java      # listRefs/checkRemoteUpdate/switchRef
src/main/java/com/spark/callgraph/service/ProjectRegistry.java      # RegisteredProject 扩展字段
src/main/java/com/spark/callgraph/web/ProjectsController.java        # git/refs|switch|remote-status
src/main/resources/static/index.html                                # Git 信息栏
src/main/resources/static/app.js                                    # 交互逻辑
src/main/resources/static/style.css                                 # 样式
```

## 9. 测试策略（TDD）

后端走**本地 bare 仓库夹具**（复用 `GitCloneServiceTest` 的 `initWorkRepo` + `pushToBare`，无网络依赖）：

1. `test_listRefs_returnsBranchesAndTags`：建 2 分支 + 1 tag 推送到 bare，`listRefs(bare.toUri())` 返回期望 branches/tags，`defaultBranch` 正确。
2. `test_switchRef_toBranch`：clone 后 `switchRef(..., "feature", "BRANCH")` → `git.getRepository().getBranch()` == `feature`。
3. `test_switchRef_toTag`：`switchRef(..., "v1", "TAG")` → HEAD 指向 tag（detached），`currentRef` == `v1`。
4. `test_switchRef_stashAndPop`：工作区写入未提交改动 → 切分支 → 改动被保留（stash pop 成功，文件内容仍在）。
5. `test_switchRef_dirtyConflict_noLoseChange`：制造冲突改动 → 切换抛异常且 stash 仍存在（改动未丢失）。
6. `test_checkRemoteUpdate_upToDate_and_behind`：本地 clone 后远端再推一次 → `BEHIND`；再次 pull 后 → `UP_TO_DATE`。
7. `test_projectRegistry_backwardCompat`：旧 JSON（无新字段）反序列化不报错且新字段为 null。

Controller 层：`ProjectsControllerTest` 补 `git/refs`、`git/remote-status` MockMvc 用例（mock service 或临时仓库）。

**既有测试**：`GitCloneServiceTest` 保持通过；`GitPrepareServiceTest`、`AnalysisControllerTest`、`ExcelReportGeneratorTest` 存在**预存失败**（详见 §11），与本次改动无关。

## 10. 实施顺序（每阶段独立可验证）

1. `GitCloneService` 新增 `listRefs/checkRemoteUpdate/switchRef`（TDD：先写 `GitCloneServiceRefTest` 失败用例 → 实现 → 全绿）。
2. `ProjectRegistry.RegisteredProject` 扩展字段 + 向后兼容。
3. DTO + `ProjectsController` 新增端点（含异步切换 Job）。
4. `index.html` Git 信息栏 + `style.css`。
5. `app.js` 交互逻辑（进入项目自动检查、切换轮询、检查更新）。
6. `mvn test` 全量门禁（见 §11 预存失败说明）。

## 11. 全量测试门禁结果与预存失败

本次改动相关测试 **全部通过**：

- `GitRefServiceTest`（9 用例）、`GitCloneServiceRefTest`、`ProjectRegistryTest` 全绿。

全量 `mvn test`（119 用例）报告 6 个失败。经在**干净 `HEAD` 基线**（`git worktree` 检出、不含任何未提交改动）复跑比对，确认为**预存失败**，与本次 Git 分支/Tag 功能无关：

| 失败用例 | 现象 | 归因 |
|---|---|---|
| `GitPrepareServiceTest.test_prepare_cloneCompileDone:105` | NPE（`compiledDir` 为 null） | `GitPrepareService` 已改为"编译推迟到进入分析时"，但测试仍期望 `prepare` 阶段同步调用编译桩；契约不一致 |
| `GitPrepareServiceTest.test_prepare_compileFailure_marksFailed:131` | 期望 `FAILED` 实为 `DONE` | 同上 |
| `GitPrepareServiceTest.test_prepare_backendInSubdir_located:178` | NPE | 同上 |
| `AnalysisControllerTest.test_scanEntries_badPath_rejected:197` | 期望 400 实为 500 | HEAD 基线同样失败 |
| `ExcelReportGeneratorTest.test_excelStructure:94` | 期望非 null | 工作区未提交的 `ExcelReportGenerator.java`（噪音规则 Sheet 功能）改动所致；HEAD 上通过 |
| `ExcelReportGeneratorTest.test_sheetNameDedup_and_longName:161` | 期望 true 实为 false | 同上 |

结论：本次功能**零回归**；其中前 3 项与前 1 项属提交即存在的契约/断言不一致，后 2 项由工作区遗留改动引入。**修复不在本次范围内**，留待后续单独处理。

