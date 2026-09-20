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

    // 扫描策略与弹窗（OPT-27 C8）已抽出 js/strategy.js：保留 loadScanStrategy 薄委托
    // （Projects.init 注入点使用），引导状态/导航动作经 Strategy.guideState/guideAction
    // 供下方 Guide.init 引用；Strategy.init 注入未搬簇函数（guideNoiseConfigured，晚绑定）。
    const loadScanStrategy = Strategy.loadScanStrategy;
    Strategy.init({ guideNoiseConfigured: guideNoiseConfigured });

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
    // C8 弹窗状态（excludeModalItems / scanStrategy / ssEditingProfileId / ssEditingRuleId /
    // dsContext / globalScanStrategy / ssPageEditingProfileId）与添加入口弹窗状态已迁入
    // js/strategy.js（OPT-27 C8），为零外部引用的模块私有变量，见该文件顶注释。
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
    // 扫描策略配置 / 添加入口弹窗 / 批量排除（OPT-27 C8）已抽出 js/strategy.js
    // ==============================================================

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

    // 引导状态/导航动作（guideState/guideAction）已随 C8 抽出 js/strategy.js（OPT-27 C8）
    if (window.Guide) {
        Guide.init({
            getState: Strategy.guideState,
            onAction: Strategy.guideAction,
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
