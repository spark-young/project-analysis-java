/* Java工程分析工具 - 前端逻辑（原生 JS，无外部依赖） */
(function () {
    'use strict';

    // 请求层已抽出 js/api.js（OPT-27 拆分）：此处保留同名薄委托，
    // 调用点零改动、行为零变化（Api 由 index.html 在本文件之前加载）。
    const postJson = Api.postJson;
    const fetchJson = Api.fetchJson;
    const putJson = Api.putJson;

    // 基础 UI（els / switchView / toast / confirm / error / loading / badge / escapeHtml）
    // 已抽出 js/ui.js（OPT-27 C2）：同样保留同名薄委托，调用点零改动。
    // els 在 ui.js 加载时构建一次（脚本位于 </body> 前，DOM 就绪时序与原先一致）。
    const els = Ui.els;
    const switchView = Ui.switchView;
    const showError = Ui.showError;
    const clearError = Ui.clearError;
    const showToast = Ui.showToast;
    const showConfirm = Ui.showConfirm;
    const settleConfirm = Ui.settleConfirm;
    const showLoading = Ui.showLoading;
    const loadingSetStep = Ui.loadingSetStep;
    const hideLoading = Ui.hideLoading;
    const badge = Ui.badge;
    const badgeHtml = Ui.badgeHtml;
    const escapeHtml = Ui.escapeHtml;
    // init 注入（方案 §4.3）：switchView 内部需要通知引导刷新；
    // guideRefresh 仍是 app.js 的函数（单一来源），在此移交给 ui.js。
    Ui.init({ guideRefresh: guideRefresh });

    // 项目列表/进入/删除 + Git 拉取轮询与分支切换已抽出 js/projects.js（OPT-27 C3）：
    // 同样保留同名薄委托，调用点零改动；Projects.init 注入未搬簇函数（晚绑定，方案 §4.3）。
    const refreshProjectList = Projects.refreshProjectList;
    const enterProject = Projects.enterProject;
    const autoLoadEntryList = Projects.autoLoadEntryList;
    const autoLoadCacheForProject = Projects.autoLoadCacheForProject;
    const setSourceMode = Projects.setSourceMode;
    const currentProjectPath = Projects.currentProjectPath;
    const renderGitStatus = Projects.renderGitStatus;
    const pollGitStatus = Projects.pollGitStatus;

    // 入口清单视图（渲染/勾选/过滤/发起分析/Excel）已抽出 js/entries.js（OPT-27 C4）：
    // 同样保留同名薄委托，调用点零改动；Entries.init 注入未搬簇函数与 C6 状态活读 getter。
    const entryKey = Entries.entryKey;
    const renderEntryList = Entries.renderEntryList;
    const updateEntryToolbar = Entries.updateEntryToolbar;
    const downloadProjectExcel = Entries.downloadProjectExcel;

    // 批量加载与频次分析（批量清单/行内展开/项目级搜索定位/频次区）已抽出 js/batch.js（OPT-27 C6）：
    // 同样保留同名薄委托，调用点零改动；Batch.init 注入未搬簇函数与 C5 共享对象的活读 getter。
    const renderBatchSummary = Batch.renderBatchSummary;
    const readableFullSig = Batch.readableFullSig;
    const sigHtmlFromString = Batch.sigHtmlFromString;
    const appendSigFromString = Batch.appendSigFromString;
    const clearProjectSearch = Batch.clearProjectSearch;
    const expandGuided = Batch.expandGuided;
    const applyNodeMark = Batch.applyNodeMark;   // C5 nodeEl 渲染每行时套用命中标记
    const refreshFreqView = Batch.refreshFreqView;
    const renderFreqAnalysis = Batch.renderFreqAnalysis;
    const splitMethodSignature = Batch.splitMethodSignature;

    // 结果树渲染与搜索（renderResult/renderWarnings/nodeEl/rootsOf/统计/搜索/全部展开收起）
    // 已抽出 js/result.js（OPT-27 C5）：同样保留同名薄委托，调用点零改动；ResultView.init
    // 注入未搬簇函数（guideRefresh 引导刷新 / C7 噪声判定与规则哈希）。
    const renderResult = ResultView.renderResult;
    const renderWarnings = ResultView.renderWarnings;
    const nodeEl = ResultView.nodeEl;
    const graphIndex = ResultView.graphIndex;
    const rootsOf = ResultView.rootsOf;
    const renderStats = ResultView.renderStats;
    const computeFilteredStats = ResultView.computeFilteredStats;
    const renderSingleEntryResult = ResultView.renderSingleEntryResult;
    const hideBatchEntryHeader = ResultView.hideBatchEntryHeader;
    const updateBatchRowStats = ResultView.updateBatchRowStats;
    const computeBatchFilteredStats = ResultView.computeBatchFilteredStats;
    const backToBatchList = ResultView.backToBatchList;
    const clearSearchHits = ResultView.clearSearchHits;
    const resetGlobalSearch = ResultView.resetGlobalSearch;
    const reapplyFilterToTree = ResultView.reapplyFilterToTree;
    const runGlobalSearch = ResultView.runGlobalSearch;
    const expandAll = ResultView.expandAll;
    const collapseAll = ResultView.collapseAll;

    // 噪声规则管理已抽出 js/noise.js（OPT-27 C7）：同样保留同名薄委托，调用点零改动；
    // Noise.init 在下方授予 guideRefresh 引用（方案 §4.3 晚绑定 hooks 注入模式）。
    const noiseRulesHash = Noise.noiseRulesHash;
    const isNoiseGraphMethod = Noise.isNoiseGraphMethod;
    const isNoiseMethod = Noise.isNoiseMethod;
    const loadNoiseRules = Noise.loadNoiseRules;
    const renderNoiseRulesList = Noise.renderNoiseRulesList;
    const filterFreqMethod = Noise.filterFreqMethod;
    const openNoiseRulesPanel = Noise.openNoiseRulesPanel;
    const closeNoiseRulesPanel = Noise.closeNoiseRulesPanel;
    const refreshAllFilteredViews = Noise.refreshAllFilteredViews;
    const markNoiseConfigured = Noise.markNoiseConfigured;
    const applyNoiseRulesFromPanel = Noise.applyNoiseRulesFromPanel;
    const exportNoiseRules = Noise.exportNoiseRules;
    const handleNoiseRuleImportFile = Noise.handleNoiseRuleImportFile;
    const addNoiseRule = Noise.addNoiseRule;
    const handleNoiseRuleDelete = Noise.handleNoiseRuleDelete;
    const resetNoiseRules = Noise.resetNoiseRules;
    const saveNoiseRules = Noise.saveNoiseRules;
    Noise.init({ guideRefresh: guideRefresh });

    ResultView.init({
        guideRefresh: guideRefresh,
        isNoiseGraphMethod: isNoiseGraphMethod,
        noiseRulesHash: noiseRulesHash,
    });

    Entries.init({
        renderResult: renderResult,
        guideRefresh: guideRefresh,
        readableFullSig: readableFullSig,
        sigHtmlFromString: sigHtmlFromString,
        // C6 已把 freqFilter/batchModel 迁入 App.state：getter 改为活读 App.state，
        // entries.js 侧调用代码语义不变（仍执行期调用 hook，无注入时快照）。
        getFreqFilter: () => App.state.freqFilter,
        getBatchModel: () => App.state.batchModel,
    });

    Projects.init({
        guideRefresh: guideRefresh,
        loadNoiseRules: loadNoiseRules,
        loadScanStrategy: loadScanStrategy,
        renderEntryList: renderEntryList,
        renderResult: renderResult,
    });

    Batch.init({
        renderStats: renderStats,
        computeBatchFilteredStats: computeBatchFilteredStats,
        renderWarnings: renderWarnings,
        hideBatchEntryHeader: hideBatchEntryHeader,
        renderSingleEntryResult: renderSingleEntryResult,
        rootsOf: rootsOf,
        nodeEl: nodeEl,
        updateBatchRowStats: updateBatchRowStats,
        graphIndex: graphIndex,
        isNoiseGraphMethod: isNoiseGraphMethod,
        isNoiseMethod: isNoiseMethod,
        filterFreqMethod: filterFreqMethod,
        getNodeRegistry: ResultView.getNodeRegistry,     // C5 const Map，C6 清空/读、C5 写（身份恒定）
        getSourceLabel: ResultView.getSourceLabel,        // C5 常量表（C5 nodeEl 共用，单一来源）
        clearExpandFns: ResultView.clearExpandFns,        // C5 数组，C6 仅就地截断
    });

    // els（150+ 元素 DOM 缓存）已抽出 js/ui.js（OPT-27 C2），见顶部薄委托。

    // App.state.currentResult / App.state.currentRequest / App.state.currentBatchSummary / App.state.currentCacheFileName /
    // App.state.currentEntryList / App.state.currentCandidates 已迁入 App.state（OPT-27 C3：
    // projects.js 的 enterProject/autoLoad* 写入，写入方/清零归属见 js/state.js 头注释）。
    // currentExcelMode 已迁入 App.state（OPT-27 C4：C6 视图切换写入、entries.js btnExcel 读取）
    // batchRowStates 已迁入 App.state（OPT-27 C6，含 DOM 引用，见 js/state.js 头注释）
    // Step 2 入口清单状态

    // ---- 新手引导只读状态（guide.js 消费，不参与业务逻辑） ----
    // currentView / guideProjectCount / guideHasResult / guideResultStale 已迁入
    // js/state.js 的 App.state（OPT-27 C2/C3：projects.js 写入、guideState 只读）

    /** 通知引导刷新（引导未加载时静默跳过） */
    function guideRefresh() {
        if (window.Guide) Guide.refresh();
    }

    /** 引导用：用户是否配置过过滤规则（C7 状态已迁入 noise.js，通过访问器活读） */
    function guideNoiseConfigured() {
        if (window.Guide && Guide.isSeen(Guide.NOISE_CONFIGURED)) return true;
        const prc = Noise.getProjectRulesCache();
        if (prc && prc.length > 0) return true;
        const go = Noise.getGlobalOverrides();
        return !!(go && Object.keys(go).length > 0);
    }
    // entrySelKeys 已迁入 App.state（OPT-27 C4：C4 渲染读 + C8 桥接绑定写，清零归属见 state.js）
    let excludeModalItems = [];    // 批量排除弹窗当前承载的条目（C8 弹窗状态，本簇外零引用，随 C8 外搬）
    let searchTimer = null;
    // expandFns / nodeRegistry / hitRows / activeSearch 已随结果树簇抽出 js/result.js（OPT-27 C5），
    // 见顶部薄委托；nodeRegistry 与 expandFns 经 ResultView.getNodeRegistry/clearExpandFns 供 C6 活读。
    // freqFilter / batchModel / batchRowStates 已迁入 App.state（OPT-27 C6：batch.js
    // 读写、C5 统计簇与 entries.js hook 活读，清零/重建归属见 js/state.js 头注释）；
    // freqViewMode / projSearchMarks / projSearchOrder / projSearchCursor 为 batch.js
    // 模块私有（grep 确认本簇外零引用，不进 App.state）。
    // C7 噪声规则状态已迁入 js/noise.js（OPT-27 C7）：noiseRules / globalRulesCache / projectRulesCache /
    // globalOverrides / activeNoiseRules / compiledActiveRules / activeNoiseHash / noiseRuleScope /
    // noiseMemo / freqNoiseMemo / _lastAppliedRulesHash 为 noise.js 模块私有，通过 Noise.getProjectRulesCache()
    // 与 Noise.getGlobalOverrides() 访问器供 app.js guideNoiseConfigured() 活读，其余状态仅 C7 函数内部引用。
    // noiseRuleMode 已迁入 js/state.js 的 App.state（OPT-27 C2：switchView 离开
    // noiseRules 视图时重置 'panel'，噪声簇读写页面/弹窗模式，防"半重置"归属见 state.js 头注释）
    // currentProjectId / gitSwitchTimer / gitRefsCache 已迁出（OPT-27 C3）：
    // currentProjectId 进 App.state（多簇共享）；gitSwitchTimer/gitRefsCache 为
    // projects.js 模块私有（不进 App.state，理由见 state.js 头注释）
    // 扫描策略状态
    let scanStrategy = null;         // 当前项目的有效扫描策略（ScanStrategy DTO）
    let ssEditingProfileId = null;   // 管理弹窗中正在编辑的方案 id
    let ssEditingRuleId = null;      // 二级规则编辑弹窗中正在编辑的规则 id（null = 新增）
    let dsContext = 'modal';         // 扫描策略编辑上下文：'modal'（项目级弹窗）| 'page'（全局页面）
    let globalScanStrategy = null;   // 全局扫描策略（页面版单独维护）
    let ssPageEditingProfileId = null; // 页面版正在编辑的方案 id

    // ==============================================================
    // 项目列表 / 进入 / 删除（refreshProjectList / renderProjectList / projectCardHtml /
    // enterProject / autoLoadEntryList / autoLoadCacheForProject / updateStep3Hint /
    // deleteProject）及其事件绑定（btnImportLocal/btnRefreshProjects/btnBackToProjects/
    // navToProjects/navToAnalyze）已抽出 js/projects.js（OPT-27 C3），见顶部薄委托；
    // projectIndex / gitSwitchTimer / gitRefsCache 为该模块私有（不进 App.state）。
    // ==============================================================
    // navToNoiseRules / btnNrPageBack 事件绑定已迁入 js/noise.js（OPT-27 C7），见该文件底部绑定区。
    // ==============================================================

    // 旧缓存批次切换 UI + 重新分析按钮 已移除（改为单份缓存 + Step3 状态提示）

    // 项目来源切换（sourceMode/gitProjectPath 状态、setSourceMode/currentProjectPath、
    // tabLocal/tabGit 绑定）已抽出 js/projects.js（OPT-27 C3）；
    // sourceMode/gitProjectPath 迁入 App.state（归属见 js/state.js 头注释）。

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    // showError / clearError / showToast / showConfirm / settleConfirm / loading 系列
    // 已抽出 js/ui.js（OPT-27 C2），见顶部薄委托；confirm 弹窗的 4 个按钮点击与
    // Escape 键绑定随函数一同搬入 ui.js（纯监听注册，无相互依赖的监听器，时序无行为影响）。

    // postJson / fetchJson / putJson 已抽出 js/api.js（OPT-27），见本文件顶部薄委托。

    // buildRequest / validateForm / className 补全 已移除（路径 A/B 按钮已删）

    // ------------------------------------------------------------------
    // 项目检查
    // ------------------------------------------------------------------

    els.btnInfo.addEventListener('click', async () => {
        clearError();
        const path = els.projectPath.value.trim();
        if (!path) { showError('请填写项目路径'); return; }
        els.projectInfo.textContent = '检查中……';
        els.projectInfo.className = 'hint';
        try {
            const resp = await fetch('/api/project/info?path=' + encodeURIComponent(path));
            const info = await resp.json();
            if (!resp.ok) throw new Error(info.error || '检查失败');
            els.projectInfo.className = 'hint ok';
            els.projectInfo.textContent =
                '布局: ' + info.layoutLabel + ' · 项目类: ' + info.projectClassCount +
                ' 个 · 依赖 jar: ' + info.dependencyJarCount + ' 个' +
                (info.warnings && info.warnings.length ? ' · 告警 ' + info.warnings.length + ' 条' : '');
        } catch (e) {
            els.projectInfo.className = 'hint err';
            els.projectInfo.textContent = e.message;
        }
    });

    // ------------------------------------------------------------------
    // Git 拉取 + 编译轮询（GIT_STATUS_LABEL/renderJobLog/renderGitStatus/stopGitPoll/
    // pollGitStatus/btnGitPrepare 绑定）与 分支/Tag 切换 + 远端更新检测（formatCheckTime/
    // GIT_REMOTE_STATUS_LABEL/showGitInfoBar/hideGitInfoBar/renderGitRemoteBadge/loadGitRefs/
    // checkGitRemoteStatus/renderGitSwitchProgress/setGitSwitchBusy/stopGitSwitchPoll/
    // onGitSwitchDone/pollGitSwitchStatus/startGitSwitch/btnGitSwitch/btnGitCheckUpdate 绑定）
    // 已抽出 js/projects.js（OPT-27 C3），见顶部薄委托；
    // gitPollTimer 句柄迁入 App.state（clear 责任见 js/state.js 头注释）。
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 交易入口扫描 + 勾选分析（renderEntries/collectCheckedEntries/buildEntryRequest/
    // analyzeCheckedEntries/updateEntryCount/applyEntryFilter）与 Excel 下载
    // （btnExcel 绑定/downloadProjectExcel）已抽出 js/entries.js（OPT-27 C4），见顶部薄委托；
    // entryItems 为该模块私有（含 DOM 引用，本簇外零引用，不进 App.state）；
    // currentExcelMode 迁入 App.state（写入方=C6 视图切换，归属见 js/state.js 头注释）。
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // 结果渲染（renderResult/renderSingleEntryResult/backToBatchList/hideBatchEntryHeader/
    // renderStats/computeFilteredStats/updateBatchRowStats/computeBatchFilteredStats/
    // renderWarnings/nodeEl/matchNode/collectMatches/clearSearchHits/toggleSearchBar/
    // focusMatches/resetGlobalSearch/runGlobalSearch/graphIndex/rootsOf/
    // invalidateAdapterCache/reapplyFilterToTree 与 SOURCE_LABEL/INVOKE_LABEL 常量、
    // expandFns/nodeRegistry/hitRows/activeSearch/_adapterCache/_graphIndexCache 状态）
    // 已抽出 js/result.js（OPT-27 C5），见顶部薄委托。
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // "可读参数 → JVM 描述符"与入口串解析：逻辑已统一到共享模块 Sig（sig.js）。
    // 调用点见 parseEntryString（下方委托 Sig.parseEntryString）。
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // 图结构适配层：把 schema=2 的 result.graph 惰性还原为树视图节点。
    // 不整体物化冗余树：每个节点的 children 是 getter，首次访问才构建子节点；
    // 环方法作为叶子返回（子树已在上层路径，避免全局搜索无限递归）。
    // 惰性根缓存 _adapterCache 已随 C5 抽出（rootsOf 内部，result.js）。
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 噪声规则管理（isNoiseGraphMethod/isNoiseMethod/filterFreqMethod/loadNoiseRules/
    // renderNoiseRulesList/openNoiseRulesPanel/refreshAllFilteredViews/applyNoiseRulesFromPanel/
    // saveNoiseRules/resetNoiseRules/markNoiseConfigured 等全部函数 + 相关事件绑定）
    // 已抽出 js/noise.js（OPT-27 C7），见顶部薄委托；noiseMemo/freqNoiseMemo 判定记忆与
    // 两层规则缓存状态一并迁入 noise.js（Noise.getProjectRulesCache/Noise.getGlobalOverrides 活读）。
    // ------------------------------------------------------------------


    els.btnGlobalSearch.addEventListener('click', runGlobalSearch);
    els.globalSearchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') runGlobalSearch();
    });
    els.btnGlobalSearchClear.addEventListener('click', () => {
        resetGlobalSearch();
        clearSearchHits();
    });

    // ------------------------------------------------------------------
    // 全部展开 / 收起
    // ------------------------------------------------------------------

    els.btnExpandAll.addEventListener('click', expandAll);

    els.btnCollapseAll.addEventListener('click', collapseAll);

    // 结果区「刷新过滤」：重新拉取两层规则（含项目覆盖）→ 重算调用链剪枝 + 统计 + 频率
    els.btnResultRefresh.addEventListener('click', () => {
        if (!els.noiseRulesPanel.hidden || !els.viewNoiseRules.hidden) {
            applyNoiseRulesFromPanel();
        } else {
            loadNoiseRules().then(refreshAllFilteredViews);
        }
    });

    els.btnBackToList.addEventListener('click', () => {
        backToBatchList();
    });

    // 项目路径不预填：输入框留空由用户自行填写，示例写法见其 placeholder 提示

    // 页面加载时拉取样板方法过滤规则
    loadNoiseRules();

    // 启动：先加载项目列表，默认停在项目列表视图
    refreshProjectList();

    // 页面加载时恢复最近一个未过期的 Git 任务（在途或刚完成）
    // 无任务时后端返回 200 空响应体，r.json() 会抛错，故 catch 成 null
    fetch('/api/git/latest').then(r => r.ok ? r.json().catch(() => null) : null).then(st => {
        if (!st || !st.status) return;
        // 恢复仓库地址到输入框
        if (st.repoUrl) els.repoUrl.value = st.repoUrl;
        // 自动切到 Git tab，让用户立刻看到进度条
        setSourceMode('git');
        if (st.status === 'DONE') {
            // 已完成：恢复 App.state.gitProjectPath，用户可以直接进入分析
            App.state.gitProjectPath = st.projectPath;
            App.state.sourceMode = 'git';
            renderGitStatus({
                status: 'DONE',
                message: (st.message || '✓ 项目已导入') + ' —— 请从项目列表中点击"进入分析"'
            });
            refreshProjectList();
        } else if (st.status === 'FAILED') {
            renderGitStatus(st);
        } else {
            // 在途中（PENDING/CLONING），继续轮询
            App.state.gitPollTimer = setInterval(() => pollGitStatus(st.jobId), 2000);
            renderGitStatus(st);
            els.btnGitPrepare.disabled = true;
        }
    });

    // ==================================================================
    // Step 2: 已确认的交易入口清单 —— 渲染（entryKey/renderEntryList/renderEntryRow/
    // updateEntryToolbar）已抽出 js/entries.js（OPT-27 C4），见顶部薄委托；
    // entrySelKeys 迁入 App.state（C4 渲染读 + C8 桥接绑定写，清零归属见 js/state.js 头注释）。
    // ==================================================================

    // ==============================================================
    // 扫描策略配置（自动扫描方案管理）
    // ==============================================================

    /** 上下文辅助：根据 dsContext 返回当前正在编辑的策略和元素引用 */
    function __ss() {
        const page = dsContext === 'page';
        return {
            strategy: page ? globalScanStrategy : scanStrategy,
            editingId: page ? ssPageEditingProfileId : ssEditingProfileId,
            setEditingId: (id) => { page ? (ssPageEditingProfileId = id) : (ssEditingProfileId = id); },
            profileListEl: page ? els.ssPageProfileList : els.ssProfileList,
            editorEmptyEl: page ? els.ssPageEditorEmpty : els.ssEditorEmpty,
            editorBodyEl: page ? els.ssPageEditorBody : els.ssEditorBody,
            profileNameEl: page ? els.ssPageProfileName : els.ssProfileName,
            profileDescEl: page ? els.ssPageProfileDesc : els.ssProfileDesc,
            builtinTagEl: page ? els.ssPageBuiltinTag : els.ssBuiltinTag,
            detectorsEl: page ? els.ssPageDetectors : els.ssDetectors,
            ruleListEl: page ? els.ssPageRuleList : els.ssRuleList,
            ruleAddEl: page ? els.btnSsPageRuleAdd : els.btnSsRuleAdd,
            deleteBtn: page ? els.btnSsPageProfileDelete : els.btnSsProfileDelete,
            saveUrl: page ? '/api/scan-strategy/global' : '/api/scan-strategy/project/' + App.state.currentProjectId,
            resetUrl: page ? '/api/scan-strategy/global/reset' : '/api/scan-strategy/project/' + App.state.currentProjectId + '/reset',
        };
    }

    /** 从后端加载当前项目的扫描策略并渲染下拉 */
    async function loadScanStrategy() {
        if (!App.state.currentProjectId) return;
        try {
            scanStrategy = await fetchJson('/api/scan-strategy/project/' + App.state.currentProjectId);
            renderScanProfileSelect();
        } catch (e) {
            console.warn('[ScanStrategy] 加载失败:', e);
            scanStrategy = null;
        }
    }

    /** 填充扫描策略下拉框 */
    function renderScanProfileSelect() {
        const sel = els.scanProfileSelect;
        if (!scanStrategy || !scanStrategy.profiles) {
            sel.innerHTML = '<option value="">标准扫描</option>';
            return;
        }
        const activeId = scanStrategy.activeProfileId || 'builtin-standard';
        sel.innerHTML = scanStrategy.profiles.map(p => {
            const label = p.builtin ? p.name + ' (内置)' : p.name;
            return '<option value="' + escapeHtml(p.id) + '"'
                + (p.id === activeId ? ' selected' : '')
                + '>' + escapeHtml(label) + '</option>';
        }).join('');
    }

    /** 从后端加载全局扫描策略（系统配置页面的扫描策略 tab） */
    async function loadGlobalScanStrategy() {
        try {
            globalScanStrategy = await fetchJson('/api/scan-strategy/global');
            if (!ssPageEditingProfileId && globalScanStrategy) {
                ssPageEditingProfileId = globalScanStrategy.activeProfileId || 'builtin-standard';
            }
        } catch (e) {
            console.warn('[GlobalScanStrategy] 加载失败:', e);
            globalScanStrategy = null;
        }
    }

    /** 渲染系统配置页面的扫描策略 tab 内容 */
    function renderSsPageContent() {
        if (!globalScanStrategy) {
            showToast('全局策略数据尚未加载', 'warn');
            return;
        }
        renderSsProfileList();
        renderSsEditor();
    }

    /** 打开扫描策略管理弹窗 */
    function openScanStrategyPanel() {
        if (!scanStrategy) { showToast('策略数据尚未加载', 'warn'); return; }
        dsContext = 'modal';
        ssEditingProfileId = scanStrategy.activeProfileId || 'builtin-standard';
        els.scanStrategyOverlay.hidden = false;
        els.scanStrategyPanel.hidden = false;
        renderSsProfileList();
        renderSsEditor();
    }

    /** 关闭扫描策略管理弹窗（未保存的编辑直接丢弃，从磁盘重新拉取） */
    function closeScanStrategyPanel() {
        els.scanStrategyPanel.hidden = true;
        els.scanStrategyOverlay.hidden = true;
        ssEditingProfileId = null;
        loadScanStrategy();
    }

    /** 渲染左侧方案列表 */
    function renderSsProfileList() {
        const ctx = __ss();
        const activeId = ctx.editingId;
        const profiles = (ctx.strategy && ctx.strategy.profiles) || [];
        ctx.profileListEl.innerHTML = profiles.map(p => {
            const isActive = p.id === activeId;
            const isBuiltin = !!p.builtin;
            const badge = isBuiltin ? '<span class="ss-pi-badge">内置</span>' : '';
            const activeMark = isActive ? '<span class="ss-pi-active">✓ 当前</span>' : '';
            return '<div class="ss-profile-item' + (isActive ? ' active' : '') + '" data-id="' + escapeHtml(p.id) + '">'
                + '<span class="ss-pi-name">' + escapeHtml(p.name) + '</span>'
                + badge
                + activeMark
                + '</div>';
        }).join('');
        ctx.profileListEl.querySelectorAll('.ss-profile-item').forEach(el => {
            el.addEventListener('click', () => {
                const id = el.dataset.id;
                if (id === ctx.editingId) return;
                ctx.setEditingId(id);
                renderSsProfileList();
                renderSsEditor();
            });
        });
        updateSsProfileActions();
    }

    /** 更新方案操作按钮状态（内置不能删/复制） */
    function updateSsProfileActions() {
        const profile = currentEditingProfile();
        const isBuiltin = profile && !!profile.builtin;
        __ss().deleteBtn.disabled = !profile || isBuiltin;
    }

    /** 获取当前正在编辑的方案对象 */
    function currentEditingProfile() {
        const ctx = __ss();
        if (!ctx.strategy || !ctx.strategy.profiles || !ctx.editingId) return null;
        return ctx.strategy.profiles.find(p => p.id === ctx.editingId);
    }

    /** 渲染右侧编辑器 */
    function renderSsEditor() {
        const ctx = __ss();
        const profile = currentEditingProfile();
        if (!profile) {
            ctx.editorEmptyEl.hidden = false;
            ctx.editorBodyEl.hidden = true;
            return;
        }
        ctx.editorEmptyEl.hidden = true;
        ctx.editorBodyEl.hidden = false;

        const isBuiltin = !!profile.builtin;
        ctx.profileNameEl.value = profile.name || '';
        ctx.profileDescEl.value = profile.description || '';
        ctx.builtinTagEl.hidden = !isBuiltin;
        ctx.profileNameEl.disabled = isBuiltin;
        ctx.profileDescEl.disabled = isBuiltin;

        renderSsDetectors(profile, isBuiltin);
        renderSsRuleList(profile, isBuiltin);
    }

    const DETECTOR_LABELS = {
        REST: 'HTTP REST 接口（@Controller / @RestController）',
        DUBBO: 'Dubbo RPC 接口（@DubboService）',
        ELASTIC_JOB: 'ElasticJob 定时任务（extends AbstractSimpleElasticJob）',
        MAIN: 'Main 方法入口（public static void main）',
    };
    const DETECTOR_ORDER = ['REST', 'DUBBO', 'ELASTIC_JOB', 'MAIN'];

    /** 渲染探测器开关 */
    function renderSsDetectors(profile, readonly) {
        const ctx = __ss();
        const detectors = profile.detectors || {};
        ctx.detectorsEl.innerHTML = DETECTOR_ORDER.map(key => {
            const checked = detectors[key] !== false;
            return '<label class="ss-detector-item' + (readonly ? ' readonly' : '') + '">'
                + '<input type="checkbox" data-key="' + key + '"'
                + (checked ? ' checked' : '')
                + (readonly ? ' disabled' : '')
                + '> '
                + (DETECTOR_LABELS[key] || key)
                + '</label>';
        }).join('');
        if (!readonly) {
            ctx.detectorsEl.querySelectorAll('input[data-key]').forEach(cb => {
                cb.addEventListener('change', () => {
                    const profile = currentEditingProfile();
                    if (!profile) return;
                    const key = cb.dataset.key;
                    if (!profile.detectors) profile.detectors = {};
                    profile.detectors[key] = cb.checked;
                });
            });
        }
    }

    const RULE_KIND_LABELS = {
        ANNOTATION_METHOD: '注解方法',
        ANNOTATION_CLASS: '注解类',
        PACKAGE: '包扫描',
        INTERFACE_IMPLEMENT: '继承/实现',
        CLASS_NAME: '类名匹配',
        METHOD_NAME: '方法名匹配',
    };

    /** 渲染自定义规则列表 */
    function renderSsRuleList(profile, readonly) {
        const ctx = __ss();
        const rules = (profile && profile.rules) || [];
        ctx.ruleAddEl.disabled = readonly;
        if (rules.length === 0) {
            ctx.ruleListEl.innerHTML = '<div class="ss-rule-empty">暂无自定义规则</div>';
            return;
        }
        ctx.ruleListEl.innerHTML = rules.map((r, idx) => {
            const kindLabel = RULE_KIND_LABELS[r.kind] || r.kind;
            const summary = describeRule(r);
            const itemCls = 'ss-rule-item' + (r.enabled === false ? ' disabled' : '');
            return '<div class="' + itemCls + '" data-idx="' + idx + '">'
                + '<span class="ss-ri-kind">' + kindLabel + '</span>'
                + '<span class="ss-ri-name">' + escapeHtml(r.name || '(未命名)') + '</span>'
                + '<span class="ss-ri-detail">' + escapeHtml(summary) + '</span>'
                + '<span class="ss-ri-actions">'
                + '<button type="button" class="btn small ss-rule-edit"' + (readonly ? ' disabled' : '') + '>编辑</button>'
                + '<button type="button" class="btn small warn ss-rule-del"' + (readonly ? ' disabled' : '') + '>删除</button>'
                + '</span>'
                + '</div>';
        }).join('');
        ctx.ruleListEl.querySelectorAll('.ss-rule-edit').forEach((btn, idx) => {
            btn.addEventListener('click', () => {
                const items = ctx.ruleListEl.querySelectorAll('.ss-rule-item');
                const realIdx = Array.from(items).indexOf(btn.closest('.ss-rule-item'));
                const rule = profile.rules[realIdx];
                if (rule) openSsRuleEditor(rule, realIdx);
            });
        });
        ctx.ruleListEl.querySelectorAll('.ss-rule-del').forEach((btn, idx) => {
            btn.addEventListener('click', () => {
                const items = ctx.ruleListEl.querySelectorAll('.ss-rule-item');
                const realIdx = Array.from(items).indexOf(btn.closest('.ss-rule-item'));
                if (profile.rules) profile.rules.splice(realIdx, 1);
                renderSsRuleList(profile, readonly);
            });
        });
    }

    /** 生成规则简短的描述文字 */
    function describeRule(r) {
        if (r.kind === 'ANNOTATION_METHOD' || r.kind === 'ANNOTATION_CLASS') {
            return '注解: ' + (r.annotation || '(未设置)');
        }
        if (r.kind === 'PACKAGE') {
            return '包: ' + (r.packagePrefix || '(未设置)') + (r.recursive !== false ? ' (递归)' : '');
        }
        if (r.kind === 'INTERFACE_IMPLEMENT') {
            return '接口/基类: ' + (r.interfaceName || '(未设置)');
        }
        if (r.kind === 'CLASS_NAME' || r.kind === 'METHOD_NAME') {
            return '模式: ' + (r.pattern || '(未设置)');
        }
        return '';
    }

    // ---- 二级规则编辑弹窗 ----

    /** 打开规则编辑弹窗 */
    function openSsRuleEditor(rule, idx) {
        ssEditingRuleId = idx != null ? idx : null;
        els.ssRuleEditorTitle.textContent = idx != null ? '编辑规则' : '新增规则';
        els.ssRuleName.value = (rule && rule.name) || '';
        els.ssRuleKind.value = (rule && rule.kind) || 'ANNOTATION_METHOD';
        els.ssRuleExcludes.value = (rule && rule.excludes) ? rule.excludes.join('\n') : '';
        els.ssRuleEnabled.checked = rule ? (rule.enabled !== false) : true;
        renderSsRuleDynamicFields(els.ssRuleKind.value, rule);
        els.ssRuleEditorOverlay.hidden = false;
        els.ssRuleEditor.hidden = false;
        els.ssRuleName.focus();
    }

    /** 关闭规则编辑弹窗 */
    function closeSsRuleEditor() {
        els.ssRuleEditor.hidden = true;
        els.ssRuleEditorOverlay.hidden = true;
        ssEditingRuleId = null;
    }

    /** 规则类型切换 → 渲染动态字段 */
    function renderSsRuleDynamicFields(kind, rule) {
        let html = '';
        if (kind === 'ANNOTATION_METHOD' || kind === 'ANNOTATION_CLASS') {
            const hint = kind === 'ANNOTATION_METHOD'
                ? '如 org.springframework.web.bind.annotation.GetMapping'
                : '如 org.springframework.stereotype.Controller';
            html = '<div class="modal-field">'
                + '<label class="modal-label">注解全限定名</label>'
                + '<input id="ssRuleAnnotation" class="modal-input" placeholder="' + hint + '"'
                + ' value="' + escapeHtml((rule && rule.annotation) || '') + '">'
                + '</div>';
        } else if (kind === 'PACKAGE') {
            html = '<div class="modal-field">'
                + '<label class="modal-label">包名前缀</label>'
                + '<input id="ssRulePackage" class="modal-input" placeholder="如 com.example.service"'
                + ' value="' + escapeHtml((rule && rule.packagePrefix) || '') + '">'
                + '</div>'
                + '<label class="ss-rule-enabled"><input type="checkbox" id="ssRuleRecursive"'
                + ((rule && rule.recursive !== false) ? ' checked' : '')
                + '> 递归扫描子包</label>';
        } else if (kind === 'INTERFACE_IMPLEMENT') {
            html = '<div class="modal-field">'
                + '<label class="modal-label">接口或基类全限定名</label>'
                + '<input id="ssRuleInterface" class="modal-input" placeholder="如 com.example.MyService"'
                + ' value="' + escapeHtml((rule && rule.interfaceName) || '') + '">'
                + '</div>';
        } else if (kind === 'CLASS_NAME') {
            html = '<div class="modal-field">'
                + '<label class="modal-label">类名通配模式</label>'
                + '<input id="ssRulePattern" class="modal-input" placeholder="如 *Controller、com.example.*"'
                + ' value="' + escapeHtml((rule && rule.pattern) || '') + '">'
                + '</div>'
                + '<div class="modal-field-hint">支持 *（单级包）和 **（多级通配）</div>';
        } else if (kind === 'METHOD_NAME') {
            html = '<div class="modal-field">'
                + '<label class="modal-label">方法名通配模式</label>'
                + '<input id="ssRulePattern" class="modal-input" placeholder="如 handle*、*Event"'
                + ' value="' + escapeHtml((rule && rule.pattern) || '') + '">'
                + '</div>'
                + '<div class="modal-field-hint">支持 *（任意字符）和 **（多段通配）</div>';
        }
        els.ssRuleDynamic.innerHTML = html;
    }

    /** 从弹窗收集规则数据 */
    function collectSsRuleFromEditor() {
        const kind = els.ssRuleKind.value;
        const rule = {
            id: null,
            name: els.ssRuleName.value.trim(),
            kind: kind,
            enabled: els.ssRuleEnabled.checked,
            excludes: els.ssRuleExcludes.value.split('\n')
                .map(s => s.trim()).filter(s => s.length > 0),
        };
        if (kind === 'ANNOTATION_METHOD' || kind === 'ANNOTATION_CLASS') {
            rule.annotation = (document.getElementById('ssRuleAnnotation') || {}).value || '';
        } else if (kind === 'PACKAGE') {
            rule.packagePrefix = (document.getElementById('ssRulePackage') || {}).value || '';
            rule.recursive = (document.getElementById('ssRuleRecursive') || {}).checked !== false;
        } else if (kind === 'INTERFACE_IMPLEMENT') {
            rule.interfaceName = (document.getElementById('ssRuleInterface') || {}).value || '';
        } else if (kind === 'CLASS_NAME' || kind === 'METHOD_NAME') {
            rule.pattern = (document.getElementById('ssRulePattern') || {}).value || '';
        }
        return rule;
    }

    // ---- 事件绑定（扫描策略） ----

    // 管理按钮 → 打开弹窗
    els.btnScanStrategyManage.addEventListener('click', openScanStrategyPanel);
    els.btnSsClose.addEventListener('click', closeScanStrategyPanel);
    els.scanStrategyOverlay.addEventListener('click', closeScanStrategyPanel);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.ssRuleEditor.hidden) closeSsRuleEditor();
        if (e.key === 'Escape' && !els.scanStrategyPanel.hidden && els.ssRuleEditor.hidden) closeScanStrategyPanel();
    });

    // 下拉选择 → 立即切换生效（先取服务端最新数据，避免混入未保存的编辑）
    els.scanProfileSelect.addEventListener('change', async () => {
        if (!App.state.currentProjectId || !scanStrategy) return;
        const newId = els.scanProfileSelect.value;
        try {
            const fresh = await fetchJson('/api/scan-strategy/project/' + App.state.currentProjectId);
            fresh.activeProfileId = newId;
            await putJson('/api/scan-strategy/project/' + App.state.currentProjectId, fresh);
            scanStrategy = fresh;
            renderScanProfileSelect();
            showToast('已切换扫描方案', 'success');
        } catch (e) {
            showToast('切换失败: ' + e.message, 'error');
        }
    });

    // ---- 通用操作函数（同时绑定到弹窗和页面按钮） ----

    /** 保存生效 */
    async function saveSsStrategy() {
        const ctx = __ss();
        const profile = currentEditingProfile();
        if (!profile) return;
        if (profile.builtin) {
            showToast('内置方案不能修改，请复制后编辑', 'warn');
            return;
        }
        const name = ctx.profileNameEl.value.trim();
        if (!name) { showToast('请填写方案名称', 'warn'); return; }
        profile.name = name;
        profile.description = ctx.profileDescEl.value.trim();
        if (ctx.strategy.activeProfileId === profile.id) {
            renderScanProfileSelect();
        }
        try {
            await putJson(ctx.saveUrl, ctx.strategy);
            renderSsProfileList();
            if (dsContext === 'modal') renderScanProfileSelect();
            showToast('✓ 扫描策略已保存', 'success');
        } catch (e) {
            showToast('保存失败: ' + e.message, 'error');
        }
    }
    els.btnSsSave.addEventListener('click', saveSsStrategy);
    els.btnSsPageSave.addEventListener('click', saveSsStrategy);

    /** 恢复默认 */
    async function resetSsStrategy() {
        const ok = await showConfirm('重置将丢失所有自定义方案和设置，确定要恢复默认吗？', '恢复默认');
        if (!ok) return;
        const ctx = __ss();
        try {
            await postJson(ctx.resetUrl, {});
            if (dsContext === 'page') {
                await loadGlobalScanStrategy();
            } else {
                await loadScanStrategy();
            }
            const ctx2 = __ss();
            ctx2.setEditingId(ctx2.strategy.activeProfileId || 'builtin-standard');
            renderSsProfileList();
            renderSsEditor();
            if (dsContext === 'modal') renderScanProfileSelect();
            showToast('已恢复默认扫描策略', 'success');
        } catch (e) {
            showToast('重置失败: ' + e.message, 'error');
        }
    }
    els.btnSsReset.addEventListener('click', resetSsStrategy);
    els.btnSsPageReset.addEventListener('click', resetSsStrategy);

    /** 新建方案 */
    async function newSsProfile() {
        const ctx = __ss();
        try {
            const data = await postJson('/api/scan-strategy/profile/new', {});
            const newId = data.profileId;
            const profile = {
                id: newId,
                name: '新建方案 ' + (ctx.strategy.profiles.length + 1),
                description: '',
                builtin: false,
                detectors: { REST: true, DUBBO: true, ELASTIC_JOB: true, MAIN: true },
                rules: [],
            };
            ctx.strategy.profiles.push(profile);
            ctx.setEditingId(newId);
            renderSsProfileList();
            renderSsEditor();
            showToast('已新建方案，编辑后点击「保存生效」', 'success');
        } catch (e) {
            showToast('新建失败: ' + e.message, 'error');
        }
    }
    els.btnSsProfileNew.addEventListener('click', newSsProfile);
    els.btnSsPageProfileNew.addEventListener('click', newSsProfile);

    /** 复制方案 */
    async function copySsProfile() {
        const ctx = __ss();
        const profile = currentEditingProfile();
        if (!profile) { showToast('请先选择一个方案', 'warn'); return; }
        try {
            const data = await postJson('/api/scan-strategy/profile/new', {});
            const newId = data.profileId;
            const copy = JSON.parse(JSON.stringify(profile));
            copy.id = newId;
            copy.name = profile.name + ' (副本)';
            copy.builtin = false;
            ctx.strategy.profiles.push(copy);
            ctx.setEditingId(newId);
            renderSsProfileList();
            renderSsEditor();
            showToast('已复制方案，编辑后点击「保存生效」', 'success');
        } catch (e) {
            showToast('复制失败: ' + e.message, 'error');
        }
    }
    els.btnSsProfileCopy.addEventListener('click', copySsProfile);
    els.btnSsPageProfileCopy.addEventListener('click', copySsProfile);

    /** 删除方案 */
    async function deleteSsProfile() {
        const ctx = __ss();
        const profile = currentEditingProfile();
        if (!profile) { showToast('请先选择一个方案', 'warn'); return; }
        if (profile.builtin) { showToast('内置方案不能删除', 'warn'); return; }
        const ok = await showConfirm('确定删除方案「' + profile.name + '」？', '删除方案');
        if (!ok) return;
        const idx = ctx.strategy.profiles.indexOf(profile);
        if (idx >= 0) ctx.strategy.profiles.splice(idx, 1);
        if (ctx.strategy.activeProfileId === profile.id) {
            ctx.strategy.activeProfileId = 'builtin-standard';
        }
        ctx.setEditingId(ctx.strategy.activeProfileId || 'builtin-standard');
        try {
            await putJson(ctx.saveUrl, ctx.strategy);
        } catch (e) {
            showToast('删除失败: ' + e.message, 'error');
            return;
        }
        renderSsProfileList();
        renderSsEditor();
        if (dsContext === 'modal') renderScanProfileSelect();
        showToast('已删除方案', 'success');
    }
    els.btnSsProfileDelete.addEventListener('click', deleteSsProfile);
    els.btnSsPageProfileDelete.addEventListener('click', deleteSsProfile);

    // ---- 方案导入 / 导出（跨用户分享自定义扫描方案） ----

    /** 分享文件标识：与过滤规则的 JSON 区分开，便于导入时校验 */
    const SS_SHARE_TYPE = 'callgraph-scan-strategy';
    const SS_SHARE_VERSION = 1;

    /** 导出当前层的自定义方案（内置方案随 jar 走，不参与分享） */
    function exportSsProfiles() {
        const ctx = __ss();
        const profiles = (ctx.strategy && ctx.strategy.profiles) || [];
        const customs = profiles.filter((p) => p && !p.builtin);
        if (customs.length === 0) {
            showToast('当前没有自定义方案，内置方案无需分享', 'warn');
            return;
        }
        const payload = {
            type: SS_SHARE_TYPE,
            version: SS_SHARE_VERSION,
            exportedAt: new Date().toISOString(),
            activeProfileId: ctx.strategy.activeProfileId || '',
            profiles: customs,
        };
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob(
            [JSON.stringify(payload, null, 2)],
            { type: 'application/json;charset=utf-8' }));
        a.download = 'scan-strategy-' + new Date().toISOString().slice(0, 10) + '.json';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(a.href);
        showToast('✓ 已导出 ' + customs.length + ' 个自定义方案', 'success');
    }

    /** 导入他人分享的方案：按 id 合并（同 id 覆盖），内置方案忽略，导入后仍需点「保存生效」 */
    async function importSsProfiles(fileInput) {
        const file = fileInput.files && fileInput.files[0];
        fileInput.value = '';
        if (!file) return;
        let payload;
        try {
            payload = JSON.parse(await file.text());
        } catch (e) {
            showToast('导入失败：不是合法的 JSON 文件', 'error');
            return;
        }
        if (!payload || payload.type !== SS_SHARE_TYPE || !Array.isArray(payload.profiles)) {
            showToast('导入失败：请选择本工具「导出」生成的扫描方案文件', 'error');
            return;
        }
        const incoming = payload.profiles.filter((p) => p && !p.builtin && p.id && p.name);
        if (incoming.length === 0) {
            showToast('文件里没有可导入的自定义方案', 'warn');
            return;
        }
        const ctx = __ss();
        if (!ctx.strategy.profiles) ctx.strategy.profiles = [];
        const existing = ctx.strategy.profiles;
        const dupCount = incoming.filter((p) => existing.some((e) => e.id === p.id)).length;
        const ok = await showConfirm(
            '将导入 ' + incoming.length + ' 个方案'
            + (dupCount > 0 ? '（其中 ' + dupCount + ' 个与现有方案 ID 相同，会被覆盖）' : '')
            + '；导入后需点「保存生效」才会写入。确定继续？',
            '导入扫描方案');
        if (!ok) return;
        let added = 0;
        let replaced = 0;
        incoming.forEach((p) => {
            const copy = JSON.parse(JSON.stringify(p));
            copy.builtin = false;   // 防止伪造内置标记绕过「不可编辑」
            const idx = existing.findIndex((e) => e.id === copy.id);
            if (idx >= 0) { existing[idx] = copy; replaced++; }
            else { existing.push(copy); added++; }
        });
        ctx.setEditingId(incoming[incoming.length - 1].id);
        renderSsProfileList();
        renderSsEditor();
        if (dsContext === 'modal') renderScanProfileSelect();
        showToast('✓ 导入完成：新增 ' + added + ' 个'
            + (replaced > 0 ? '、覆盖 ' + replaced + ' 个' : '')
            + '，点「保存生效」写入', 'success');
    }

    els.btnSsExport.addEventListener('click', exportSsProfiles);
    els.btnSsPageExport.addEventListener('click', exportSsProfiles);
    els.btnSsImport.addEventListener('click', () => {
        els.ssImportFile.value = '';
        els.ssImportFile.click();
    });
    els.ssImportFile.addEventListener('change', () => importSsProfiles(els.ssImportFile));
    els.btnSsPageImport.addEventListener('click', () => {
        els.ssPageImportFile.value = '';
        els.ssPageImportFile.click();
    });
    els.ssPageImportFile.addEventListener('change', () => importSsProfiles(els.ssPageImportFile));

    /** 新增规则 */
    function addSsRule() {
        const profile = currentEditingProfile();
        if (!profile) { showToast('请先选择一个方案', 'warn'); return; }
        if (profile.builtin) { showToast('内置方案不能编辑', 'warn'); return; }
        openSsRuleEditor(null, null);
    }
    els.btnSsRuleAdd.addEventListener('click', addSsRule);
    els.btnSsPageRuleAdd.addEventListener('click', addSsRule);

    // 规则类型切换 → 动态字段
    els.ssRuleKind.addEventListener('change', () => {
        renderSsRuleDynamicFields(els.ssRuleKind.value, null);
    });

    // 规则编辑器确定
    els.ssRuleEditorOk.addEventListener('click', () => {
        const profile = currentEditingProfile();
        if (!profile) { showToast('请先选择一个方案', 'warn'); return; }
        const rule = collectSsRuleFromEditor();
        if (!rule.name) { showToast('请填写规则名称', 'warn'); return; }
        const kind = rule.kind;
        if ((kind === 'ANNOTATION_METHOD' || kind === 'ANNOTATION_CLASS') && !rule.annotation) {
            showToast('请填写注解全限定名', 'warn'); return;
        }
        if (kind === 'PACKAGE' && !rule.packagePrefix) {
            showToast('请填写包名前缀', 'warn'); return;
        }
        if (kind === 'INTERFACE_IMPLEMENT' && !rule.interfaceName) {
            showToast('请填写接口或基类全限定名', 'warn'); return;
        }
        if ((kind === 'CLASS_NAME' || kind === 'METHOD_NAME') && !rule.pattern) {
            showToast('请填写通配模式', 'warn'); return;
        }
        const isEdit = ssEditingRuleId != null;
        if (isEdit) {
            const existing = profile.rules.find((r, i) => i === ssEditingRuleId);
            if (existing) {
                Object.assign(existing, rule);
                existing.id = null;
            }
        } else {
            rule.id = null;
            if (!profile.rules) profile.rules = [];
            profile.rules.push(rule);
        }
        closeSsRuleEditor();
        const readonly = !!profile.builtin;
        renderSsRuleList(profile, readonly);
        showToast('规则已' + (isEdit ? '更新' : '添加'), 'success');
    });

    // 规则编辑器取消/关闭
    els.ssRuleEditorCancel.addEventListener('click', closeSsRuleEditor);
    els.ssRuleEditorClose.addEventListener('click', closeSsRuleEditor);
    els.ssRuleEditorOverlay.addEventListener('click', closeSsRuleEditor);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.ssRuleEditor.hidden) closeSsRuleEditor();
    });

    // ---- 系统配置页面 tab 切换 ----

    els.scTabNoise.addEventListener('click', () => {
        els.scTabNoise.classList.add('active');
        els.scTabScan.classList.remove('active');
        els.scTabContentNoise.hidden = false;
        els.scTabContentNoise.classList.add('active');
        els.scTabContentScan.hidden = true;
        els.scTabContentScan.classList.remove('active');
    });

    els.scTabScan.addEventListener('click', async () => {
        els.scTabNoise.classList.remove('active');
        els.scTabScan.classList.add('active');
        els.scTabContentNoise.hidden = true;
        els.scTabContentNoise.classList.remove('active');
        els.scTabContentScan.hidden = false;
        els.scTabContentScan.classList.add('active');
        dsContext = 'page';
        await loadGlobalScanStrategy();
        renderSsPageContent();
    });

    // ---- Step 2 按钮事件 ----

    // 🔍 自动扫描 → diff → 弹窗展示候选（可勾选）→ 用户确认后批量加入
    els.btnEntryScan.addEventListener('click', async () => {
        if (!App.state.currentProjectId) { showError('请先进入项目'); return; }
        clearError();
        const profile = scanStrategy && scanStrategy.profiles
            ? scanStrategy.profiles.find(p => p.id === (scanStrategy.activeProfileId || 'builtin-standard'))
            : null;
        const profileLabel = profile ? profile.name : '标准扫描';
        showLoading('正在按「' + profileLabel + '」扫描交易入口，需解析项目字节码，请稍候…');
        try {
            const resp = await postJson(
                '/api/projects/' + App.state.currentProjectId + '/entries/scan',
                { profileId: (scanStrategy ? scanStrategy.activeProfileId : undefined) });
            hideLoading();
            const candidates = resp.candidates || [];
            const scanExisted = resp.existed || 0;
            openAddEntryModal('auto');
            if (candidates.length === 0) {
                els.addEntryScanWrap.hidden = false;
                els.addEntryScanList.innerHTML = '<div class="scan-result-empty">未扫描到可加入的新方法（当前清单已是最新）</div>';
                els.addEntryScanStats.textContent = '扫描到 0 个可加入的方法' + (scanExisted > 0 ? '；' + scanExisted + ' 个已在清单中' : '');
                setVerifyStatus('扫描完成', 'warn');
            } else {
                currentScanCandidates = candidates;
                renderScanResults(candidates, scanExisted);
                setVerifyStatus('扫描完成，请选择要加入的方法', 'ok');
            }
        } catch (e) {
            hideLoading();
            showError('扫描失败: ' + e.message);
        }
    });

    // ➕ 手动添加 → Modal（扫描机制：扫描出方法列表 → 勾选 → 批量加入）
    let addEntryVerified = false;       // 扫描是否已完成
    let currentScanCandidates = [];     // 当前扫描出的候选方法（add-batch 提交用）
    let currentScanExisted = 0;         // 当前扫描结果中已在清单中的数量
    let addEntryModalMode = 'manual';   // 'manual' | 'auto'，控制 modal 输入区显示
    function openAddEntryModal(mode) {
        mode = mode || 'manual';
        addEntryModalMode = mode;
        if (mode === 'auto') {
            els.addEntryModalTitle.textContent = '📋 自动扫描结果';
            els.addEntryInputArea.hidden = true;
            els.addEntryVerify.hidden = true;
        } else {
            els.addEntryModalTitle.textContent = '➕ 手动添加交易入口';
            els.addEntryInputArea.hidden = false;
            els.addEntryVerify.hidden = false;
            els.addEntryClass.value = '';
            els.addEntryMethod.innerHTML = '<option value="">留空（扫描该类下所有命中规则的方法）</option>';
            els.addEntryMethodText.value = '';
            els.addEntryPaste.value = '';
        }
        setVerifyStatus(mode === 'auto' ? '扫描完成，请选择要加入的方法' : '未扫描', '');
        addEntryVerified = false;
        currentScanCandidates = [];
        currentScanExisted = 0;
        els.addEntryScanWrap.hidden = true;
        els.addEntryScanList.innerHTML = '';
        els.addEntryScanAll.checked = true;
        els.addEntryConfirm.disabled = true;
        els.addEntryConfirm.textContent = '确定加入（0）';
        els.addEntryOverlay.hidden = false;
        els.addEntryModal.hidden = false;
        if (mode === 'manual') els.addEntryClass.focus();
    }
    function closeAddEntryModal() {
        els.addEntryOverlay.hidden = true;
        els.addEntryModal.hidden = true;
    }
    function setVerifyStatus(text, type) {
        // type: ''(gray) | 'ok'(green) | 'warn'(amber) | 'err'(red)
        els.addEntryVerifyStatus.textContent = text;
        els.addEntryVerifyStatus.className = 'modal-verify-status' + (type ? ' vs-' + type : '');
    }
    // 输入变动 → 重置扫描状态
    function resetVerify() {
        if (addEntryVerified) {
            addEntryVerified = false;
            currentScanCandidates = [];
            currentScanExisted = 0;
            els.addEntryScanWrap.hidden = true;
            els.addEntryScanList.innerHTML = '';
            els.addEntryScanAll.checked = true;
            els.addEntryConfirm.disabled = true;
            els.addEntryConfirm.textContent = '确定加入（0）';
            setVerifyStatus('已修改，请重新扫描', 'warn');
        }
    }
    // 渲染扫描结果列表 + 更新统计/确认按钮
    function renderScanResults(candidates, existed) {
        els.addEntryScanWrap.hidden = false;
        const listEl = els.addEntryScanList;
        listEl.innerHTML = '';
        currentScanExisted = existed || 0;
        if (candidates.length === 0) {
            listEl.innerHTML = '<div class="scan-result-empty">未扫描到可加入的新方法，可尝试放宽方法名或调整扫描规则</div>';
        } else {
            candidates.forEach((item) => {
                const sig = readableFullSig(item.className, item.methodName, item.descriptor) || entryKey(item);
                const key = entryKey(item);
                const row = document.createElement('div');
                row.className = 'scan-result-item';
                row.innerHTML =
                    '<input type="checkbox" class="scan-item-cb" data-key="' + key.replace(/"/g, '&quot;') + '" checked>'
                    + '<span class="scan-item-sig">' + sigHtmlFromString(sig) + '</span>'
                    + (item.group ? '<span class="scan-item-group">' + escapeHtml(item.group) + '</span>' : '');
                listEl.appendChild(row);
            });
        }
        updateScanStats();
    }
    // 统计条 + 确认按钮勾选数
    function updateScanStats() {
        const all = els.addEntryScanList.querySelectorAll('.scan-item-cb');
        const checked = els.addEntryScanList.querySelectorAll('.scan-item-cb:checked').length;
        els.addEntryScanAll.checked = all.length > 0 && all.length === checked;
        els.addEntryConfirm.textContent = '确定加入（' + checked + '）';
        els.addEntryConfirm.disabled = checked === 0;
        let statsText = '扫描到 ' + all.length + ' 个可加入的方法，勾选 ' + checked + ' 个';
        if (currentScanExisted > 0) statsText += '；' + currentScanExisted + ' 个已在清单中（不会重复加入）';
        els.addEntryScanStats.textContent = statsText;
    }
    // 手动扫描：读取输入（类名 + 可选方法名）
    function readAddEntryInput() {
        let className = '', methodName = '';
        const pasteRaw = els.addEntryPaste.value.trim();
        if (pasteRaw) {
            const p = parseEntryString(pasteRaw);
            className = p.className;
            methodName = p.methodName || '';
        } else {
            className = els.addEntryClass.value.trim();
            methodName = els.addEntryMethodText.value.trim() || els.addEntryMethod.value.trim();
        }
        return { className, methodName };
    }

    els.btnEntryAdd.addEventListener('click', openAddEntryModal);
    els.addEntryClose.addEventListener('click', closeAddEntryModal);
    els.addEntryCancel.addEventListener('click', closeAddEntryModal);
    els.addEntryOverlay.addEventListener('click', closeAddEntryModal);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.addEntryModal.hidden) closeAddEntryModal();
    });

    // 类名输入 → 自动补全 + 加载该类的方法列表
    let addEntrySearchTimer = null;
    els.addEntryClass.addEventListener('input', () => {
        clearTimeout(addEntrySearchTimer);
        addEntrySearchTimer = setTimeout(async () => {
            const q = els.addEntryClass.value.trim();
            const path = currentProjectPath();
            if (!path || !q) { els.addEntryClassList.innerHTML = ''; return; }
            try {
                const resp = await fetch('/api/classes/search?path=' + encodeURIComponent(path)
                    + '&q=' + encodeURIComponent(q));
                if (!resp.ok) return;
                const list = await resp.json();
                els.addEntryClassList.innerHTML = list
                    .slice(0, 50)
                    .map(c => `<option value="${c}"></option>`).join('');
                // 如果唯一匹配 → 加载方法列表
                if (list.length === 1 || (q.includes('.') && list.some(c => c === q))) {
                    await loadMethodsForClass(q.includes('.') ? q : list[0]);
                }
            } catch (e) { /* 忽略补全失败 */ }
        }, 300);
    });
    els.addEntryClass.addEventListener('change', () => {
        const v = els.addEntryClass.value.trim();
        if (v) loadMethodsForClass(v);
    });

    async function loadMethodsForClass(fullClassName) {
        const path = currentProjectPath();
        if (!path) return;
        try {
            const resp = await fetch('/api/classes/methods?path=' + encodeURIComponent(path)
                + '&class=' + encodeURIComponent(fullClassName));
            if (!resp.ok) return;
            const methods = await resp.json();
            if (!Array.isArray(methods) || methods.length === 0) return;
            els.addEntryMethod.innerHTML =
                '<option value="">留空（整个类所有方法都作为入口）</option>'
                + methods.map(m => `<option value="${m.name}" data-desc="${m.descriptor || ''}">${m.name}()</option>`).join('');
        } catch (e) { /* 忽略 */ }
    }

    // 快捷粘贴 → 自动解析填充
    els.addEntryPaste.addEventListener('input', () => {
        const raw = els.addEntryPaste.value.trim();
        if (!raw) return;
        const parsed = parseEntryString(raw);
        if (parsed) {
            els.addEntryClass.value = parsed.className;
            if (parsed.methodName) {
                els.addEntryMethodText.value = parsed.methodName;
                els.addEntryMethod.value = '';
            } else {
                els.addEntryMethodText.value = '';
            }
        }
    });

    /** 解析用户输入的入口串（逻辑统一在 Sig，见 sig.js；支持整类/方法/可读重载/旧 JVM 描述符） */
    function parseEntryString(raw) {
        return Sig.parseEntryString(raw);
    }

    // 🔎 扫描按钮：调用 scan-manual，渲染扫描结果列表
    els.addEntryVerify.addEventListener('click', async () => {
        if (!App.state.currentProjectId) { showError('请先进入项目'); return; }
        const input = readAddEntryInput();
        if (!input.className) { setVerifyStatus('请先填类名', 'err'); return; }

        els.addEntryVerify.disabled = true;
        els.addEntryScanWrap.hidden = false;
        els.addEntryScanList.innerHTML = '<div class="scan-result-empty">扫描中...</div>';
        els.addEntryScanStats.textContent = '扫描中...';
        setVerifyStatus('扫描中...', '');
        currentScanCandidates = [];
        try {
            const resp = await postJson('/api/projects/' + App.state.currentProjectId + '/entries/scan-manual', {
                className: input.className,
                methodName: input.methodName || '',
                profileId: (scanStrategy ? scanStrategy.activeProfileId : undefined)
            });
            const candidates = resp.candidates || [];
            const existed = resp.existed || 0;
            currentScanCandidates = candidates;
            renderScanResults(candidates, existed);
            setVerifyStatus('扫描完成', candidates.length > 0 ? 'ok' : 'warn');
            addEntryVerified = true;
        } catch (e) {
            els.addEntryScanWrap.hidden = true;
            setVerifyStatus('扫描失败: ' + e.message, 'err');
            addEntryVerified = false;
        } finally {
            els.addEntryVerify.disabled = false;
        }
    });

    // 全选 / 取消全选
    els.addEntryScanAll.addEventListener('change', () => {
        const checked = els.addEntryScanAll.checked;
        els.addEntryScanList.querySelectorAll('.scan-item-cb').forEach(cb => cb.checked = checked);
        updateScanStats();
    });

    // 扫描结果列表内 checkbox 变更 → 更新统计 + 确认按钮
    els.addEntryScanList.addEventListener('change', (e) => {
        if (e.target.classList.contains('scan-item-cb')) {
            updateScanStats();
        }
    });

    // 输入变动 → 重置扫描状态
    els.addEntryClass.addEventListener('input', resetVerify);
    els.addEntryMethodText.addEventListener('input', resetVerify);
    els.addEntryMethod.addEventListener('change', resetVerify);
    els.addEntryPaste.addEventListener('input', resetVerify);

    // 确定加入：收集勾选的方法 → 批量提交
    els.addEntryConfirm.addEventListener('click', async () => {
        if (!App.state.currentProjectId) { showError('请先进入项目'); closeAddEntryModal(); return; }
        const checkedItems = [];
        els.addEntryScanList.querySelectorAll('.scan-item-cb:checked').forEach(cb => {
            const key = cb.dataset.key;
            const item = currentScanCandidates.find(c => entryKey(c) === key);
            if (item) checkedItems.push(item);
        });
        if (checkedItems.length === 0) {
            showError('请至少勾选一个方法');
            return;
        }
        try {
            const resp = await postJson('/api/projects/' + App.state.currentProjectId + '/entries/add-batch', checkedItems);
            closeAddEntryModal();
            await autoLoadEntryList(App.state.currentProjectId);
            let msg = '✓ ' + resp.added + ' 个添加成功';
            if (resp.existed > 0) msg += '，' + resp.existed + ' 个已存在（自动跳过）';
            showError(msg, true);
        } catch (e) {
            showError('添加失败: ' + e.message);
        }
    });

    // 排除 / 恢复：收进 IIFE 内的局部对象持有，不再挂到 window（OPT-32）。
    // 调用点由「生成 HTML 时的内联 onclick」改为下方事件委托，消除对全局函数的依赖。
    const entryActions = {
        /** 排除（confirmed → excluded）：单条入口也走统一弹窗，便于选原因 */
        exclude(key) {
            if (!App.state.currentProjectId) return;
            const items = ((App.state.currentEntryList && App.state.currentEntryList.confirmed) || [])
                .filter(it => entryKey(it) === key);
            if (items.length === 0) return;
            openExcludeModal(items);
        },
        /** 恢复（excluded → confirmed） */
        restore(key) {
            if (!App.state.currentProjectId) return;
            postJson('/api/projects/' + App.state.currentProjectId + '/entries/restore', { key: key })
                .then(() => autoLoadEntryList(App.state.currentProjectId))
                .catch(e => showError('恢复失败: ' + e.message));
        },
    };

    /** 入口行内「排除 / 恢复」按钮：事件委托（原来依赖 window.* 内联 onclick，OPT-32） */
    els.entryConfirmedList.addEventListener('click', (e) => {
        const btn = e.target.closest('.entry-exclude-btn');
        if (!btn) return;
        const row = btn.closest('.entry-row');
        if (row && row.dataset.key) entryActions.exclude(row.dataset.key);
    });
    els.entryExcludedList.addEventListener('click', (e) => {
        const btn = e.target.closest('.entry-restore-btn');
        if (!btn) return;
        const row = btn.closest('.entry-row');
        if (row && row.dataset.key) entryActions.restore(row.dataset.key);
    });

    // ==================================================================
    // 批量排除：清单勾选 + 统一/逐个原因弹窗
    // ==================================================================
    const EXCLUDE_REASON_PRESETS = ['非业务入口', '已废弃 / 不再使用', '测试 / 演示代码', '重复入口'];

    /** 清单勾选变更（事件委托） */
    els.entryConfirmedList.addEventListener('change', (e) => {
        if (!e.target.classList.contains('entry-cb')) return;
        const row = e.target.closest('.entry-row');
        if (!row) return;
        const key = row.dataset.key;
        if (e.target.checked) App.state.entrySelKeys.add(key); else App.state.entrySelKeys.delete(key);
        row.classList.toggle('selected', e.target.checked);
        updateEntryToolbar();
    });

    /** 全选 / 全不选 */
    els.entryCheckAll.addEventListener('change', () => {
        const confirmed = (App.state.currentEntryList && App.state.currentEntryList.confirmed) || [];
        const checked = els.entryCheckAll.checked;
        confirmed.forEach(it => {
            if (checked) App.state.entrySelKeys.add(entryKey(it)); else App.state.entrySelKeys.delete(entryKey(it));
        });
        els.entryConfirmedList.querySelectorAll('.entry-row').forEach(row => {
            const cb = row.querySelector('.entry-cb');
            if (cb) { cb.checked = checked; row.classList.toggle('selected', checked); }
        });
        updateEntryToolbar();
    });

    /** 打开批量排除弹窗 */
    els.btnBatchExclude.addEventListener('click', () => {
        const confirmed = (App.state.currentEntryList && App.state.currentEntryList.confirmed) || [];
        const items = confirmed.filter(it => App.state.entrySelKeys.has(entryKey(it)));
        if (items.length === 0) return;
        openExcludeModal(items);
    });

    function openExcludeModal(items) {
        excludeModalItems = items.slice();
        els.excludeModalCount.textContent = items.length;
        els.excludeModalList.innerHTML = items.map((item) => {
            const sig = readableFullSig(item.className, item.methodName, item.descriptor) || entryKey(item);
            return `<div class="ex-item"><span class="ex-item-sig" title="${escapeHtml(sig)}">${sigHtmlFromString(sig)}</span></div>`;
        }).join('');
        els.excludeReasonPresets.innerHTML = EXCLUDE_REASON_PRESETS.map((r, i) =>
            `<button type="button" class="ex-preset${i === 0 ? ' active' : ''}" data-reason="${escapeHtml(r)}">${escapeHtml(r)}</button>`
        ).join('');
        els.excludeReasonText.value = EXCLUDE_REASON_PRESETS[0];
        els.excludeReasonUnified.checked = true;
        els.excludePerItemReasons.hidden = true;
        renderExcludePerItemReasons();
        els.excludeModalOverlay.hidden = false;
        els.excludeModal.hidden = false;
    }

    function closeExcludeModal() {
        els.excludeModalOverlay.hidden = true;
        els.excludeModal.hidden = true;
        excludeModalItems = [];
    }

    function renderExcludePerItemReasons() {
        els.excludePerItemReasons.innerHTML = excludeModalItems.map((item) => {
            const sig = readableFullSig(item.className, item.methodName, item.descriptor) || entryKey(item);
            return `<div class="ex-reason-item">
                <span class="ex-reason-sig" title="${escapeHtml(sig)}">${sigHtmlFromString(sig)}</span>
                <input type="text" placeholder="该入口的排除原因（留空默认「用户排除」）">
            </div>`;
        }).join('');
    }

    // 预设原因点选
    els.excludeReasonPresets.addEventListener('click', (e) => {
        const btn = e.target.closest('.ex-preset');
        if (!btn) return;
        els.excludeReasonPresets.querySelectorAll('.ex-preset')
            .forEach(b => b.classList.toggle('active', b === btn));
        els.excludeReasonText.value = btn.dataset.reason;
    });
    // 手动编辑原因 → 取消预设高亮
    els.excludeReasonText.addEventListener('input', () => {
        els.excludeReasonPresets.querySelectorAll('.ex-preset')
            .forEach(b => b.classList.remove('active'));
    });
    // 统一 / 逐个原因切换
    els.excludeReasonUnified.addEventListener('change', () => {
        els.excludePerItemReasons.hidden = els.excludeReasonUnified.checked;
    });

    els.excludeModalClose.addEventListener('click', closeExcludeModal);
    els.excludeModalCancel.addEventListener('click', closeExcludeModal);
    els.excludeModalOverlay.addEventListener('click', closeExcludeModal);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.excludeModal.hidden) closeExcludeModal();
    });

    // 确认排除（批量提交）
    els.excludeModalConfirm.addEventListener('click', async () => {
        if (!App.state.currentProjectId || excludeModalItems.length === 0) return;
        const unified = els.excludeReasonUnified.checked;
        const perItemInputs = els.excludePerItemReasons.querySelectorAll('.ex-reason-item input');
        const payload = excludeModalItems.map((item, idx) => {
            const raw = unified ? els.excludeReasonText.value : (perItemInputs[idx] ? perItemInputs[idx].value : '');
            return { key: entryKey(item), reason: (raw || '').trim() || '用户排除' };
        });
        els.excludeModalConfirm.disabled = true;
        try {
            const resp = await postJson('/api/projects/' + App.state.currentProjectId + '/entries/exclude/batch', payload);
            closeExcludeModal();
            App.state.entrySelKeys.clear();
            await autoLoadEntryList(App.state.currentProjectId);
            const n = resp && resp.excluded != null ? resp.excluded : payload.length;
            showError('✓ 已排除 ' + n + ' 个入口，可在「已排除的入口」中恢复', true);
        } catch (e) {
            showError('批量排除失败: ' + e.message);
        } finally {
            els.excludeModalConfirm.disabled = false;
        }
    });

    // ==================================================================
    // Step 3: 批量分析 —— 拿 confirmed 清单逐个 analyze
    // ==================================================================

    // 深度 preset 按钮交互
    document.querySelectorAll('.depth-preset').forEach(btn => {
        btn.addEventListener('click', () => {
            els.maxDepth.value = btn.dataset.depth;
            document.querySelectorAll('.depth-preset').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
        });
    });
    // input 手动改时取消 preset 高亮
    els.maxDepth.addEventListener('input', () => {
        document.querySelectorAll('.depth-preset').forEach(b => b.classList.remove('active'));
    });
    // 初始化：默认 20 高亮
    document.querySelector('.depth-preset[data-depth="20"]')?.classList.add('active');

    els.btnBatchAnalyze.addEventListener('click', async () => {
        if (!App.state.currentProjectId) return;
        const confirmed = (App.state.currentEntryList && App.state.currentEntryList.confirmed) || [];
        if (confirmed.length === 0) {
            showError('清单为空，请先在 Step 2 确认入口');
            return;
        }
        const depth = parseInt(els.maxDepth.value || '20', 10);

        els.batchProgress.hidden = false;
        els.batchProgressText.textContent = '准备中...';
        els.batchProgressBar.style.width = '0%';
        els.btnBatchAnalyze.disabled = true;
        clearError();

        // 按整个清单一次性做多入口分析（后端异步执行，前端轮询进度）
        const req = {
            projectPath: currentProjectPath(),
            maxDepth: depth,
            entries: confirmed.map((item) => ({
                className: item.className,
                methodName: item.methodName || '',
                methodDescriptor: item.descriptor || '',
            })),
        };

        try {
            const { jobId } = await postJson('/api/analyze/batch', req);

            // 轮询进度，实时更新进度条（状态轻量，不含结果）
            for (;;) {
                await new Promise((r) => setTimeout(r, 500));
                const st = await fetchJson('/api/analyze/batch/progress/' + encodeURIComponent(jobId));
                els.batchProgressBar.style.width = (st.progress || 0) + '%';
                els.batchProgressText.textContent = st.step || '分析中...';
                if (st.state === 'DONE') break;
                if (st.state === 'FAILED') { throw new Error(st.error || '分析失败'); }
            }

            // 后端已完成并落盘轻量索引，这里加载并渲染（批量清单视图）
            App.state.currentRequest = req;
            await autoLoadCacheForProject(App.state.currentProjectId);
        } catch (e) {
            els.batchProgressBar.style.width = '100%';
            els.batchProgressText.textContent = '✗ 分析失败';
            showError(e.message);
        } finally {
            els.btnBatchAnalyze.disabled = false;
        }
    });

    // ------------------------------------------------------------------
    // 新手引导：app.js 只提供只读状态 + 导航动作，渲染全在 guide.js
    // ------------------------------------------------------------------
    function guideState() {
        const confirmed = (App.state.currentEntryList && App.state.currentEntryList.confirmed) || [];
        return {
            view: App.state.currentView,
            projectCount: App.state.guideProjectCount,
            currentProjectId: App.state.currentProjectId,
            entryCount: confirmed.length,
            hasResult: App.state.guideHasResult,
            resultStale: App.state.guideResultStale,
            noiseConfigured: guideNoiseConfigured(),
        };
    }

    /** 引导条按钮：只把用户带到该去的地方，不代执行有代价的操作（分析/导入仍需本人确认） */
    function guideAction(action) {
        const scrollTo = (node) => {
            if (node && node.scrollIntoView) node.scrollIntoView({ block: 'center', behavior: 'smooth' });
        };
        switch (action) {
            case 'import':
                switchView('projects');
                scrollTo(document.getElementById('projectPath'));
                break;
            case 'openProject':
                switchView('projects');
                scrollTo(els.projectsList);
                break;
            case 'fixEntries':
                if (!App.state.currentProjectId) { switchView('projects'); break; }
                switchView('analyze');
                scrollTo(els.btnEntryScan);
                break;
            case 'analyze':
            case 'reanalyze':
                if (!App.state.currentProjectId) { switchView('projects'); break; }
                switchView('analyze');
                scrollTo(els.btnBatchAnalyze);
                break;
            case 'noiseRules':
                if (App.state.currentProjectId) openNoiseRulesPanel();
                else switchView('noiseRules');
                break;
        }
    }

    if (window.Guide) {
        Guide.init({
            getState: guideState,
            onAction: guideAction,
            onReplay: () => {
                switchView('projects');
                showToast('引导已重新打开（关掉顶部条或走完全流程后会自动隐藏）');
            },
        });
    }

    // 页脚版权年份
    const footerYearEl = document.getElementById('footerYear');
    if (footerYearEl) footerYearEl.textContent = String(new Date().getFullYear());

    // 页面初始化：切到项目列表视图 + 自动刷新
    switchView('projects');
    refreshProjectList();

})();
