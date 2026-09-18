# OPT-27：`app.js` 拆分方案（8 簇）

> 基线：`9fa4571`。铁律：**只搬运、不改行为；一簇一个 commit；宁可慢不可坏**。
> 分析对象：`src/main/resources/static/app.js`（当前 5133 行，整个文件是一个大 IIFE：第 2 行 `(function () {` ~ 第 5133 行 `})();`）。

---

## 一、现状盘点

### 1.1 总体结构

| 结构 | 位置 | 说明 |
|---|---|---|
| IIFE 包裹 | `app.js:2` ~ `app.js:5133` | 全部代码在一个闭包内，**顶层只暴露 `window.Guide`（guide.js）的读取，app.js 自身零 `window.*` 暴露**（OPT-32 已清理） |
| `$` 助手 | `app.js:5` | `const $ = (sel) => document.querySelector(sel);` |
| `els` DOM 缓存 | `app.js:7` ~ `app.js:259` | 约 250 行、150+ 个 `$('#xxx')` 元素引用，**被所有簇共用** |
| 共享可变状态 | `app.js:260-318`、`340`、`645-647`、`679/693/727-728`、`2149-2176`、`2449/2493-2531`、`4586-4590`、`4700` | 约 50 个 IIFE 顶层 `let/const`，详见 1.3 |
| 事件绑定 | 全文内联，共 **146 处** `addEventListener` | 与各簇函数交织；无独立 init 段 |
| 页面初始化 | `app.js:5114-5131` | `Guide.init` → 页脚年份 → `switchView('projects')` → `refreshProjectList()`。脚本在 `</body>` 前同步加载，无 `DOMContentLoaded` |

### 1.2 函数聚类（约 190 个顶层函数/常量表）

以行号为界，主要子区域：

| 区域 | 行范围 | 内容 |
|---|---|---|
| 请求层 | 795-828 | `postJson` / `fetchJson` / `putJson`（**零闭包依赖的纯 fetch 封装**） |
| 项目列表 | 340-543 | `refreshProjectList`/`enterProject`/`renderProjectList`/`projectCardHtml`/`deleteProject`/`autoLoadEntryList`/`autoLoadCacheForProject` |
| 基础 UI | 324-339, 661-726 | `switchView`/`currentProjectPath`/`showError`/`clearError`/`showToast`/`showConfirm`/`settleConfirm` |
| Loading | 727-793 | `resetLoadingPanel`/`showLoading`/`loadingSetStep`/`loadingSetProgress`/`hideLoading` |
| Git | 861-1203 | `GIT_STATUS_LABEL`/`renderJobLog`/`renderGitStatus`/`pollGitStatus`/`loadGitRefs`/`checkGitRemoteStatus`/`onGitSwitchDone`/`pollGitSwitchStatus`/`startGitSwitch`/`renderGit*`/`stopGit*` |
| 入口清单视图 | 1204-1442 | `entryItems`/`renderEntries`/`collectCheckedEntries`/`buildEntryRequest`/`analyzeCheckedEntries`/`updateEntryCount`/`applyEntryFilter`/`downloadProjectExcel` |
| 结果渲染 | 1443-1602 | `SOURCE_LABEL`/`INVOKE_LABEL`/`renderResult`/`renderBatchSummary`/`renderWarnings` |
| 批量加载 | 1603-1734 | `toggleEntryBody`/`buildEntryBody(Sync)`/`loadBatchEntry`/`fetchEntryFile`/`loadAllBatchEntries`/`resetBatchModel`/`finalizeBatchLoad` |
| 签名工具 | 1748-1818 | `batchMethodKey`/`readableFullSig`/`sigPartsFromString`/`sigHtmlFromString`/`appendSigFromString`（OPT-24 后多为 Sig 薄封装） |
| 频次/项目级搜索 | 1819-2148, 2449-2743 | `buildProjectFreq`/`currentFreqData`/`renderProjectFreq`/`runProjectSearch`/`computeSearchMarks`/`revealHitPath`/`nextProjectHit`/`renderFreq*`/`fillFreqCallers`/`exportFreqMethodCallers`/`filterFreqMethod` |
| 树渲染与树内搜索 | 3366-3767 | `renderWarnings`/`nodeEl`/`badge(Html)`/`escapeHtml`/`matchNode`/`collectMatches`/`toggleSearchBar`/`resetGlobalSearch`/`runGlobalSearch`/`graphIndex`/`rootsOf` |
| 入口清单管理 | 3768-3860 | `entryKey`/`renderEntryList`/`renderEntryRow`/`updateEntryToolbar` |
| 扫描策略 | 3861-4585 | `__ss`/`loadScanStrategy`/`renderSs*`/`openScanStrategyPanel`/`saveSsStrategy`/`newSsProfile`/`copySsProfile`/`deleteSsProfile`/`exportSsProfiles`/`importSsProfiles`/`addSsRule` |
| 添加入口/排除弹窗 | 4586-5068 | `openAddEntryModal`/`verify`(4759-4764 区域)/`loadMethodsForClass`/`parseEntryString`/`entryActions`/`EXCLUDE_REASON_PRESETS`/`openExcludeModal`/批量排除 |
| 引导/收尾 | 5069-5133 | `guideState`/`guideAction`/`Guide.init`/页脚年份/初始化 |

### 1.3 闭包共享状态（多簇共用 → 拆分的核心难点）

| 变量 | 定义处 | 被哪些簇读写 |
|---|---|---|
| `els` | 7 | 全部簇 |
| `currentResult` | 260 | 结果渲染、频次、噪声过滤、批量行内展开、树内搜索 |
| `currentProjectId` | 309 | 项目列表、入口清单、扫描策略、噪声规则、添加入口 |
| `currentEntryList` / `currentCandidates` | 267-268 | 入口清单视图 ↔ 添加入口弹窗 ↔ 排除弹窗 |
| `batchModel` / `batchRowStates` | 262/296 | 批量加载 ↔ 行内展开 ↔ 统计刷新 ↔ 频次 |
| `noiseRules/globalRulesCache/projectRulesCache/globalOverrides/activeNoiseRules/compiledActiveRules/activeNoiseHash` | 300-306 | 噪声编辑簇 ↔ 结果过滤判定（`isNoiseMethod`）↔ 引导（`guideNoiseConfigured`） |
| `freqFilter/freqViewMode` | 294-295 | 频次簇 ↔ 结果渲染 chips |
| `nodeRegistry/hitRows/activeSearch/expandFns` | 289-293 | 树渲染 ↔ 树内搜索 ↔ 项目级搜索 |
| `projSearchMarks/Order/Cursor` | 297-299 | 项目级搜索 ↔ 树渲染 |
| `scanStrategy/globalScanStrategy/ss*` | 313-318 | 扫描策略簇 ↔ 添加入口弹窗（scan-manual） |
| `entrySelKeys/excludeModalItems` | 287-288 | 入口清单 ↔ 排除弹窗 |
| `gitSwitchTimer/gitRefsCache/sourceMode/gitProjectPath/gitPollTimer` | 310-311/645-647 | Git 簇 ↔ 项目列表（进入项目时刷新） |
| `toastTimer/confirmResolver/loadingTick/loadingStartAt` | 679/693/727-728 | 基础 UI 簇（被所有簇调用） |

**结论：除请求层 3 函数外，几乎所有簇都共享闭包状态 → 必须先建共享状态通道，再逐簇搬出。**

### 1.4 `window.*` 暴露面

`app.js` 自身无 `window.*` 暴露（OPT-32 清理完毕）。对外仅**读取** `window.Guide`（guide.js，721 行先于 app.js 加载）。`window.Sig` 由 `js/sig.js` 提供（722 行，UMD 双导出：`window.Sig` + `module.exports`）。

---

## 二、8 簇划分

| # | 簇名 | 拥有的函数（代表） | 大致行范围 | 簇间依赖 |
|---|---|---|---|---|
| C1 | **api 请求层** → `js/api.js` | `postJson`/`fetchJson`/`putJson` | 795-828 | 无（纯 fetch 封装，零闭包依赖） |
| C2 | **ui 基础 UI** → `js/ui.js` | `$`/`els`/`switchView`/`showError`/`clearError`/`showToast`/`showConfirm`/`settleConfirm`/`resetLoadingPanel`/`showLoading`/`loadingSetStep`/`loadingSetProgress`/`hideLoading`/`escapeHtml`/`badge`/`badgeHtml` | 5-7, 324-339, 670-793, 3460-3481 | 依赖 els；被所有簇调用。**须配共享状态通道** |
| C3 | **projects 项目与 Git** → `js/projects.js` | `refreshProjectList`/`renderProjectList`/`projectCardHtml`/`enterProject`/`deleteProject`/`autoLoad*`/`setSourceMode`/`currentProjectPath`/`renderJobLog`/`renderGitStatus`/`pollGitStatus`/`loadGitRefs`/`checkGitRemoteStatus`/`startGitSwitch`/`pollGitSwitchStatus`/`renderGit*`/`stopGit*`/`GIT_*_LABEL`/`formatCheckTime`/`updateStep3Hint` | 340-543, 544-669(部分), 861-1203 | 依赖 C1、C2；状态 `sourceMode/gitProjectPath/gitPollTimer/gitSwitchTimer/gitRefsCache` |
| C4 | **entries 入口清单视图** → `js/entries.js` | `entryItems`/`renderEntries`/`collectCheckedEntries`/`buildEntryRequest`/`analyzeCheckedEntries`/`updateEntryCount`/`applyEntryFilter`/`downloadProjectExcel`/`entryKey`/`renderEntryList`/`renderEntryRow`/`updateEntryToolbar` | 1204-1442, 3768-3860 | 依赖 C1、C2；状态 `entryItems/entrySelKeys/excludeModalItems`；与 C8 的弹窗互调 |
| C5 | **result 树渲染与搜索** → `js/result.js` | `renderResult`/`renderWarnings`/`nodeEl`/`graphIndex`/`rootsOf`/`renderStats`/`computeFilteredStats`/`matchNode`/`collectMatches`/`clearSearchHits`/`toggleSearchBar`/`focusMatches`/`resetGlobalSearch`/`runGlobalSearch`/`nodeRegistry`/`hitRows`/`activeSearch`/`expandFns`/`invalidateAdapterCache`/`reapplyFilterToTree` | 1443-1602, 3366-3767, 2149-2282 | 依赖 C1、C2、C6 的频率数据；调用 Sig |
| C6 | **batch+freq 批量与频次** → `js/batch.js` | `toggleEntryBody`/`buildEntryBody(Sync)`/`loadBatchEntry`/`fetchEntryFile`/`loadAllBatchEntries`/`resetBatchModel`/`finalizeBatchLoad`/`renderBatchSummary`/`showProjectPanels`/`setBatchLoadState`/`buildProjectFreq`/`renderProjectFreq`/`runProjectSearch`/`computeSearchMarks`/`revealHitPath`/`nextProjectHit`/`renderFreq*`/`fillFreqCallers`/`exportFreqMethodCallers`/`filterFreqMethod`/签名工具组 | 1603-1818, 1819-2148, 2400-2743 | 依赖 C1、C2、C5（行内渲染）；状态 `batchModel/batchRowStates/freq*/projSearch*` |
| C7 | **noise 噪声规则** → `js/noise.js` | `noiseRulesHash`/`isNoiseGraphMethod`/`isNoiseMethod`/`noiseRulesApiUrl`/`recomputeActiveNoiseRules`/`compileActiveNoiseRules`/`matchCompiledRules`/`loadNoiseRules`/`openNoiseRulesPanel`/`renderNoiseRulesList`/`renderGlobalOverridesSection`/`renderCustomRulesSection`/`applyNoiseRulesFromPanel`/`exportNoiseRules`/`handleNoiseRuleImportFile`/`addNoiseRule`/`handleNoiseRuleDelete`/`resetNoiseRules`/`saveNoiseRules`/`markNoiseConfigured` | 2149-2175(部分), 2743-2760, 2838-3365 | 依赖 C1、C2、Sig；**被 C5 反向调用**（过滤判定）→ 抽出时判定函数须先于 C5 可用 |
| C8 | **strategy+modals 扫描策略与弹窗** → `js/strategy.js` | `__ss`/`loadScanStrategy`/`loadGlobalScanStrategy`/`renderScanProfileSelect`/`renderSsPageContent`/`open/closeScanStrategyPanel`/`renderSs*`/`currentEditingProfile`/`saveSsStrategy`/`resetSsStrategy`/`newSsProfile`/`copySsProfile`/`deleteSsProfile`/`exportSsProfiles`/`importSsProfiles`/`addSsRule`/`openAddEntryModal`/`closeAddEntryModal`/`setVerifyStatus`/`resetVerify`/`renderScanResults`/`updateScanStats`/`readAddEntryInput`/`loadMethodsForClass`/`parseEntryString`/`entryActions`/`EXCLUDE_REASON_PRESETS`/`openExcludeModal`/批量排除/`guideState`/`guideAction`/init 段 | 3861-5068, 5069-5133 | 依赖 C1-C4、C7；**必须最后搬**（组合根/初始化留在 app.js 或此簇） |

> `app.js` 最终退化为**组合根**：保留 `els` 构建 + 各模块 `init(依赖)` 调用 + 事件绑定注册（或随簇带走绑定）。

---

## 三、落地顺序（风险从低到高）

1. **C1 api** —— 零状态零依赖，最安全，已落地（见 §六）。
2. **C2 ui**（先建共享状态通道，见 §四-3）—— 它是所有后续簇的依赖，必须第 2 个；风险在于 `els` 与 `switchView` 被全员使用，用依赖注入/共享 App 对象解决。
3. **C3 projects+Git** —— 功能自成一体，与其它簇的耦合点集中在 `enterProject` 调 `autoLoad*`/`loadNoiseRules`（通过回调或晚绑定解决）。
4. **C4 entries** —— 与 C8 弹窗互调较多，先于 C8 但晚于 C3。
5. **C6 batch+freq** —— 与 C5 双向耦合（行内渲染 vs 频次数据），一起验证但分两个 commit（batch 先、freq 后）亦可。
6. **C5 result** —— 依赖面最广（Sig、噪声过滤、频次、批量行状态）。
7. **C7 noise** —— 判定函数被 C5 反向调用，抽出后通过共享模块前向引用；改动面横跨渲染与编辑两块。
8. **C8 strategy+modals** —— 最大最杂（900+ 行），且含 init 组合根，**必须最后**。

**不能先搬的**：任何读写 §1.3 共享状态的函数，在共享状态通道建立前都不能搬（否则闭包状态丢失）。

---

## 四、拆分机制

### 4.1 新文件加载顺序（`index.html:721-723`）

```html
<script src="guide.js?v=..."></script>
<script src="js/sig.js?v=..."></script>
<script src="js/api.js?v=20260919a"></script>   <!-- C1 -->
<script src="js/ui.js?v=..."></script>          <!-- C2（后续） -->
<script src="app.js?v=..."></script>            <!-- 组合根，永远最后 -->
```

每次搬一簇：`app.js` 的 `?v=` 必须提升；新文件用自己的 `?v=`。

### 4.2 命名空间约定

沿用 `sig.js` 的 UMD 双导出模板：

```js
(function (root, factory) {
    'use strict';
    var Api = factory();
    if (typeof module !== 'undefined' && module.exports) { module.exports = Api; }  // Node
    if (root) { root.Api = Api; }                                                    // 浏览器
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';
    // ...函数体逐行照搬...
    return { postJson: postJson, fetchJson: fetchJson, putJson: putJson };
});
```

各簇命名：`Api`/`Ui`/`Projects`/`Entries`/`ResultView`/`Batch`/`Noise`/`Strategy`。

### 4.3 共享状态传递（IIFE 拆分后闭包状态不能丢）

- **第一步（C1/C2 之间）**：新建 `js/state.js`（UMD，`root.App`），把 §1.3 的共享 `let` 变量**原值搬入** `App.state`（getter/setter 或直接属性），app.js 与后续各簇统一经 `App.state.currentResult` 等读写。搬移时必须**逐个变量 commit**，每搬一批跑全量验证。
- **后续簇**：模块工厂不直接拿 DOM，由 app.js 在 init 时注入：`Ui.init(els)`、`Projects.init({ els, api: Api, ui: Ui })` 等。依赖只增不改语义。
- **禁止**：把共享 `let` 变量改成 `window.x`（回到 OPT-32 清理前的老路）。

### 4.4 事件绑定与 DOM 初始化顺序

- 现在 146 处 `addEventListener` 内联在各簇区域，脚本同步加载、绑定即执行。拆分后**保持绑定语句所在簇的相对顺序不变**：app.js（组合根）按原行号顺序调用各模块导出的 `bind()`，或该簇的 `init()` 内部按原顺序执行绑定。
- 页面初始化段（5114-5131）保留在 app.js 最后，**最后一步** `switchView('projects'); refreshProjectList();` 的顺序不得改变。

---

## 五、逐簇验证方法

每簇 commit 前必须全绿：

1. **语法**：`node --check src/main/resources/static/app.js`（及所有新增 `js/*.js`）。
2. **契约**：`node src/test/js/contract.test.js` → 必须 `132/132 断言通过`。
3. **后端全量**：`mvn -B clean test -DforkCount=0` → 193 用例 / 15 失败全 ENV-01（GitCloneServiceRefTest 6、GitPrepareServiceTest 5、GitRefServiceTest 4）。

**人工冒烟清单**（每簇聚焦本簇 + 高频共用路径）：

- 通用：打开首页 → 项目卡片渲染 → 进入项目 → 返回列表 → 刷新。
- C1：进入项目（fetchJson）、点击分析（postJson）、保存策略（putJson）各触发一次网络请求且错误 toast 正常。
- C2：切换视图、顶部错误条显示/清除、toast、确认弹窗（进入项目点删除→取消）、loading 面板步骤条与进度条。
- C3：导入本地项目、Git 项目状态徽标、分支/Tag 下拉、检查更新、切换分支进度、删除项目。
- C4：入口清单渲染、勾选/全选/反选、过滤输入、批量分析发起、Excel 下载。
- C5：结果树展开/收起、徽标与噪声过滤、节点行内搜索（Enter/上下个命中/关闭）、全局搜索、统计面板数字随过滤变化。
- C6：批量分析逐入口加载、行内展开/收起、频次 Top-N 分页、调用方展开、来源筛选 chips、项目级搜索定位。
- C7：噪声规则弹窗/页面切换层级、增删改规则、启用开关、保存/重置/导出/导入、过滤立即生效。
- C8：扫描策略弹窗与全局页面、方案新建/复制/删除/导入导出、规则二级编辑、添加入口弹窗（手动+自动扫描+校验）、批量排除弹窗。

---

## 六、首个可落地簇：C1 `api.js`（已执行）

### 步骤

1. 新建 `src/main/resources/static/js/api.js`：UMD 双导出（`window.Api` + `module.exports`），`postJson`/`fetchJson`/`putJson` **函数体从 app.js:795-828 逐行照搬**，零修改。
2. `app.js`：删除 795-828 三个函数定义；在 IIFE 顶部（`const $` 之后）加同名薄委托：
   ```js
   const postJson = Api.postJson;
   const fetchJson = Api.fetchJson;
   const putJson = Api.putJson;
   ```
   约 29 处调用点（postJson 15 行 / fetchJson 10 行 / putJson 4 行）**零改动**；委托置于 IIFE 顶部，规避任何执行期 TDZ。
3. `index.html:722-723`：在 sig.js 与 app.js 之间插入 `<script src="js/api.js?v=20260919a"></script>`；`app.js` 提升为 `?v=20260919a`。
4. 验证三连：`node --check` × 3 文件 → 通过；`node src/test/js/contract.test.js` → 132/132；`mvn -B clean test -DforkCount=0` → 193 用例 / 15 失败全 ENV-01。
5. 单独 commit（只含 `js/api.js`、`app.js`、`index.html`）。

### 后续簇的共同前置

- 搬 C2 前先落 `js/state.js`（§4.3），把 `currentResult/currentProjectId` 等 3-5 个最高频变量先迁入 `App.state`，验证后再继续；每次迁移单独 commit。

---

## 七、风险与「明确不要做」

**风险**

1. **闭包状态截断**：任何函数搬出后仍引用原闭包 `let` → ReferenceError 或静默拿到不同实例。对策：§1.3 状态清单逐项核对 + 搬出后 `node --check` 只能查语法，靠 mvn + 冒烟兜底；每个变量迁移单独 commit。
2. **绑定顺序漂移**：146 处绑定若乱序，`els` 尚未就绪或互相覆盖。对策：绑定随簇走但保持原相对顺序。
3. **`?v=` 忘提**：浏览器缓存旧 app.js 配新 api.js（或反之）。对策：每 commit 同时改两处 `?v=`，列入提交前检查单。
4. **引导流程（Guide）**：`guideState/guideAction` 依赖项目/噪声状态，搬动时序错误会让引导条误判。对策：C8 最后搬，init 段不动。
5. **契约测试盲区**：132 断言只覆盖 Sig/噪声口径，不覆盖 DOM 行为 → 冒烟清单不可省。

**明确不要做**

- ❌ 不重写逻辑、不改函数行为、不优化命名/格式/空行（逐行照搬）。
- ❌ 不把共享状态放 `window.*`。
- ❌ 不引入构建工具/打包器/ES Module（无构建工具现状保持）。
- ❌ 不一次搬多簇、不在一个 commit 里混入行为修复。
- ❌ 不删 `app.js` 中任何看似无用的注释/占位（如 app.js:830 的"已移除"注记、1202 的占位注释）。
- ❌ 发现"搬动必须改行为"的情况：停下报告，不硬搬。

---

## 八、进度记录

| 日期 | 事项 | Commit |
|---|---|---|
| 2026-09-18 | C1 `api.js` 落地（postJson/fetchJson/putJson，薄委托方案） | `199ce86` |

---

## 九、勘误与补充（QA 审查意见，2026-09-18）

> 首簇 `199ce86` 经 QA 独立验证 **PASS**（函数体逐行零差异、26 处调用点零改动、TDZ 安全、回归 193/15 全 ENV-01、契约 132/132）。以下据此更新：

1. **勘误（§六-2 调用点计数）**：原写"约 29 处调用点（postJson 15/fetchJson 10/putJson 4）"——15/10/4 **含 3 个定义行**，真实调用点为 **26 处**（postJson 14 / fetchJson 9 / putJson 3，另有 3 处定义已删除）。
2. **勘误（§1.2 行号基准）**：本文行号均以基线 `9fa4571` 为准；C1 落地后 `app.js` 整体 **+6 行偏移**。**每簇落地后行号需重算**，后续簇不得照搬本文行号。
3. **补充（组合根时序铁律，§4.3）**：`els` 在 app.js IIFE 执行期一次性构建——C2 落地后必须保证 `Ui.init(els)` 先于任何使用 els 的模块 init，**init 注入顺序 = 原 IIFE 内绑定/初始化的行号顺序**。
4. **补充（状态重置归属）**：`resetBatchModel`/`resetGlobalSearch` 等重置函数搬簇后，`App.state` 各字段的清零职责须写明归属簇，防止"半重置"。
5. **补充（冒烟留痕）**：每簇的人工冒烟结果应记录在 commit message 或 §八进度表，否则"全绿"无法回溯是否做过冒烟。
