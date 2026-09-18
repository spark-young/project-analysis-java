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
| 2026-09-18 | C2 `state.js` + `ui.js` 落地（详见下方 C2 落地记录） | `83fd601` |
| 2026-09-20 | C3 `projects.js` 落地（详见下方 C3 落地记录，state.js 扩至 15 字段） | `6d836c6` |
| 2026-09-21 | C4 `entries.js` 落地（详见下方 C4 落地记录，state.js 扩至 17 字段） | 见 git log（OPT-27 C4） |

### C2 落地记录（state.js + ui.js）

**state.js 收编字段（仅 C2 实际需要，后续簇按头注释占位扩展）：**

| 字段 | 初始值 | 写入方 | 读取方 | 清零（重置）职责归属 |
|---|---|---|---|---|
| `currentView` | `'projects'` | ui.js `switchView`（唯一写入方） | app.js `guideState` | 无独立重置场景——switchView 每次调用整体覆盖（原 app.js:341） |
| `noiseRuleMode` | `'panel'` | ui.js `switchView`（离开 noiseRules 视图重置 `'panel'`，原 app.js:334）；app.js 噪声簇（navToNoiseRules=`'page'`、btnBackToProjects/btnNrPageBack/openNoiseRulesPanel=`'panel'`，原行号 611/623/637/2910） | app.js 噪声簇（renderNoiseRulesList / renderGlobalOverridesSection / renderCustomRulesSection / applyNoiseRulesFromPanel / resetNoiseRules / saveNoiseRules 等） | **重置 `'panel'` 的唯一权威是 switchView**；页面→弹窗回退由 btnBackToProjects / btnNrPageBack 显式写 `'panel'`，与原行为逐字一致（防"半重置"） |

**ui.js 收编函数（全部逐行搬运，仅 switchView 的 3 个标识符走共享通道）：**
`$`、`els`（原 13-264 整块）、`switchView`（原 330-343）、`showError`/`clearError`（原 676-683）、`toastTimer`+`showToast`（原 685-697）、`confirmResolver`+`showConfirm`+`settleConfirm`（原 699-717）、**确认弹窗 4 个按钮 click + Escape keydown 绑定随函数一同搬入**（原 718-724，纯监听注册、无相互依赖的监听器，注册时点提前无行为影响）、loading 系列含 `loadingTick`/`loadingStartAt` 内部状态（原 726-799）、`badge`/`badgeHtml`/`escapeHtml`（原 3433-3449，escapeHtml 保持单一来源）。
`resetLoadingPanel` / `loadingSetProgress` / `$` / `toastTimer` / `confirmResolver` / `loadingTick` / `loadingStartAt` 仅被 ui.js 内部使用，不导出不委托。

**app.js 侧改动**：顶部新增 13 个薄委托（els/switchView/showError/clearError/showToast/showConfirm/settleConfirm/showLoading/loadingSetStep/hideLoading/badge/badgeHtml/escapeHtml）+ `Ui.init({ guideRefresh })` 注入；删除被搬代码；14 处 `noiseRuleMode` 与 1 处 `currentView` 读点改为 `App.state.*`（机械改名，同一存储，零行为差异）。

**els 构建时机保证**：ui.js 由 index.html 在 app.js 之前、`</body>` 前同步加载，els 在 ui.js 工厂执行期构建一次——与原先（app.js IIFE 执行期）处于同一段 DOM 就绪时序，元素全部位于脚本标签上方，只构建一次。

**init 注入顺序（对应 QA 补充第 3 点）**：ui.js 自建 els 等价于「Ui.init(els) 先于一切使用」；app.js 顶部即调 `Ui.init({ guideRefresh })`（guideRefresh 是提升的函数声明，注入先于任何 switchView 调用——首次调用在 app.js 末尾 init 段）；app.js 其余绑定按原行号顺序不变。已知偏差：confirm 弹窗 5 处绑定随函数提前到 ui.js 执行，因其仅依赖 els+内部状态且与其他监听器无先后依赖（Escape 各 handler 互不干扰、无 stopPropagation），无行为影响。

**C2 人工冒烟清单（⚠️ 需用户在浏览器实测——本沙箱无浏览器，DOM 行为契约测试覆盖不到）：**
1. 顶部导航三个入口（项目/分析/过滤规则）切换，active 高亮正确、视图 hidden 切换正确。
2. 项目卡片「进入项目」→「返回项目列表」往返，面包屑徽标正确。
3. 删除项目弹出确认框 → 取消（不删）→ 再点 → 确定（删除）；确认框打开时按 Escape 等价于取消。
4. 触发一次失败请求（如填不存在的路径点检查）→ 顶部错误条出现且文案正确；成功操作后错误条清除。
5. 任意成功/失败操作的 toast 提示浮出、约 2.2s 自动消失（error/warn 有样式区分）；连续触发只显示最后一条。
6. 进入项目触发 loading 遮罩：单行文案模式；批量分析触发分步模式（步骤点亮、进度条推进、>3s 出现"已用时 N 秒"、>20s 出现耐心等待文案）；完成后遮罩隐藏。
7. 结果树内徽标（badge）渲染正常、无 HTML 转义破版（含引号/尖括号的方法名）。
8. 独立过滤规则页面进入/返回，模式在弹窗（panel）与页面（page）间切换后再次进项目弹窗模式正确（验证 noiseRuleMode 重置归属）。

### C3 落地记录（state.js 扩展 + projects.js）

**state.js 收编字段（C3 批次 13 个，写入方/读取方/清零归属见 state.js 头注释）：**

| 字段 | 初始值 | 归属要点 |
|---|---|---|
| `currentProjectId` | `null` | 写=projects.js（enterProject/deleteProject/btnBackToProjects/navToAnalyze）；读=各未搬簇约 40 处；置 null 权威=deleteProject 删当前项目 / btnBackToProjects |
| `sourceMode` | `'local'` | 写=projects.js（enterProject/setSourceMode）+ app.js 恢复块；读=projects.js currentProjectPath；setSourceMode 为唯一模式切换权威 |
| `gitProjectPath` | `null` | 写=projects.js（enterProject/pollGitStatus/btnGitPrepare 置 null）+ app.js 恢复块；重置权威=btnGitPrepare 提交新任务前显式置 null |
| `gitPollTimer` | `null` | **句柄类**：创建=projects.js btnGitPrepare / app.js 恢复块（互斥）；**唯一清理点=stopGitPoll**（判空后 clear+置 null，可安全重复调用；btnGitPrepare 重入前先调 stopGitPoll，无泄漏） |
| `currentResult`/`currentRequest`/`currentBatchSummary`/`currentCacheFileName` | `null` | 写=projects.js enterProject（切换项目整体重置 null）+ app.js C5/C6；清零权威=enterProject |
| `currentEntryList`/`currentCandidates` | `null`/`[]` | 写=projects.js autoLoadEntryList（含失败兜底置 `{confirmed:[],excluded:[]}`）+ app.js C4 |
| `guideProjectCount`/`guideHasResult`/`guideResultStale` | `null`/`false`/`false` | 写=projects.js（renderProjectList/refreshProjectList 失败兜底/updateStep3Hint）；读=app.js guideState 只读渲染 |

**有意不迁入 App.state（最小化共享面）**：`projectIndex`（仅 projects.js 内部）、`gitSwitchTimer`（句柄仅 Git 切换轮询内部：创建=startGitSwitch、唯一清理点=stopGitSwitchPoll，判空可重复 clear）、`gitRefsCache`（仅 loadGitRefs 写，无读取方）。

**projects.js 收编函数（逐行搬运，async/await 语法原样保留）**：projects 段 = `refreshProjectList`/`renderProjectList`/`projectCardHtml`/`enterProject`/`autoLoadEntryList`/`autoLoadCacheForProject`/`updateStep3Hint`/`deleteProject`；Git 段 = `setSourceMode`/`currentProjectPath`/`GIT_STATUS_LABEL`/`renderJobLog`/`renderGitStatus`/`stopGitPoll`/`pollGitStatus`/`formatCheckTime`/`GIT_REMOTE_STATUS_LABEL`/`showGitInfoBar`/`hideGitInfoBar`/`renderGitRemoteBadge`/`loadGitRefs`/`checkGitRemoteStatus`/`renderGitSwitchProgress`/`setGitSwitchBusy`/`stopGitSwitchPoll`/`onGitSwitchDone`/`pollGitSwitchStatus`/`startGitSwitch`；事件绑定 10 个随 `init()` 收编（注册顺序 = 原行号顺序 349/363/364/372/373/418/419/559/794/799）。

**晚绑定 hooks（Projects.init 注入）**：`guideRefresh`（app.js 单一来源）+ `loadNoiseRules`/`loadScanStrategy`/`renderEntryList`/`renderResult`（未搬的 C4/C5/C7/C8 函数，函数声明提升保证注入点可用）。

**app.js 侧改动**：顶部 8 个薄委托（refreshProjectList/enterProject/autoLoadEntryList/autoLoadCacheForProject/setSourceMode/currentProjectPath/renderGitStatus/pollGitStatus）+ `Projects.init(...)`；删除两段被搬代码；12 个状态名机械改名 `App.state.*`（约 55 处，含恢复块 :2723-2734）；navToNoiseRules/btnNrPageBack/btnInfo 绑定**留在 app.js**（属 C6 噪声簇/项目检查行为，避免为 C3 提前搬 C6 的 noiseRuleScope）。

**C3 人工冒烟清单（⚠️ 需用户在浏览器实测）：**
1. 项目列表加载/刷新，卡片状态徽标（已就绪/需编译/待分析/已丢失）正确。
2. 点「进入分析」→ loading 分步推进 → 进入分析视图；本地项目路径回填输入框。
3. 项目卡片「移除」→ 原生 confirm → 当前项目被删时自动回项目列表视图。
4. 本地路径导入：填路径 → 识别 → loading → 自动进入。
5. Git 拉取：填仓库地址提交 → 进度条/编译日志实时滚动跟随 → DONE 后提示从列表进入；页面刷新后在途任务恢复继续轮询（gitPollTimer 重建）。
6. Git 分支/Tag：下拉加载、切换确认弹窗、进度条、DONE toast、自动重新进入项目；stash/冲突提示文案正确。
7. 「检查更新」→ 远端状态徽标（已最新/有更新/未知）与最近检查时间。
8. 返回项目列表 → currentProjectId 置空 → 点导航「分析」被拦截并回列表（验证 currentProjectId 清零归属）。

### C4 落地记录（state.js 扩展 + entries.js）

**state.js 收编字段（C4 批次 2 个，归属见 state.js 头注释）：**

| 字段 | 初始值 | 归属要点 |
|---|---|---|
| `currentExcelMode` | `'entry'` | 写=app.js C6（renderBatchSummary→'project'、renderSingleEntryResult→'entry'，视图切换整体覆盖）；读=entries.js btnExcel 分支；无独立清零场景 |
| `entrySelKeys` | `new Set()` | 写=app.js C8 桥接绑定（change/全选/确认排除后 clear）+ entries.js updateEntryToolbar（清单空 clear）；读=entries.js renderEntryRow/updateEntryToolbar + app.js btnBatchExclude；**clear 权威=updateEntryToolbar（空清单）与 excludeModalConfirm 成功后**，Set 就地增删无重赋值 |

**entryItems 归属判断（QA 点名项）**：grep 全文件确认 `entryItems` 的全部 11 处读写（原 :197-325）均落在本次搬出的 C4 代码块内（renderEntries 建模型、勾选/过滤/统计/全选按钮读取），本簇外（C5~C8）**零引用** → 与 C3 `gitSwitchTimer` 同判例，**留在 entries.js 模块私有**，不进 App.state。
**excludeModalItems 归属修正**：方案 §二 原把它列在 C4，但 grep 显示其全部 5 处使用都在 C8 批量排除弹窗区、C4 代码零引用 → **留在 app.js，随 C8 外搬**（勘误 §二）。
**currentExcelMode 归属修正**：方案原未列出，但 C4 的 btnExcel 读它、C6 的两处视图切换写它 → 跨簇，迁入 App.state。

**entries.js 收编函数（逐行搬运）**：勾选分析段 = `renderEntries`/`collectCheckedEntries`/`buildEntryRequest`/`analyzeCheckedEntries`/`updateEntryCount`/`applyEntryFilter` + 4 个绑定（entryFilter/btnEntryAll/btnEntryNone/btnAnalyzeEntries）；Excel 段 = `btnExcel` 绑定 + `downloadProjectExcel`；Step2 清单段 = `entryKey`/`renderEntryList`/`renderEntryRow`/`updateEntryToolbar`。注：`renderEntries` 当前在 app.js 中已无调用方（历史遗留），按清单原样搬运、保持无调用状态。

**晚绑定 hooks（Entries.init 注入）**：`renderResult`（C5）、`guideRefresh`（app.js 单一来源）、`readableFullSig`/`sigHtmlFromString`（C5 签名渲染）、`getFreqFilter`/`getBatchModel`（C6 状态活读 getter——btnExcel 与 downloadProjectExcel 在执行期读取，保持活读语义，非注入时快照）。已搬簇走命名空间：`Projects.currentProjectPath()`。

**app.js 侧改动**：顶部 4 个薄委托（entryKey/renderEntryList/updateEntryToolbar/downloadProjectExcel）+ `Entries.init`（置于 Projects.init 之前，因 Projects.init 的 renderEntryList 接线引用该委托）；删除两段被搬代码；`currentExcelMode`（2 处）与 `entrySelKeys`（4 处）机械改名 App.state.*；C8 桥接绑定 3 个（entryConfirmedList change/entryCheckAll/btnBatchExclude）留在 app.js（引用 C8 的 openExcludeModal）。

**C4 人工冒烟清单（⚠️ 需用户在浏览器实测）：**
1. 进入项目 → Step2 清单渲染（已确认/已排除统计、行序号、组徽标、手动徽标、完整签名格式）。
2. 过滤输入框实时过滤行显示；清空恢复。
3. 行勾选 → 已选计数与批量排除按钮可用性；全选框三态（全选/半选/未选）正确。
4. 勾选后点「分析」→ loading → 结果渲染（analyzeCheckedEntries → renderResult 链路）。
5. 单入口结果视图点 Excel → 单入口报告下载（entry 模式）。
6. 批量清单视图点 Excel → 项目级聚合报告下载（project 模式，验证 currentExcelMode 切换）。
7. 勾选 → 批量排除弹窗 → 排除成功 → 清单刷新且勾选集清空（entrySelKeys clear 归属）。
8. 已排除区「恢复」→ 回到已确认清单。

---

## 九、勘误与补充（QA 审查意见，2026-09-18）

> 首簇 `199ce86` 经 QA 独立验证 **PASS**（函数体逐行零差异、26 处调用点零改动、TDZ 安全、回归 193/15 全 ENV-01、契约 132/132）。以下据此更新：

1. **勘误（§六-2 调用点计数）**：原写"约 29 处调用点（postJson 15/fetchJson 10/putJson 4）"——15/10/4 **含 3 个定义行**，真实调用点为 **26 处**（postJson 14 / fetchJson 9 / putJson 3，另有 3 处定义已删除）。
2. **勘误（§1.2 行号基准）**：本文行号均以基线 `9fa4571` 为准；C1 落地后 `app.js` 整体 **+6 行偏移**。**每簇落地后行号需重算**，后续簇不得照搬本文行号。
3. **补充（组合根时序铁律，§4.3）**：`els` 在 app.js IIFE 执行期一次性构建——C2 落地后必须保证 `Ui.init(els)` 先于任何使用 els 的模块 init，**init 注入顺序 = 原 IIFE 内绑定/初始化的行号顺序**。
4. **补充（状态重置归属）**：`resetBatchModel`/`resetGlobalSearch` 等重置函数搬簇后，`App.state` 各字段的清零职责须写明归属簇，防止"半重置"。
5. **补充（冒烟留痕）**：每簇的人工冒烟结果应记录在 commit message 或 §八进度表，否则"全绿"无法回溯是否做过冒烟。
