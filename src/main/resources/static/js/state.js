/*
 * state.js —— app.js 拆分的共享状态通道（OPT-27 C2/C3 批次字段）
 * ==================================================
 *
 * 背景：app.js 是单 IIFE，约 50 个顶层 let 为各簇闭包共享。逐簇外搬时，
 * 被多簇读写的字段必须先迁入本模块，避免闭包状态被截断成两份。
 *
 * 原则（QA 补充）：**只搬当前簇实际需要的字段**，后续簇外搬时按此模式追加；
 * 每个字段必须写明「写入方 / 读取方 / 清零（重置）职责归属」，防止"半重置"
 * （一簇重置了另一簇不知道）。
 *
 * —— C2 迁入字段 ——
 *
 *   currentView   初始 'projects'
 *     写入：ui.js switchView（唯一写入方，视图切换即整体覆盖）
 *     读取：app.js guideState（新手引导只读状态）
 *     清零职责：无独立重置场景——switchView 每次调用都整体覆盖（原 app.js:341）。
 *
 *   noiseRuleMode 初始 'panel'   // 'panel'（弹窗）| 'page'（独立页面）
 *     写入：ui.js switchView（离开 noiseRules 视图时重置 'panel'，原 app.js:334）；
 *           app.js 噪声规则簇（navToNoiseRules='page'、btnBackToProjects/btnNrPageBack='panel'、
 *           openNoiseRulesPanel='panel'，原行号 611/623/637/2910）
 *     读取：app.js 噪声规则簇（renderNoiseRulesList / renderGlobalOverridesSection /
 *           renderCustomRulesSection / applyNoiseRulesFromPanel / resetNoiseRules /
 *           saveNoiseRules 等，按 mode 选弹窗或页面容器）
 *     清零职责：**重置 'panel' 的唯一权威是 switchView**；页面→弹窗的回退由
 *           btnBackToProjects / btnNrPageBack 显式写 'panel'，与原行为逐字一致。
 *
 * —— C3 迁入字段（projects + Git 簇，OPT-27）——
 *
 *   currentProjectId  初始 null   // 当前选中项目 id
 *     写入：projects.js enterProject（进入时赋值）、deleteProject（删除当前项目置 null）、
 *           btnBackToProjects / navToAnalyze 绑定（读写）；
 *           app.js 未搬簇大量只读引用（清单/结果/噪声/策略簇，原 :83 声明）
 *     读取：app.js guideState / guideAction 以及各未搬簇（约 40 处）
 *     清零职责：置 null 的唯一场景 = deleteProject 删除当前项目、btnBackToProjects
 *           返回项目列表（原 :342/:365）；enterProject 每次进入整体覆盖。无定时器职责。
 *
 *   sourceMode  初始 'local'   // 'local' | 'git'（项目来源）
 *     写入：projects.js enterProject（按项目 type 赋值）、setSourceMode（唯一模式切换权威，
 *           同步 tab 高亮与双 pane 显隐）；app.js 页面加载恢复块（/api/git/latest 恢复时写 'git'）
 *     读取：projects.js currentProjectPath / setSourceMode
 *     清零职责：无独立清零场景——enterProject / setSourceMode / 恢复块每次整体覆盖。
 *
 *   gitProjectPath  初始 null   // Git 拉取编译完成后的本地目录
 *     写入：projects.js enterProject（GIT 项目赋值 / 本地项目置 null）、
 *           pollGitStatus DONE 时赋值、btnGitPrepare 开始新任务时置 null；
 *           app.js 恢复块（/api/git/latest DONE 时赋值）
 *     读取：projects.js currentProjectPath（git 模式下返回此值）
 *     清零职责：btnGitPrepare 提交新任务前显式置 null（原 :564）——防旧路径残留误导
 *           currentProjectPath；enterProject 进入本地项目时也置 null。
 *
 *   gitPollTimer  初始 null   // 【句柄类状态】Git 拉取任务轮询 setInterval 句柄
 *     写入（创建）：projects.js btnGitPrepare 绑定（提交任务成功后启动，原 :575）；
 *           app.js 页面加载恢复块（在途任务继续轮询，原 :3348）——两处创建互斥
 *           （恢复块仅页面加载执行一次；btnGitPrepare 启动前先调 stopGitPoll）
 *     清理（clearInterval）：**唯一清理点 = projects.js stopGitPoll**（pollGitStatus 遇
 *           终态 DONE/FAILED、请求异常、轮询失败时调用，原 :526）。stopGitPoll 先判空再
 *           clear 并置 null，**可安全重复调用**；btnGitPrepare 重入（用户连续提交任务）
 *           前也先调 stopGitPoll（原 :563），旧句柄必被处置，无泄漏。
 *     读取：无（句柄只被写/清，不读）。
 *
 *   currentResult  初始 null   // 最近一次成功分析的结果 DTO
 *     写入：projects.js enterProject（进入新项目时重置 null）、autoLoadCacheForProject
 *           （命中缓存时赋值）；app.js C5 结果簇（正常分析完成后赋值）
 *     读取：app.js C5 结果簇（renderResult / Excel 导出等）
 *     清零职责：enterProject 项目切换时重置 null（原 :212）——防上个项目结果串场。
 *
 *   currentRequest  初始 null   // 最近一次成功分析的请求（Excel 复用）
 *     写入：projects.js enterProject（重置 null）；app.js 分析簇
 *     清零职责：同上（原 :213）。
 *
 *   currentBatchSummary  初始 null   // 批量分析轻量索引（清单视图）
 *     写入：projects.js enterProject（重置 null）；app.js C6 批量簇
 *     清零职责：同上（原 :214）。
 *
 *   currentCacheFileName  初始 null   // 批量中当前展开入口的缓存文件名（Excel 用）
 *     写入：projects.js enterProject（重置 null）；app.js C6 批量簇
 *     清零职责：同上（原 :215）。
 *
 *   currentEntryList  初始 null   // EntryList DTO（confirmed + excluded）
 *     写入：projects.js autoLoadEntryList（拉取成功赋值 / 失败置 {confirmed:[],excluded:[]}）、
 *           enterProject 不直接写；app.js C4 清单簇（增删改后重新赋值）
 *     读取：app.js C4 清单簇 + guideState
 *     清零职责：autoLoadEntryList 失败兜底置空对象（原 :274）；无其他独立清零。
 *
 *   currentCandidates  初始 []   // 本次扫描新增的候选（临时）
 *     写入：projects.js autoLoadEntryList（每次拉取后置 []）；app.js C4 扫描簇
 *     清零职责：autoLoadEntryList 每次执行重置（原 :268/:41）。
 *
 *   guideProjectCount  初始 null   // 已导入项目数（null = 尚未拉到，引导先不渲染避免闪烁）
 *     写入：projects.js renderProjectList（= list.length）、refreshProjectList
 *           （请求失败时置 0）；app.js 未搬簇无写入
 *     读取：app.js guideState（只读渲染）
 *     清零职责：无独立清零——每次列表刷新整体覆盖；失败兜底置 0（原 :107/:114）。
 *
 *   guideHasResult  初始 false   // 是否已有可用分析结果
 *     写入：projects.js updateStep3Hint（= !!cacheInfo.hasCache）
 *     读取：app.js guideState
 *     清零职责：updateStep3Hint 每次整体覆盖（原 :307）。
 *
 *   guideResultStale  初始 false   // 结果是否已因清单变化而过期
 *     写入：projects.js updateStep3Hint（= !!cacheInfo.dirty）
 *     读取：app.js guideState
 *     清零职责：updateStep3Hint 每次整体覆盖（原 :308）。
 *
 * —— C4 迁入字段（entries 入口清单视图簇，OPT-27）——
 *
 *   currentExcelMode  初始 'entry'   // 'entry' 单入口导出 | 'project' 项目级导出
 *     写入：app.js C6 结果簇（renderBatchSummary 写 'project'、renderSingleEntryResult
 *           写 'entry'，随视图切换整体覆盖）；entries.js 不写、只在 btnExcel 中读分支
 *     读取：entries.js btnExcel 绑定（决定走 downloadProjectExcel 还是单入口导出）
 *     清零职责：无独立清零场景——视图切换时整体覆盖（原 app.js:54 声明）。
 *
 *   entrySelKeys  初始 new Set()   // 清单中勾选待排除的入口 key 集合
 *     写入：app.js C8 桥接绑定（entryConfirmedList change 增删、entryCheckAll 全选增删、
 *           excludeModalConfirm 成功后 clear）；entries.js updateEntryToolbar（清单空时 clear）
 *     读取：entries.js renderEntryRow（勾选回显）/ updateEntryToolbar（已选数统计）、
 *           app.js btnBatchExclude（取已选项打开排除弹窗）
 *     清零职责：**唯一 clear 权威 = updateEntryToolbar（清单为空时）与 excludeModalConfirm
 *           成功提交后**（原 app.js:73 声明）；Set 就地增删，无整体重赋值场景。
 *
 * —— C6 迁入字段（batch+freq 批量与频次簇，OPT-27）——
 *
 *   batchModel  初始 null   // 批量全量加载模型 {batch,projectId,entries,done,total,failed,loaded,projectFreq}
 *     写入：batch.js resetBatchModel（换批次整体重建，唯一重赋值点）/ loadAllBatchEntries
 *           （重建 entries 槽位、写 done/total/failed）/ finalizeBatchLoad（写 loaded/projectFreq）
 *     读取：batch.js 内部多处 + app.js C5（computeBatchFilteredStats 统计条）+
 *           entries.js getBatchModel hook（项目级 Excel 取已加载入口）
 *     清零职责：resetBatchModel 是唯一重建权威（原 app.js:98 声明，:419 重赋值）；
 *           renderBatchSummary 判断 batch !== batchModel.batch 决定重建或复用，无置 null 场景。
 *
 *   batchRowStates  初始 []   // 批量清单每行的行内展开状态 { idx,entry,open,rendered,result,roots,body,rowEl }
 *     写入：batch.js renderBatchSummary（length=0 就地清空 + 逐行填充，含 rowEl/body DOM 引用）
 *     读取：batch.js（toggleEntryBody/buildEntryBodySync/revealHitPath/markEntryRows）+
 *           app.js C5（reapplyFilterToTree / updateFilteredStats）
 *     清零职责：renderBatchSummary 重绘清单时 length=0 就地清空（原 app.js:71 声明，:281）——
 *           与旧 DOM 行同生命周期，无其他独立清零场景。
 *
 *   freqFilter  初始 'ALL'   // 频次来源筛选：ALL/PROJECT/DEPENDENCY/EXTERNAL
 *     写入：batch.js（showProjectPanels / renderFreqAnalysis 重置 'ALL'、freqList 委托绑定
 *           的 chip 切换赋值）
 *     读取：batch.js renderFreqList / updateFreqFilterChips + entries.js getFreqFilter hook
 *           （Excel 导出带上当前来源筛选）
 *     清零职责：重置 'ALL' 的权威 = showProjectPanels（展示项目面板）与 renderFreqAnalysis
 *           （每次新分析）（原 app.js:96 声明，:511/:1245）；chip 切换为用户显式赋值。
 *
 * —— 有意**不**迁入 App.state 的 C3/C4 内部状态（最小化共享面）——
 *   projectIndex   仅 projects.js 内部使用（refreshProjectList 建索引、enterProject 查询）
 *   gitSwitchTimer 【句柄】仅 Git 分支切换轮询内部使用：创建=startGitSwitch（原 :786）、
 *                  唯一清理点=stopGitSwitchPoll（原 :709，判空后 clear 置 null，可安全重复
 *                  调用；hideGitInfoBar/切换终态/提交失败均会调用，重入无泄漏）
 *   gitRefsCache   仅 loadGitRefs 写入（原 :636），当前无读取方（下拉数据直写 DOM）
 *   entryItems     仅 entries.js 内部使用（勾选/过滤/统计读取；
 *                  含 rowEl/checkEl DOM 引用，grep 确认本簇外零引用，与 gitSwitchTimer 同判例）
 *   excludeModalItems 仅 app.js C8 批量排除弹窗区使用（本簇外零引用；C8 外搬时随簇走）
 *   freqViewMode / projSearchMarks / projSearchOrder / projSearchCursor / _freqRows /
 *                  _freqPage   仅 batch.js 内部使用（频次视图切换、项目级搜索定位、频次分页；
 *                  grep 确认本簇外零引用，模块私有）
 *
 * 后续簇预期（占位说明，迁入时再真正添加字段）：
 *   C5 result：      nodeRegistry / hitRows / activeSearch / expandFns
 *   C7 noise：       noiseRules / globalRulesCache / projectRulesCache / globalOverrides /
 *                    activeNoiseRules / compiledActiveRules / activeNoiseHash / noiseRuleScope
 *   C8 strategy：    scanStrategy / globalScanStrategy / ss* / dsContext
 */
(function (root, factory) {
    'use strict';
    var App = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = App;                 // Node
    }
    if (root) {
        root.App = App;                       // 浏览器（ui.js / projects.js / app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    return {
        state: {
            // ---- C2 迁入（原 app.js 行号见头注释） ----
            currentView: 'projects',
            noiseRuleMode: 'panel',
            // ---- C3 迁入（projects + Git 簇） ----
            currentProjectId: null,           // 原 app.js:83 逐字迁入（含初始值）
            sourceMode: 'local',              // 原 app.js:406 逐字迁入（含初始值）
            gitProjectPath: null,             // 原 app.js:407 逐字迁入（含初始值）
            gitPollTimer: null,               // 原 app.js:408 逐字迁入（句柄类，见头注释 clear 责任）
            currentResult: null,              // 原 app.js:33 逐字迁入
            currentRequest: null,             // 原 app.js:34 逐字迁入
            currentBatchSummary: null,        // 原 app.js:35 逐字迁入
            currentCacheFileName: null,       // 原 app.js:36 逐字迁入
            currentEntryList: null,           // 原 app.js:40 逐字迁入
            currentCandidates: [],            // 原 app.js:41 逐字迁入
            guideProjectCount: null,          // 原 app.js:45 逐字迁入
            guideHasResult: false,            // 原 app.js:46 逐字迁入
            guideResultStale: false,          // 原 app.js:47 逐字迁入
            // ---- C4 迁入（entries 入口清单视图簇） ----
            currentExcelMode: 'entry',        // 原 app.js:54 逐字迁入（含初始值）
            entrySelKeys: new Set(),          // 原 app.js:73 逐字迁入（Set 就地增删，见头注释 clear 归属）
            // ---- C6 迁入（batch+freq 批量与频次簇） ----
            batchModel: null,                 // 原 app.js:98 逐字迁入（含初始值；重建权威=resetBatchModel）
            batchRowStates: [],               // 原 app.js:71 逐字迁入（含 rowEl/body DOM 引用，length=0 就地清空）
            freqFilter: 'ALL'                 // 原 app.js:96 逐字迁入（重置 'ALL' 权威=showProjectPanels/renderFreqAnalysis）
        }
    };
});
