/* Java工程分析工具 - 前端逻辑（原生 JS，无外部依赖） */
(function () {
    'use strict';

    const $ = (sel) => document.querySelector(sel);

    const els = {
        // ---- 视图切换 + 项目列表 ----
        navToProjects: $('#navToProjects'),
        navToAnalyze: $('#navToAnalyze'),
        viewProjects: $('#viewProjects'),
        viewAnalyze: $('#viewAnalyze'),
        btnRefreshProjects: $('#btnRefreshProjects'),
        btnImportLocal: $('#btnImportLocal'),
        projectsList: $('#projectsList'),
        projectsEmpty: $('#projectsEmpty'),
        projectsCount: $('#projectsCount'),
        projectsImportError: $('#projectsImportError'),
        currentProjectBadge: $('#currentProjectBadge'),
        currentProjectName: $('#currentProjectName'),
        currentProjectPath: $('#currentProjectPath'),
        btnBackToProjects: $('#btnBackToProjects'),

        // ---- Step 3 状态提示 ----
        step3HintBar: $('#step3HintBar'),
        step3HintIcon: $('#step3HintIcon'),
        step3HintText: $('#step3HintText'),

        // ---- Step 2 入口清单 ----
        entryListStats: $('#entryListStats'),
        btnEntryScan: $('#btnEntryScan'),
        btnEntryAdd: $('#btnEntryAdd'),
        entryConfirmedList: $('#entryConfirmedList'),
        entryConfirmToolbar: $('#entryConfirmToolbar'),
        entryCheckAll: $('#entryCheckAll'),
        btnBatchExclude: $('#btnBatchExclude'),
        batchExcludeCount: $('#batchExcludeCount'),
        entrySelectedCount: $('#entrySelectedCount'),
        excludeModalOverlay: $('#excludeModalOverlay'),
        excludeModal: $('#excludeModal'),
        excludeModalClose: $('#excludeModalClose'),
        excludeModalCount: $('#excludeModalCount'),
        excludeModalList: $('#excludeModalList'),
        excludeModalCancel: $('#excludeModalCancel'),
        excludeModalConfirm: $('#excludeModalConfirm'),
        excludeReasonPresets: $('#excludeReasonPresets'),
        excludeReasonText: $('#excludeReasonText'),
        excludeReasonUnified: $('#excludeReasonUnified'),
        excludePerItemReasons: $('#excludePerItemReasons'),
        entryExcludedDetails: $('#entryExcludedDetails'),
        entryExcludedCount: $('#entryExcludedCount'),
        entryExcludedList: $('#entryExcludedList'),
        batchAnalyzeStats: $('#batchAnalyzeStats'),
        btnBatchAnalyze: $('#btnBatchAnalyze'),
        batchProgress: $('#batchProgress'),
        batchProgressBar: $('#batchProgressBar'),
        batchProgressText: $('#batchProgressText'),

        // ---- 项目创建/导入 ----
        projectPath: $('#projectPath'),
        btnInfo: $('#btnInfo'),
        projectInfo: $('#projectInfo'),
        tabLocal: $('#tabLocal'),
        tabGit: $('#tabGit'),
        paneLocal: $('#paneLocal'),
        paneGit: $('#paneGit'),
        repoUrl: $('#repoUrl'),
        gitBranch: $('#gitBranch'),
        gitToken: $('#gitToken'),
        gitUsername: $('#gitUsername'),
        btnGitPrepare: $('#btnGitPrepare'),
        gitStatus: $('#gitStatus'),

        // ---- Git 分支/Tag 切换 + 远端更新检测 ----
        gitInfoBar: $('#gitInfoBar'),
        gitCurrentRef: $('#gitCurrentRef'),
        gitCurrentRefType: $('#gitCurrentRefType'),
        gitRefSelect: $('#gitRefSelect'),
        btnGitSwitch: $('#btnGitSwitch'),
        btnGitCheckUpdate: $('#btnGitCheckUpdate'),
        gitRemoteStatus: $('#gitRemoteStatus'),
        gitLastCheck: $('#gitLastCheck'),
        gitSwitchProgress: $('#gitSwitchProgress'),
        gitSwitchProgressBar: $('#gitSwitchProgressBar'),
        gitSwitchProgressText: $('#gitSwitchProgressText'),
        gitSwitchLog: $('#gitSwitchLog'),
        gitPrepareLog: $('#gitPrepareLog'),

        maxDepth: $('#maxDepth'),
        btnExcel: $('#btnExcel'),
        errorBanner: $('#errorBanner'),
        toast: $('#toast'),
        entrySection: $('#entrySection'),
        entryProjectName: $('#entryProjectName'),
        entryFilter: $('#entryFilter'),
        btnEntryAll: $('#btnEntryAll'),
        btnEntryNone: $('#btnEntryNone'),
        entryGroups: $('#entryGroups'),
        entryCount: $('#entryCount'),
        btnAnalyzeEntries: $('#btnAnalyzeEntries'),
        resultSection: $('#resultSection'),
        resultTitle: $('#resultTitle'),
        statsBar: $('#statsBar'),
        warnings: $('#warnings'),
        tree: $('#tree'),
        btnExpandAll: $('#btnExpandAll'),
        btnCollapseAll: $('#btnCollapseAll'),
        btnResultRefresh: $('#btnResultRefresh'),
        freqSection: $('#freqSection'),
        freqStatsBar: $('#freqStatsBar'),
        freqFilterBar: $('#freqFilterBar'),
        freqList: $('#freqList'),
        btnFreqExpandAll: $('#btnFreqExpandAll'),
        btnFreqCollapseAll: $('#btnFreqCollapseAll'),
        btnFreqRefresh: $('#btnFreqRefresh'),
        btnNoiseRulesProject: $('#btnNoiseRulesProject'),
        noiseRulesOverlay: $('#noiseRulesOverlay'),
        noiseRulesPanel: $('#noiseRulesPanel'),
        noiseRulesList: $('#noiseRulesList'),
        btnNoiseRulesClose: $('#btnNoiseRulesClose'),
        btnNoiseRuleAdd: $('#btnNoiseRuleAdd'),
        btnNoiseRuleReset: $('#btnNoiseRuleReset'),
        btnNoiseRuleSave: $('#btnNoiseRuleSave'),
        btnNoiseRuleSelectAll: $('#btnNoiseRuleSelectAll'),
        btnNoiseRuleSelectNone: $('#btnNoiseRuleSelectNone'),
        btnNoiseRuleInvert: $('#btnNoiseRuleInvert'),
        btnNoiseRuleExport: $('#btnNoiseRuleExport'),
        btnNoiseRuleImport: $('#btnNoiseRuleImport'),
        noiseRuleImportFile: $('#noiseRuleImportFile'),

        // ---- 全局过滤规则独立页面 ----
        navToNoiseRules: $('#navToNoiseRules'),
        viewNoiseRules: $('#viewNoiseRules'),
        noiseRulesListPage: $('#noiseRulesListPage'),
        btnNrPageAdd: $('#btnNrPageAdd'),
        btnNrPageReset: $('#btnNrPageReset'),
        btnNrPageSave: $('#btnNrPageSave'),
        btnNrPageBack: $('#btnNrPageBack'),
        btnNrPageSelectAll: $('#btnNrPageSelectAll'),
        btnNrPageSelectNone: $('#btnNrPageSelectNone'),
        btnNrPageInvert: $('#btnNrPageInvert'),
        btnNrPageExport: $('#btnNrPageExport'),
        btnNrPageImport: $('#btnNrPageImport'),
        noiseRulePageImportFile: $('#noiseRulePageImportFile'),

        // ---- 手动添加入口 Modal ----
        addEntryOverlay: $('#addEntryOverlay'),
        addEntryModal: $('#addEntryModal'),
        addEntryClose: $('#addEntryClose'),
        addEntryCancel: $('#addEntryCancel'),
        addEntryConfirm: $('#addEntryConfirm'),
        addEntryScanWrap: $('#addEntryScanWrap'),
        addEntryScanStats: $('#addEntryScanStats'),
        addEntryScanAll: $('#addEntryScanAll'),
        addEntryScanList: $('#addEntryScanList'),
        addEntryModalTitle: $('#addEntryModalTitle'),
        addEntryInputArea: $('#addEntryInputArea'),

        confirmOverlay: $('#confirmOverlay'),
        confirmModal: $('#confirmModal'),
        confirmTitle: $('#confirmTitle'),
        confirmMessage: $('#confirmMessage'),
        confirmOk: $('#confirmOk'),
        confirmCancel: $('#confirmCancel'),
        confirmClose: $('#confirmClose'),

        loadingOverlay: $('#loadingOverlay'),
        loadingOverlayText: $('#loadingOverlayText'),
        loadingSteps: $('#loadingSteps'),
        loadingBarWrap: $('#loadingBarWrap'),
        loadingBar: $('#loadingBar'),
        loadingElapsed: $('#loadingElapsed'),
        addEntryVerify: $('#addEntryVerify'),
        addEntryVerifyStatus: $('#addEntryVerifyStatus'),
        addEntryClass: $('#addEntryClass'),
        addEntryClassList: $('#addEntryClassList'),
        addEntryMethod: $('#addEntryMethod'),
        addEntryMethodText: $('#addEntryMethodText'),
        addEntryPaste: $('#addEntryPaste'),

        globalSearchInput: $('#globalSearchInput'),
        globalSearchMode: $('#globalSearchMode'),
        btnGlobalSearch: $('#btnGlobalSearch'),
        btnGlobalSearchClear: $('#btnGlobalSearchClear'),
        globalSearchResult: $('#globalSearchResult'),
        globalSearchChips: $('#globalSearchChips'),
        globalSearch: document.querySelector('.global-search'),
        legend: document.querySelector('.legend'),
        btnBackToList: $('#btnBackToList'),
        projectSearch: $('#projectSearch'),
        projectLoadState: $('#projectLoadState'),
        projectSearchInput: $('#projectSearchInput'),
        projectSearchMode: $('#projectSearchMode'),
        btnProjectSearch: $('#btnProjectSearch'),
        btnProjectSearchClear: $('#btnProjectSearchClear'),
        projectSearchResult: $('#projectSearchResult'),
        projectSearchNextHit: $('#btnProjectSearchNext'),
        projectSearchPrevHit: $('#btnProjectSearchPrev'),

        // ---- 扫描策略配置 ----
        scanProfileSelect: $('#scanProfileSelect'),
        btnScanStrategyManage: $('#btnScanStrategyManage'),
        scanStrategyOverlay: $('#scanStrategyOverlay'),
        scanStrategyPanel: $('#scanStrategyPanel'),
        ssProfileList: $('#ssProfileList'),
        btnSsProfileNew: $('#btnSsProfileNew'),
        btnSsProfileCopy: $('#btnSsProfileCopy'),
        btnSsProfileDelete: $('#btnSsProfileDelete'),
        btnSsExport: $('#btnSsExport'),
        btnSsImport: $('#btnSsImport'),
        ssImportFile: $('#ssImportFile'),
        ssEditorEmpty: $('#ssEditorEmpty'),
        ssEditorBody: $('#ssEditorBody'),
        ssProfileName: $('#ssProfileName'),
        ssProfileDesc: $('#ssProfileDesc'),
        ssBuiltinTag: $('#ssBuiltinTag'),
        ssDetectors: $('#ssDetectors'),
        btnSsRuleAdd: $('#btnSsRuleAdd'),
        ssRuleList: $('#ssRuleList'),
        btnSsSave: $('#btnSsSave'),
        btnSsReset: $('#btnSsReset'),
        btnSsClose: $('#btnSsClose'),
        ssRuleEditorOverlay: $('#ssRuleEditorOverlay'),
        ssRuleEditor: $('#ssRuleEditor'),
        ssRuleEditorTitle: $('#ssRuleEditorTitle'),
        ssRuleEditorClose: $('#ssRuleEditorClose'),
        ssRuleName: $('#ssRuleName'),
        ssRuleKind: $('#ssRuleKind'),
        ssRuleDynamic: $('#ssRuleDynamic'),
        ssRuleExcludes: $('#ssRuleExcludes'),
        ssRuleEnabled: $('#ssRuleEnabled'),
        ssRuleEditorCancel: $('#ssRuleEditorCancel'),
        ssRuleEditorOk: $('#ssRuleEditorOk'),

        // ---- 系统配置页面（过滤规则 / 扫描策略 tab） ----
        scTabNoise: $('#scTabNoise'),
        scTabScan: $('#scTabScan'),
        scTabContentNoise: $('#scTabContentNoise'),
        scTabContentScan: $('#scTabContentScan'),
        // 页面版扫描策略元素
        ssPageProfileList: $('#ssPageProfileList'),
        btnSsPageProfileNew: $('#btnSsPageProfileNew'),
        btnSsPageProfileCopy: $('#btnSsPageProfileCopy'),
        btnSsPageProfileDelete: $('#btnSsPageProfileDelete'),
        btnSsPageExport: $('#btnSsPageExport'),
        btnSsPageImport: $('#btnSsPageImport'),
        ssPageImportFile: $('#ssPageImportFile'),
        ssPageEditorEmpty: $('#ssPageEditorEmpty'),
        ssPageEditorBody: $('#ssPageEditorBody'),
        ssPageProfileName: $('#ssPageProfileName'),
        ssPageProfileDesc: $('#ssPageProfileDesc'),
        ssPageBuiltinTag: $('#ssPageBuiltinTag'),
        ssPageDetectors: $('#ssPageDetectors'),
        btnSsPageRuleAdd: $('#btnSsPageRuleAdd'),
        ssPageRuleList: $('#ssPageRuleList'),
        btnSsPageSave: $('#btnSsPageSave'),
        btnSsPageReset: $('#btnSsPageReset'),
    };

    let currentResult = null;
    let currentRequest = null;  // 最近一次成功分析的请求（Excel 复用）
    let currentBatchSummary = null; // 批量分析轻量索引（清单视图）
    let currentCacheFileName = null; // 批量中当前展开入口的缓存文件名（Excel 用）
    let currentExcelMode = 'entry';  // 'entry' 单入口导出 | 'project' 项目级导出
    let batchRowStates = [];         // 批量清单每行的行内展开状态 { open,rendered,result,roots,body,rowEl,entry }
    // Step 2 入口清单状态
    let currentEntryList = null;   // EntryList DTO（confirmed + excluded）
    let currentCandidates = [];    // 本次扫描新增的候选（临时）

    // ---- 新手引导只读状态（guide.js 消费，不参与业务逻辑） ----
    let currentView = 'projects';   // 当前视图：projects | analyze | noiseRules
    let guideProjectCount = null;   // 已导入项目数（null = 尚未拉到，引导先不渲染，避免闪烁）
    let guideHasResult = false;     // 是否已有可用分析结果
    let guideResultStale = false;   // 结果是否已因清单变化而过期

    /** 通知引导刷新（引导未加载时静默跳过） */
    function guideRefresh() {
        if (window.Guide) Guide.refresh();
    }

    /** 引导用：用户是否配置过过滤规则 */
    function guideNoiseConfigured() {
        if (window.Guide && Guide.isSeen(Guide.NOISE_CONFIGURED)) return true;
        if (projectRulesCache && projectRulesCache.length > 0) return true;
        return !!(globalOverrides && Object.keys(globalOverrides).length > 0);
    }
    let entrySelKeys = new Set();  // 清单中勾选待排除的入口 key 集合
    let excludeModalItems = [];    // 批量排除弹窗当前承载的条目
    let expandFns = [];      // 全部展开/收起用
    let searchTimer = null;
    const nodeRegistry = new Map();  // 数据节点 → { rowEl, setExpanded }
    let hitRows = [];                // 当前搜索高亮的行
    let activeSearch = null;         // 当前打开的行内搜索栏 { bar, node }
    let freqFilter = 'ALL';          // 方法调用次数分析的来源筛选：ALL/PROJECT/DEPENDENCY/EXTERNAL
    let freqViewMode = 'entry';      // 频率区当前显示的数据视图：'entry' 单入口 | 'project' 项目级聚合
    let batchModel = null;           // 批量全量加载模型：{batch, projectId, entries, done,total,failed, loaded, index, projectFreq}
    let projSearchMarks = null;      // 项目级搜索标记：Map<入口idx, {hit:Set<gid>, hasHit:Set<gid>}>
    let projSearchOrder = [];        // 命中扁平序列 [{entryIdx, gid}]，供「下一个命中」循环定位
    let projSearchCursor = -1;       // 当前定位到第几个命中（-1 = 未开始）
    let noiseRules = [];             // 过滤规则编辑缓冲区（弹窗/页面正在展示的那一层）
    let globalRulesCache = [];       // 全局层规则缓存（参与合并过滤）
    let projectRulesCache = [];      // 项目层自定义规则缓存（参与合并过滤）
    let globalOverrides = {};        // 项目级全局规则覆盖：{ ruleId: true/false }
    let activeNoiseRules = [];       // 实际生效的过滤规则集 = 全局层（套覆盖）+ 项目自定义层
    let compiledActiveRules = [];    // 启用中规则的预编译结果（正则只编译一次，判定只做 test）
    let activeNoiseHash = '';        // 生效规则集指纹（规则变更时算一次，后续直接比对）
    let noiseRuleScope = 'global';   // 当前查看/编辑的层级：'global' | 'project'
    let noiseRuleMode = 'panel';     // 当前编辑模式：'panel'（弹窗）| 'page'（独立页面）
    let currentProjectId = null;     // 当前选中的项目 id（null = 未选中）
    let gitSwitchTimer = null;       // 分支/Tag 切换任务轮询定时器
    let gitRefsCache = null;         // 最近一次分支/Tag 下拉数据 {branches, tags, defaultBranch}
    // 扫描策略状态
    let scanStrategy = null;         // 当前项目的有效扫描策略（ScanStrategy DTO）
    let ssEditingProfileId = null;   // 管理弹窗中正在编辑的方案 id
    let ssEditingRuleId = null;      // 二级规则编辑弹窗中正在编辑的规则 id（null = 新增）
    let dsContext = 'modal';         // 扫描策略编辑上下文：'modal'（项目级弹窗）| 'page'（全局页面）
    let globalScanStrategy = null;   // 全局扫描策略（页面版单独维护）
    let ssPageEditingProfileId = null; // 页面版正在编辑的方案 id

    // ==============================================================
    // 项目列表 / 视图切换 / 持久化项目管理
    // ==============================================================

    function switchView(to) {
        const showProjects = to === 'projects';
        const showAnalyze = to === 'analyze';
        const showNoiseRules = to === 'noiseRules';
        if (!showNoiseRules) noiseRuleMode = 'panel';
        els.viewProjects.hidden = !showProjects;
        els.viewAnalyze.hidden = !showAnalyze;
        els.viewNoiseRules.hidden = !showNoiseRules;
        els.navToProjects.classList.toggle('active', showProjects);
        els.navToAnalyze.classList.toggle('active', showAnalyze);
        els.navToNoiseRules.classList.toggle('active', showNoiseRules);
        currentView = to;
        guideRefresh();
    }

    /** 最近一次项目列表索引：进入项目时直接取用，避免重复的全量 /api/projects 请求 */
    let projectIndex = {};

    async function refreshProjectList() {
        try {
            const resp = await fetch('/api/projects');
            if (!resp.ok) {
                if (guideProjectCount == null) guideProjectCount = 0;
                guideRefresh();
                return;
            }
            const list = await resp.json();
            renderProjectList(list);
        } catch (e) {
            if (guideProjectCount == null) guideProjectCount = 0;
            guideRefresh();
        }
    }

    function renderProjectList(list) {
        list = list || [];
        projectIndex = {};
        list.forEach((p) => { projectIndex[p.id] = p; });
        guideProjectCount = list.length;
        els.projectsCount.textContent = list.length + ' 个';
        if (list.length === 0) {
            els.projectsEmpty.hidden = false;
            els.projectsList.innerHTML = '';
            guideRefresh();
            return;
        }
        els.projectsEmpty.hidden = true;
        els.projectsList.innerHTML = list.map((p) => projectCardHtml(p)).join('');
        els.projectsList.querySelectorAll('.pc-enter').forEach((btn) => {
            btn.addEventListener('click', () => enterProject(btn.dataset.id));
        });
        els.projectsList.querySelectorAll('.pc-delete').forEach((btn) => {
            btn.addEventListener('click', () => {
                const name = btn.dataset.name;
                if (!confirm('确定从列表删除项目"' + name + '"？\n（只移除记录，不删除磁盘文件）')) return;
                deleteProject(btn.dataset.id);
            });
        });
        guideRefresh();
    }

    function projectCardHtml(p) {
        const typeLabel = p.type === 'GIT' ? 'GIT' : 'LOCAL';
        const time = p.lastOpenedAt ? new Date(p.lastOpenedAt).toLocaleString()
                   : (p.createdAt ? new Date(p.createdAt).toLocaleString() : '');
        let extraInfo = '';
        if (p.type === 'GIT' && p.gitUrl) extraInfo = '<div class="pc-path">🔗 ' + escapeHtml(p.gitUrl) + '</div>';

        // 状态徽章
        let statusBadge = '';
        const cs = p.changeStatus || 'UP_TO_DATE';
        const hint = p.changeHint || '';
        const statusConfig = {
            UP_TO_DATE:     { cls: 'status-ok',    icon: '✓', label: '已就绪' },
            NEEDS_COMPILE:  { cls: 'status-warn',  icon: '⚠', label: '需编译' },
            NEEDS_ANALYZE:  { cls: 'status-info',  icon: '⚡', label: '待分析' },
            MISSING:        { cls: 'status-bad',   icon: '✗', label: '已丢失' },
            ERROR:          { cls: 'status-bad',   icon: '✗', label: '异常' }
        };
        const cfg = statusConfig[cs] || statusConfig.UP_TO_DATE;
        statusBadge = '<span class="pc-status ' + cfg.cls + '" title="' + escapeHtml(hint) + '">'
            + cfg.icon + ' ' + cfg.label + '</span>';

        const cardClass = cs === 'MISSING' ? 'project-card card-missing' : 'project-card';

        return '<div class="' + cardClass + '">'
            + '<div class="pc-head">'
            + '<span class="pc-type ' + typeLabel + '">' + typeLabel + '</span>'
            + statusBadge
            + '<span class="pc-name">' + escapeHtml(p.name || '(未命名)') + '</span>'
            + '</div>'
            + '<div class="pc-path">📁 ' + escapeHtml(p.projectPath || '') + '</div>'
            + extraInfo
            + (hint && cs !== 'UP_TO_DATE' ? '<div class="pc-hint">' + escapeHtml(hint) + '</div>' : '')
            + '<div class="pc-meta"><span>' + escapeHtml(time) + '</span>'
            + '<span class="hint">id: ' + escapeHtml(p.id ? p.id.slice(0, 8) : '') + '</span></div>'
            + '<div class="pc-actions">'
            + '<button type="button" class="btn small primary pc-enter" data-id="' + escapeHtml(p.id) + '">进入分析</button>'
            + '<button type="button" class="btn small pc-delete" data-id="' + escapeHtml(p.id) + '" data-name="' + escapeHtml(p.name || '') + '">移除</button>'
            + '</div>'
            + '</div>';
    }

    async function enterProject(id) {
        let p = projectIndex[id];
        if (!p) {
            // 索引未命中（页面直开 / 刚切换分支）：兜底拉一次列表
            try {
                const listResp = await fetch('/api/projects');
                const list = await listResp.json();
                list.forEach((x) => { projectIndex[x.id] = x; });
                p = projectIndex[id];
            } catch (e) { /* 忽略，下面统一报错 */ }
        }
        if (!p) { alert('项目不存在或已被删除'); refreshProjectList(); return; }

        const isGit = p.type === 'GIT';
        const steps = ['读取项目信息', '同步过滤规则与扫描策略'];
        if (isGit) steps.push('加载 Git 分支信息');
        steps.push('加载交易入口清单');
        const iGit = isGit ? 2 : -1;
        const iEntries = isGit ? 3 : 2;
        showLoading('正在进入项目…（工程较大时首次加载会慢一些）', { steps: steps });
        try {
            loadingSetStep(0, 'active', '打开项目');
            await postJson('/api/projects/' + encodeURIComponent(id) + '/open', {});
            currentProjectId = id;
            currentResult = null;
            currentRequest = null;
            currentBatchSummary = null;
            currentCacheFileName = null;
            if (isGit) {
                gitProjectPath = p.projectPath;
                sourceMode = 'git';
            } else {
                gitProjectPath = null;
                sourceMode = 'local';
                els.projectPath.value = p.projectPath || '';
            }
            setSourceMode(sourceMode);
            els.currentProjectBadge.textContent = p.type || 'LOCAL';
            els.currentProjectBadge.className = 'badge source-' + (isGit ? 'dependency' : 'project').toLowerCase();
            els.currentProjectName.textContent = p.name || '(未命名)';
            els.currentProjectPath.textContent = p.projectPath || '';

            // 项目切换后先同步两层过滤规则（项目路径变化，项目层规则需重新拉取），再加载清单
            loadingSetStep(1, 'active');
            await loadNoiseRules();
            await loadScanStrategy();

            if (isGit) {
                loadingSetStep(iGit, 'active', '读取远程分支');
                showGitInfoBar(p);
                await loadGitRefs(id);
                // 远端更新检查要打 git ls-remote（最长 15s），放后台跑，不阻塞进入
                checkGitRemoteStatus(id, true);
            } else {
                hideGitInfoBar();
            }

            els.entrySection.hidden = true;
            els.resultSection.hidden = true;
            els.freqSection.hidden = true;
            clearError();
            switchView('analyze');

            // Step2 已确认的交易入口清单（主体，进来即可见）
            // 其内部再触发 autoLoadCacheForProject（加载批量索引 / 单入口结果）
            loadingSetStep(iEntries, 'active', '读取入口清单');
            await autoLoadEntryList(id, (note) => loadingSetStep(iEntries, 'active', note));
            loadingSetStep(iEntries, 'done');
        } finally {
            hideLoading();
        }
    }

    /** 加载项目级已确认的交易入口清单（Step 2）。onNote 可选，用于回吐子阶段说明 */
    async function autoLoadEntryList(projectId, onNote) {
        try {
            if (onNote) onNote('读取入口清单');
            const resp = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/entries');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            currentEntryList = await resp.json();
            currentCandidates = [];
            renderEntryList();
            // Step2 清单变了 → 刷新 Step3 提示（让后端重新算 currentEntryCount + dirty）
            await autoLoadCacheForProject(projectId, onNote);
        } catch (e) {
            console.warn('[Step2] 入口清单加载失败:', e);
            currentEntryList = { confirmed: [], excluded: [] };
            renderEntryList();
        }
    }

    /** 加载单份缓存 + 更新 Step3 状态提示条 */
    async function autoLoadCacheForProject(projectId, onNote) {
        try {
            if (onNote) onNote('读取上次分析结果');
            const resp = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/cache/load-single');
            if (!resp.ok) return;
            const data = await resp.json();

            if (data.hasCache) {
                currentResult = data.result;
                renderResult(data.result);
            }

            // 更新 Step3 统一提示条
            updateStep3Hint({
                hasCache: data.hasCache,
                dirty: data.dirty,
                cachedEntryCount: data.cachedEntryCount,
                currentEntryCount: data.currentEntryCount,
            });
        } catch (e) {
            console.warn('[缓存] 自动加载失败:', e);
        }
    }

    /** Step3 统一状态提示：整合 清单变更 + Git 更新 + 暂无缓存 */
    function updateStep3Hint(cacheInfo) {
        // cacheInfo: { hasCache, dirty, cachedEntryCount, currentEntryCount }
        guideHasResult = !!cacheInfo.hasCache;
        guideResultStale = !!cacheInfo.dirty;
        // 额外检查：Git 是否有更新（由后端 changeStatus 字段决定）
        const hints = [];
        let icon = 'ℹ️';

        if (!cacheInfo.hasCache) {
            if ((cacheInfo.currentEntryCount || 0) === 0) {
                icon = '📝';
                hints.push('Step2 清单为空，请先补充交易入口');
            } else {
                icon = '▶️';
                hints.push(`清单已就绪（${cacheInfo.currentEntryCount} 个入口），点击下方按钮开始分析`);
            }
        } else {
            if (cacheInfo.dirty) {
                icon = '⚠️';
                hints.push(`清单已调整（上次 ${cacheInfo.cachedEntryCount} 个，当前 ${cacheInfo.currentEntryCount} 个），结果可能过期，建议重新分析`);
            } else {
                icon = '✅';
                hints.push(`已缓存分析结果（${cacheInfo.cachedEntryCount} 个入口，清单一致），如需最新可重新分析`);
            }
        }

        els.step3HintIcon.textContent = icon;
        els.step3HintText.textContent = hints.join(' · ');
        els.step3HintBar.hidden = false;
        guideRefresh();
    }

    async function deleteProject(id) {
        try {
            const resp = await fetch('/api/projects/' + encodeURIComponent(id), { method: 'DELETE' });
            if (!resp.ok) { const d = await resp.json(); throw new Error(d.error || '删除失败'); }
            if (currentProjectId === id) {
                currentProjectId = null;
                switchView('projects');
            }
            refreshProjectList();
        } catch (e) { alert(e.message); }
    }

    els.btnImportLocal.addEventListener('click', async () => {
        const path = els.projectPath.value.trim();
        if (!path) { els.projectsImportError.textContent = '请填写项目路径'; els.projectsImportError.hidden = false; return; }
        els.projectsImportError.hidden = true;
        showLoading('正在识别项目……');
        try {
            const p = await postJson('/api/projects/local', { path: path });
            await enterProject(p.id);
        } catch (e) {
            els.projectsImportError.textContent = e.message;
            els.projectsImportError.hidden = false;
        } finally { hideLoading(); }
    });

    els.btnRefreshProjects.addEventListener('click', refreshProjectList);
    els.btnBackToProjects.addEventListener('click', () => {
        currentProjectId = null;
        noiseRuleMode = 'panel';
        els.entrySection.hidden = true;
        els.resultSection.hidden = true;
        els.freqSection.hidden = true;
        switchView('projects');
    });
    els.navToProjects.addEventListener('click', () => { switchView('projects'); refreshProjectList(); });
    els.navToAnalyze.addEventListener('click', () => {
        if (!currentProjectId) { alert('请先在项目列表中选择一个项目'); switchView('projects'); return; }
        switchView('analyze');
    });
    els.navToNoiseRules.addEventListener('click', () => {
        noiseRuleMode = 'page';
        noiseRuleScope = 'global';
        loadNoiseRules();
        renderNoiseRulesList();
        // 重置到过滤规则 tab
        els.scTabNoise.classList.add('active');
        els.scTabScan.classList.remove('active');
        els.scTabContentNoise.hidden = false;
        els.scTabContentNoise.classList.add('active');
        els.scTabContentScan.hidden = true;
        els.scTabContentScan.classList.remove('active');
        switchView('noiseRules');
    });
    els.btnNrPageBack.addEventListener('click', () => {
        noiseRuleMode = 'panel';
        if (currentProjectId) {
            switchView('analyze');
        } else {
            switchView('projects');
        }
    });

    // 旧缓存批次切换 UI + 重新分析按钮 已移除（改为单份缓存 + Step3 状态提示）

    // ------------------------------------------------------------------
    // 项目来源切换：本地路径 / Git 仓库
    // ------------------------------------------------------------------

    let sourceMode = 'local';   // 'local' | 'git'
    let gitProjectPath = null;  // Git 拉取编译完成后的本地目录
    let gitPollTimer = null;

    function setSourceMode(mode) {
        sourceMode = mode;
        els.tabLocal.classList.toggle('active', mode === 'local');
        els.tabGit.classList.toggle('active', mode === 'git');
        els.paneLocal.hidden = mode !== 'local';
        els.paneGit.hidden = mode !== 'git';
    }

    els.tabLocal.addEventListener('click', () => setSourceMode('local'));
    els.tabGit.addEventListener('click', () => setSourceMode('git'));

    /** 当前生效的项目路径：Git 模式取拉取编译产物，本地模式取输入框 */
    function currentProjectPath() {
        if (sourceMode === 'git') return gitProjectPath;
        return els.projectPath.value.trim();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    function showError(message) {
        els.errorBanner.textContent = message;
        els.errorBanner.hidden = false;
    }
    function clearError() {
        els.errorBanner.hidden = true;
        els.errorBanner.textContent = '';
    }

    let toastTimer = null;
    /** 轻量提示：底部居中浮出，自动消失。type: success(默认)/error/warn */
    function showToast(message, type, duration) {
        els.toast.textContent = message;
        els.toast.classList.remove('hide', 'error', 'warn');
        if (type === 'error' || type === 'warn') els.toast.classList.add(type);
        els.toast.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            els.toast.classList.add('hide');
            setTimeout(() => { els.toast.hidden = true; }, 250);
        }, duration || 2200);
    }

    let confirmResolver = null;
    /** 自定义确认弹窗（替代原生 confirm）：await showConfirm(msg) 返回 true/false */
    function showConfirm(message, title) {
        if (confirmResolver) confirmResolver(false); // 上一个未决确认按取消处理
        els.confirmTitle.textContent = title || '确认操作';
        els.confirmMessage.textContent = message;
        els.confirmOverlay.hidden = false;
        els.confirmModal.hidden = false;
        return new Promise((resolve) => { confirmResolver = resolve; });
    }
    function settleConfirm(result) {
        els.confirmOverlay.hidden = true;
        els.confirmModal.hidden = true;
        if (confirmResolver) {
            const resolve = confirmResolver;
            confirmResolver = null;
            resolve(result);
        }
    }
    els.confirmOk.addEventListener('click', () => settleConfirm(true));
    els.confirmCancel.addEventListener('click', () => settleConfirm(false));
    els.confirmClose.addEventListener('click', () => settleConfirm(false));
    els.confirmOverlay.addEventListener('click', () => settleConfirm(false));
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && confirmResolver) settleConfirm(false);
    });

    // ------------------------------------------------------------------
    // 全屏加载遮罩：支持「单行文案」与「分步进度」两种模式
    //   showLoading('文案')                       单行文案
    //   showLoading('文案', { steps: ['a','b'] }) 分步清单 + 进度条 + 耗时
    //   loadingSetStep(i, 'active'|'done'|'error', '子状态说明')
    //   hideLoading()
    // ------------------------------------------------------------------
    let loadingTick = null;
    let loadingStartAt = 0;

    function resetLoadingPanel() {
        if (loadingTick) { clearInterval(loadingTick); loadingTick = null; }
        els.loadingSteps.hidden = true;
        els.loadingSteps.innerHTML = '';
        els.loadingBarWrap.hidden = true;
        els.loadingBar.style.width = '0%';
        els.loadingElapsed.hidden = true;
        els.loadingElapsed.textContent = '';
    }

    /** 展示遮罩；opts.steps 传步骤名数组时进入「分步进度」模式 */
    function showLoading(text, opts) {
        const o = opts || {};
        els.loadingOverlayText.textContent = text || '正在加载...';
        els.loadingOverlay.hidden = false;
        if (o.steps && o.steps.length) {
            resetLoadingPanel();
            els.loadingSteps.hidden = false;
            o.steps.forEach((name, i) => {
                const row = document.createElement('div');
                row.className = 'loading-step' + (i === 0 ? ' active' : '');
                row.innerHTML = '<span class="ls-dot"></span><span class="ls-name"></span><span class="ls-note"></span>';
                row.querySelector('.ls-name').textContent = (i + 1) + '. ' + name;
                els.loadingSteps.appendChild(row);
            });
            els.loadingBarWrap.hidden = false;
            loadingSetProgress(0);
        }
        if (!loadingTick) {
            loadingStartAt = Date.now();
            loadingTick = setInterval(() => {
                const sec = Math.floor((Date.now() - loadingStartAt) / 1000);
                if (sec < 3) return;
                els.loadingElapsed.hidden = false;
                els.loadingElapsed.textContent = sec >= 20
                    ? '已用时 ' + sec + ' 秒，工程较大或正在编译，请耐心等待…'
                    : '已用时 ' + sec + ' 秒';
            }, 1000);
        }
    }

    /** 标记第 idx 步：state 为 active/done/error；note 为该步的实时子状态 */
    function loadingSetStep(idx, state, note) {
        const rows = els.loadingSteps.querySelectorAll('.loading-step');
        const row = rows[idx];
        if (!row) return;
        rows.forEach((r, i) => {
            r.classList.remove('active');
            if (i < idx && !r.classList.contains('error')) r.classList.add('done');
        });
        if (state) row.classList.add(state);
        if (note !== undefined) row.querySelector('.ls-note').textContent = note || '';
        loadingSetProgress(Math.round((idx / rows.length) * 100));
    }

    function loadingSetProgress(percent) {
        if (els.loadingBarWrap.hidden) return;
        els.loadingBar.style.width = Math.max(0, Math.min(100, percent)) + '%';
    }

    function hideLoading() {
        resetLoadingPanel();
        els.loadingOverlay.hidden = true;
    }

    async function postJson(url, body) {
        const resp = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok) {
            throw new Error(data.error || ('请求失败: HTTP ' + resp.status));
        }
        return data;
    }

    async function fetchJson(url) {
        const resp = await fetch(url);
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok) {
            throw new Error(data.error || ('请求失败: HTTP ' + resp.status));
        }
        return data;
    }

    async function putJson(url, body) {
        const resp = await fetch(url, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok) {
            throw new Error(data.error || ('请求失败: HTTP ' + resp.status));
        }
        return data;
    }

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
    // Git 拉取 + 编译（异步任务轮询）
    // ------------------------------------------------------------------

    const GIT_STATUS_LABEL = {
        PENDING: '排队中',
        CLONING: '正在克隆仓库',
        COMPILING: '正在 mvn 编译',
        DONE: '完成',
        FAILED: '失败',
    };

    /**
     * 任务日志框：展示 mvn 编译等实时输出。
     * 只在内容变化时更新（避免每次轮询重置滚动位置）；原本停在底部时自动跟随最新一行。
     */
    function renderJobLog(el, lines) {
        if (!el) return;
        const arr = Array.isArray(lines) ? lines : [];
        if (arr.length === 0) {
            el.hidden = true;
            el.textContent = '';
            return;
        }
        const text = arr.join('\n');
        if (el.textContent === text) return;
        const followTail = el.hidden || (el.scrollHeight - el.scrollTop - el.clientHeight) < 24;
        el.textContent = text;
        el.hidden = false;
        if (followTail) el.scrollTop = el.scrollHeight;
    }

    function renderGitStatus(st) {
        els.gitStatus.hidden = false;
        els.gitStatus.className = 'git-status '
            + (st.status === 'DONE' ? 'ok' : st.status === 'FAILED' ? 'err' : 'busy');
        let html = '<div class="git-status-line"><b>' + (GIT_STATUS_LABEL[st.status] || st.status) + '</b>'
            + (st.projectName ? ' · ' + escapeHtml(st.projectName) : '');
        // 步骤文字 + 进度条
        if (st.status !== 'DONE' && st.status !== 'FAILED') {
            const pct = Math.max(0, Math.min(100, st.progress || 0));
            const stepText = st.step || st.message || '处理中...';
            html += '<div class="git-progress-wrap">'
                + '<div class="git-progress-bar" style="width:' + pct + '%"></div>'
                + '<span class="git-progress-text">' + escapeHtml(stepText) + ' · ' + pct + '%</span>'
                + '</div>';
        } else if (st.message) {
            html += '<span class="git-status-msg">' + escapeHtml(st.message) + '</span>';
        }
        html += '</div>';
        if (st.message && (st.status === 'DONE' || st.status === 'FAILED')) {
            html += '<div class="git-status-msg">' + escapeHtml(st.message) + '</div>';
        }
        if (st.status === 'DONE' && st.projectPath) {
            html += '<div class="git-status-msg">已就绪: <code>' + escapeHtml(st.projectPath) + '</code></div>';
        }
        els.gitStatus.innerHTML = html;
        // mvn 编译的实时输出（独立节点，避免每次轮询重建导致滚动位置丢失）
        renderJobLog(els.gitPrepareLog, st.compileLog);
    }

    function stopGitPoll() {
        if (gitPollTimer) { clearInterval(gitPollTimer); gitPollTimer = null; }
        els.btnGitPrepare.disabled = false;
    }

    async function pollGitStatus(jobId) {
        try {
            const resp = await fetch('/api/git/prepare/' + encodeURIComponent(jobId));
            const st = await resp.json();
            if (!resp.ok) {
                stopGitPoll();
                renderGitStatus({ status: 'FAILED', message: st.error || '轮询失败' });
                return;
            }
            renderGitStatus(st);
            if (st.status === 'DONE') {
                stopGitPoll();
                gitProjectPath = st.projectPath;
                // Git 项目已自动注册到项目列表，刷新显示让用户自己选择
                refreshProjectList();
                // 给个提示，告诉用户导入成功、可以从列表里点进去
                renderGitStatus({
                    status: 'DONE',
                    message: '✓ 项目已导入，请从项目列表中点击"进入分析"'
                });
            } else if (st.status === 'FAILED') {
                stopGitPoll();
            }
        } catch (e) {
            stopGitPoll();
            renderGitStatus({ status: 'FAILED', message: '网络异常: ' + e.message });
        }
    }

    els.btnGitPrepare.addEventListener('click', async () => {
        clearError();
        const repoUrl = els.repoUrl.value.trim();
        if (!repoUrl) { showError('请填写仓库地址'); return; }
        stopGitPoll();
        gitProjectPath = null;
        els.btnGitPrepare.disabled = true;
        renderGitStatus({ status: 'PENDING', message: '提交任务……', projectName: '' });
        try {
            const st = await postJson('/api/git/prepare', {
                repoUrl: repoUrl,
                branch: els.gitBranch.value.trim() || null,
                token: els.gitToken.value.trim() || null,
                username: els.gitUsername.value.trim() || null,
            });
            renderGitStatus(st);
            gitPollTimer = setInterval(() => pollGitStatus(st.jobId), 2000);
        } catch (e) {
            stopGitPoll();
            renderGitStatus({ status: 'FAILED', message: e.message });
        }
    });

    // ------------------------------------------------------------------
    // Git 分支/Tag 切换 + 远端更新检测
    // ------------------------------------------------------------------

    function formatCheckTime(ms) {
        if (!ms) return '';
        const d = new Date(ms);
        const pad = (n) => (n < 10 ? '0' + n : '' + n);
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
            + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    const GIT_REMOTE_STATUS_LABEL = {
        UP_TO_DATE: '✓ 已是最新',
        BEHIND: '▼ 远端有更新',
        UNKNOWN: '未知',
    };

    function showGitInfoBar(p) {
        els.gitInfoBar.hidden = false;
        els.gitCurrentRef.textContent = p.currentRef || '—';
        if (p.currentRefType) {
            els.gitCurrentRefType.hidden = false;
            els.gitCurrentRefType.textContent = p.currentRefType === 'TAG' ? 'TAG' : 'BRANCH';
        } else {
            els.gitCurrentRefType.hidden = true;
        }
        // 先用注册表里上次检查结果占位，随后被实时检查覆盖
        renderGitRemoteBadge(p.remoteUpdateStatus, null);
        els.gitLastCheck.textContent = p.lastCheckTime ? ('最近检查: ' + formatCheckTime(p.lastCheckTime)) : '';
        els.gitSwitchProgress.hidden = true;
        els.gitRefSelect.innerHTML = '';
        els.gitRefSelect.disabled = false;
        els.btnGitSwitch.disabled = true;
        els.btnGitCheckUpdate.disabled = false;
    }

    function hideGitInfoBar() {
        els.gitInfoBar.hidden = true;
        stopGitSwitchPoll();
    }

    function renderGitRemoteBadge(status, hint) {
        const badge = els.gitRemoteStatus;
        const key = (status || 'UNKNOWN').toUpperCase();
        const label = GIT_REMOTE_STATUS_LABEL[key] || status || '';
        badge.className = 'badge git-remote-status ' + key.toLowerCase();
        badge.textContent = label + (hint ? ' · ' + hint : '');
        badge.hidden = false;
    }

    async function loadGitRefs(projectId) {
        try {
            const refs = await fetchJson('/api/projects/' + encodeURIComponent(projectId) + '/git/refs');
            gitRefsCache = refs;
            const sel = els.gitRefSelect;
            sel.innerHTML = '';
            const branches = refs.branches || [];
            const tags = refs.tags || [];
            if (branches.length) {
                const og = document.createElement('optgroup');
                og.label = '分支';
                branches.forEach((b) => {
                    const o = document.createElement('option');
                    o.value = b;
                    o.dataset.refType = 'BRANCH';
                    o.textContent = b;
                    og.appendChild(o);
                });
                sel.appendChild(og);
            }
            if (tags.length) {
                const og = document.createElement('optgroup');
                og.label = 'Tag';
                tags.forEach((t) => {
                    const o = document.createElement('option');
                    o.value = t;
                    o.dataset.refType = 'TAG';
                    o.textContent = t;
                    og.appendChild(o);
                });
                sel.appendChild(og);
            }
            if (!branches.length && !tags.length) {
                const o = document.createElement('option');
                o.value = '';
                o.textContent = '（无可用分支/Tag）';
                sel.appendChild(o);
            }
            els.btnGitSwitch.disabled = sel.selectedOptions.length === 0;
        } catch (e) {
            els.btnGitSwitch.disabled = true;
            console.warn('[Git] 分支/Tag 列表加载失败:', e);
        }
    }

    async function checkGitRemoteStatus(projectId, silent) {
        if (!silent) {
            els.gitRemoteStatus.hidden = true;
            els.gitLastCheck.textContent = '检查中...';
        }
        try {
            const st = await fetchJson('/api/projects/' + encodeURIComponent(projectId) + '/git/remote-status');
            renderGitRemoteBadge(st.status, st.hint);
            els.gitLastCheck.textContent = st.checkedAt ? ('最近检查: ' + formatCheckTime(st.checkedAt)) : '';
        } catch (e) {
            renderGitRemoteBadge('UNKNOWN', null);
            els.gitLastCheck.textContent = '';
            if (!silent) console.warn('[Git] 远端更新检查失败:', e);
        }
    }

    function renderGitSwitchProgress(st) {
        els.gitSwitchProgress.hidden = false;
        const pct = Math.max(0, Math.min(100, st.progress || 0));
        els.gitSwitchProgressBar.style.width = pct + '%';
        els.gitSwitchProgressText.textContent = (st.step || st.message || '处理中...') + ' · ' + pct + '%';
        renderJobLog(els.gitSwitchLog, st.compileLog);
    }

    function setGitSwitchBusy(busy) {
        els.btnGitSwitch.disabled = busy;
        els.btnGitCheckUpdate.disabled = busy;
        els.gitRefSelect.disabled = busy;
    }

    function stopGitSwitchPoll() {
        if (gitSwitchTimer) { clearInterval(gitSwitchTimer); gitSwitchTimer = null; }
        els.btnGitSwitch.disabled = false;
        els.btnGitCheckUpdate.disabled = false;
        els.gitRefSelect.disabled = false;
    }

    async function onGitSwitchDone(projectId, st) {
        stopGitSwitchPoll();
        // DONE 时进度条还停留在最后一次编译快照，这里刷新为完成态
        els.gitSwitchProgress.hidden = false;
        els.gitSwitchProgressBar.style.width = '100%';
        els.gitSwitchProgressText.textContent = '✓ 切换完成 · 100%';
        const kind = st.refType === 'TAG' ? 'Tag' : '分支';
        let msg = '✓ 已切换到' + kind + '「' + st.ref + '」';
        let type = 'success';
        let duration = 2200;
        if (st.stashed) {
            msg += '\n本地未提交改动已自动 stash';
            duration = 3500;
        }
        if (st.conflict) {
            msg += '\n⚠ stash pop 冲突：改动仍保留在 stash 中未恢复，请手动处理';
            type = 'warn';
            duration = 6000;
        }
        showToast(msg, type, duration);
        await enterProject(projectId);
    }

    async function pollGitSwitchStatus(jobId, projectId) {
        try {
            const st = await fetchJson('/api/projects/' + encodeURIComponent(projectId) + '/git/switch/' + encodeURIComponent(jobId));
            renderGitSwitchProgress(st);
            if (st.status === 'DONE') {
                await onGitSwitchDone(projectId, st);
            } else if (st.status === 'FAILED') {
                stopGitSwitchPoll();
                els.gitSwitchProgressText.textContent = '切换失败: ' + (st.message || '未知错误');
                showToast('✕ 切换失败: ' + (st.message || '未知错误'), 'error', 5000);
            }
        } catch (e) {
            stopGitSwitchPoll();
            els.gitSwitchProgressText.textContent = '轮询异常: ' + e.message;
            showToast('✕ 切换状态查询失败: ' + e.message, 'error', 5000);
        }
    }

    async function startGitSwitch(projectId) {
        const sel = els.gitRefSelect;
        const opt = sel.selectedOptions && sel.selectedOptions[0];
        if (!opt || !opt.value) { showToast('请先选择要切换的分支或 Tag', 'warn'); return; }
        const ref = opt.value;
        const refType = opt.dataset.refType || 'BRANCH';
        const label = refType === 'TAG' ? 'Tag' : '分支';
        if (ref === els.gitCurrentRef.textContent) {
            if (!(await showConfirm('当前已在该' + label + '上，切换将重新拉取远端最新代码。\n确定继续？', '切换版本'))) return;
        } else if (!(await showConfirm('确定切换到' + label + '「' + ref + '」？\n将自动 stash 未提交改动，切换后重新编译并刷新分析结果。', '切换版本'))) {
            return;
        }
        setGitSwitchBusy(true);
        els.gitSwitchProgress.hidden = false;
        els.gitSwitchProgressBar.style.width = '2%';
        els.gitSwitchProgressText.textContent = '提交任务...';
        renderJobLog(els.gitSwitchLog, []);   // 清掉上一次的编译输出
        try {
            const st = await postJson('/api/projects/' + encodeURIComponent(projectId) + '/git/switch', { ref: ref, refType: refType });
            renderGitSwitchProgress(st);
            if (st.status === 'DONE') {
                await onGitSwitchDone(projectId, st);
                return;
            }
            if (st.status === 'FAILED') {
                stopGitSwitchPoll();
                els.gitSwitchProgressText.textContent = '切换失败: ' + (st.message || '未知错误');
                showToast('✕ 切换失败: ' + (st.message || '未知错误'), 'error', 5000);
                return;
            }
            gitSwitchTimer = setInterval(() => pollGitSwitchStatus(st.jobId, projectId), 2000);
        } catch (e) {
            stopGitSwitchPoll();
            els.gitSwitchProgressText.textContent = '提交失败: ' + e.message;
            showToast('✕ 切换提交失败: ' + e.message, 'error', 5000);
        }
    }

    els.btnGitSwitch.addEventListener('click', () => {
        if (!currentProjectId) return;
        startGitSwitch(currentProjectId);
    });

    els.btnGitCheckUpdate.addEventListener('click', async () => {
        if (!currentProjectId) return;
        await checkGitRemoteStatus(currentProjectId, false);
    });

    // ------------------------------------------------------------------
    // 交易入口扫描 + 勾选分析
    // ------------------------------------------------------------------

    // 旧的 scanEntries + pollScanProgress 已删除，Step2 扫描直接用同步 API
    // （见 btnEntryScan.addEventListener）

    let entryItems = [];  // { dto, rowEl, checkEl }

    function renderEntries(result) {
        entryItems = [];
        els.entryGroups.innerHTML = '';
        els.entryProjectName.textContent = result.projectName
            ? '· ' + result.projectName : '';
        els.entryFilter.value = '';

        let total = 0;
        result.groups.forEach((g) => {
            const box = document.createElement('div');
            box.className = 'entry-group';
            const head = document.createElement('div');
            head.className = 'entry-group-head';
            const title = document.createElement('span');
            title.className = 'entry-group-title';
            title.textContent = g.label + '（' + g.entries.length + '）';
            const typeBadge = document.createElement('span');
            typeBadge.className = 'badge entry-type-' + g.type.toLowerCase();
            typeBadge.textContent = g.type;
            const checkAll = document.createElement('button');
            checkAll.type = 'button';
            checkAll.className = 'btn small';
            checkAll.textContent = '选组';
            head.appendChild(title);
            head.appendChild(typeBadge);
            head.appendChild(checkAll);
            box.appendChild(head);

            const list = document.createElement('div');
            list.className = 'entry-list';
            g.entries.forEach((dto) => {
                const row = document.createElement('label');
                row.className = 'entry-row';
                const check = document.createElement('input');
                check.type = 'checkbox';
                const disp = document.createElement('span');
                disp.className = 'entry-display';
                disp.textContent = dto.display;
                const cls = document.createElement('span');
                cls.className = 'entry-class';
                cls.textContent = dto.className + '.' + dto.methodName;
                row.appendChild(check);
                row.appendChild(disp);
                row.appendChild(cls);
                list.appendChild(row);
                entryItems.push({ dto, rowEl: row, checkEl: check });
                total++;
            });
            checkAll.addEventListener('click', () => {
                const allOn = g.entries.every((dto) => {
                    const it = entryItems.find((i) => i.dto === dto);
                    return it && it.checkEl.checked;
                });
                g.entries.forEach((dto) => {
                    const it = entryItems.find((i) => i.dto === dto);
                    if (it) it.checkEl.checked = !allOn;
                });
                updateEntryCount();
            });
            box.appendChild(list);
            els.entryGroups.appendChild(box);
        });

        if (total === 0) {
            els.entryGroups.innerHTML = '<div class="hint">未发现交易入口（REST / Dubbo / ElasticJob / main），可改用手动填类名分析</div>';
        }
        updateEntryCount();
        els.entrySection.hidden = false;
        els.entrySection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function collectCheckedEntries() {
        return entryItems.filter((i) => i.checkEl.checked);
    }

    function buildEntryRequest(checked, skipCache) {
        return {
            projectPath: currentProjectPath(),
            maxDepth: parseInt(els.maxDepth.value, 10),
            skipCache: !!skipCache,
            entries: checked.map((i) => ({
                className: i.dto.className,
                methodName: i.dto.methodName,
                methodDescriptor: i.dto.methodDescriptor,
            })),
        };
    }

    async function analyzeCheckedEntries(checked, skipCache) {
        const req = buildEntryRequest(checked, skipCache);
        showLoading('正在分析 ' + checked.length + ' 个入口的调用链……');
        els.resultSection.hidden = true;
        try {
            const result = await postJson('/api/analyze', req);
            currentResult = result;
            currentRequest = req;
            renderResult(result);
        } catch (e) {
            showError(e.message);
        } finally {
            hideLoading();
        }
    }

    function updateEntryCount() {
        const checked = entryItems.filter((i) => i.checkEl.checked).length;
        els.entryCount.textContent = '已勾选 ' + checked + ' / ' + entryItems.length + ' 个入口';
    }

    function applyEntryFilter() {
        const q = els.entryFilter.value.trim().toLowerCase();
        entryItems.forEach((it) => {
            const hit = !q
                || it.dto.display.toLowerCase().indexOf(q) >= 0
                || it.dto.className.toLowerCase().indexOf(q) >= 0
                || it.dto.methodName.toLowerCase().indexOf(q) >= 0;
            it.rowEl.style.display = hit ? '' : 'none';
        });
    }

    els.entryFilter.addEventListener('input', applyEntryFilter);
    els.btnEntryAll.addEventListener('click', () => {
        entryItems.forEach((it) => { it.checkEl.checked = true; });
        updateEntryCount();
    });
    els.btnEntryNone.addEventListener('click', () => {
        entryItems.forEach((it) => { it.checkEl.checked = false; });
        updateEntryCount();
    });

    els.btnAnalyzeEntries.addEventListener('click', async () => {
        clearError();
        const checked = collectCheckedEntries();
        if (checked.length === 0) { showError('请至少勾选一个交易入口'); return; }
        await analyzeCheckedEntries(checked);
    });

    // ------------------------------------------------------------------
    // Excel 下载
    // ------------------------------------------------------------------

    els.btnExcel.addEventListener('click', async () => {
        // 批量清单视图 → 项目级导出（全部入口）
        if (currentExcelMode === 'project') {
            await downloadProjectExcel();
            return;
        }
        if (!currentResult || !currentRequest) return;
        clearError();
        showLoading('正在生成 Excel 报告……');
        try {
            // 导出时带上当前来源筛选 + 批量展开的入口缓存文件名，Excel 与页面展示保持一致
            const exportReq = Object.assign({}, currentRequest, {
                freqSourceFilter: freqFilter,
                cacheFileName: currentCacheFileName || '',
            });
            const resp = await fetch('/api/report/excel', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(exportReq),
            });
            if (!resp.ok) {
                const data = await resp.json().catch(() => ({}));
                throw new Error(data.error || ('下载失败: HTTP ' + resp.status));
            }
            const blob = await resp.blob();
            const disposition = resp.headers.get('Content-Disposition') || '';
            let filename = 'callgraph.xlsx';
            const starIdx = disposition.indexOf("filename*=UTF-8''");
            if (starIdx >= 0) {
                filename = decodeURIComponent(disposition.substring(starIdx + 17));
            }
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(a.href);
        } catch (e) {
            showError(e.message);
        } finally {
            hideLoading();
        }
    });

    /** 项目级 Excel：把所有已加载入口的缓存文件名 + 当前来源筛选发给后端，聚合导出 */
    async function downloadProjectExcel() {
        const files = (batchModel && batchModel.entries || [])
            .filter((s) => s.result)
            .map((s) => s.entry && s.entry.fileName)
            .filter(Boolean);
        if (!files.length) {
            showError('没有已加载的入口，请等待全量加载完成后再导出');
            return;
        }
        clearError();
        showLoading('正在生成项目级 Excel 报告……');
        try {
            const resp = await fetch('/api/report/excel-project', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    projectPath: currentProjectPath(),
                    cacheFiles: files,
                    freqSourceFilter: freqFilter,
                }),
            });
            if (!resp.ok) {
                const data = await resp.json().catch(() => ({}));
                throw new Error(data.error || ('下载失败: HTTP ' + resp.status));
            }
            const blob = await resp.blob();
            const disposition = resp.headers.get('Content-Disposition') || '';
            let filename = 'callgraph_project.xlsx';
            const starIdx = disposition.indexOf("filename*=UTF-8''");
            if (starIdx >= 0) {
                filename = decodeURIComponent(disposition.substring(starIdx + 17));
            }
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(a.href);
        } catch (e) {
            showError(e.message);
        } finally {
            hideLoading();
        }
    }

    // ------------------------------------------------------------------
    // 结果渲染
    // ------------------------------------------------------------------

    const SOURCE_LABEL = { PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' };
    const INVOKE_LABEL = {
        VIRTUAL: '虚调用', STATIC: '静态', INTERFACE: '接口',
        SPECIAL: '构造/super', DYNAMIC: 'lambda', IMPL: '接口实现分派',
    };

    function renderResult(result) {
        guideHasResult = true;
        guideResultStale = false;
        guideRefresh();
        // 批量分析结果 = 轻量清单索引（kind === 'batch'）→ 渲染入口清单视图
        if (result && result.kind === 'batch') {
            renderBatchSummary(result);
            if (window.Guide) {
                Guide.tipOnce('result-area', els.statsBar,
                    '过滤规则改完会自动作用到这里；也可点右上角「⟳ 刷新过滤」手动重刷统计与调用链。');
            }
            return;
        }
        // 单个入口的完整结果
        renderSingleEntryResult(result, null);
        if (window.Guide) {
            Guide.tipOnce('result-area', els.statsBar,
                '过滤规则改完会自动作用到这里；也可点右上角「⟳ 刷新过滤」手动重刷统计与调用链。');
        }
    }

    /** 批量结果清单视图：显示所有入口 + 每入口摘要，点击某行加载该入口的完整调用链 */
    function renderBatchSummary(batch) {
        currentBatchSummary = batch;
        const entries = batch.entries || [];
        const done = entries.filter((e) => !e.failed).length;
        const failedCount = entries.length - done;

        els.resultTitle.innerHTML =
            '<span class="result-title-text">交易链路分析结果</span>'
            + '<span class="entry-count-inline">（共 ' + entries.length + ' 个入口，成功 ' + done + ' 个'
            + (failedCount > 0 ? '，失败 ' + failedCount + ' 个' : '）') + '）</span>';
        renderStats(computeBatchFilteredStats(batch));
        renderWarnings({ warnings: batch.warnings || [] });
        hideBatchEntryHeader();

        els.tree.innerHTML = entries.map((entry, idx) => {
            const st = entry.stats || {};
            const chips = [
                st.totalNodes != null ? '节点 ' + st.totalNodes : null,
                st.projectMethods != null ? '项目 ' + st.projectMethods : null,
                st.dependencyMethods != null ? '依赖 ' + st.dependencyMethods : null,
                st.externalMethods != null ? '外部 ' + st.externalMethods : null,
                st.durationMs != null ? st.durationMs + 'ms' : null,
                st.truncated ? '截断' : null,
            ].filter(Boolean).join(' · ');
            return '<div class="bt-row" data-idx="' + idx + '" data-file="' + escapeHtml(entry.fileName || '') + '">'
                + '<span class="bt-index">' + (idx + 1) + '</span>'
                + '<span class="bt-method">' + sigHtmlFromString(readableFullSig(entry.className, entry.methodName, entry.descriptor) || '')
                + '</span>'
                + (entry.failed
                    ? '<span class="bt-failed">✗ 失败</span>'
                    : '<span class="bt-stats">' + escapeHtml(chips) + '</span>')
                + '<span class="bt-action"><span class="bt-caret">▸</span>展开</span>'
                + '</div>';
        }).join('');

        // 每个 .bt-row 后挂一个行内调用链容器（手风琴展开，不再跳转到独立视图）
        batchRowStates.length = 0;
        nodeRegistry.clear();
        expandFns.length = 0;
        els.tree.querySelectorAll('.bt-row').forEach((row, idx) => {
            const body = document.createElement('div');
            body.className = 'bt-body';
            body.style.display = 'none';
            row.after(body);
            const st = {
                idx,
                entry: entries[idx] || {},
                open: false,
                rendered: false,
                result: null,
                roots: null,
                body,
                rowEl: row,
            };
            batchRowStates[idx] = st;
            row.setAttribute('data-idx', idx);
            row.addEventListener('click', () => toggleEntryBody(idx));
        });
        markEntryRows();   // 重绘清单后恢复「含命中」入口标记

        els.freqSection.hidden = true;
        els.resultSection.hidden = false;
        // 批量清单：可直接导出整个项目的 Excel 报告
        currentExcelMode = 'project';
        els.btnExcel.disabled = false;
        els.btnExcel.title = '导出整个项目的 Excel 报告（全部入口调用链 + 项目级方法频率）';
        // 清单视图无树可展开/收起
        els.btnExpandAll.style.display = 'none';
        els.btnCollapseAll.style.display = 'none';
        els.legend.style.display = 'none';
        els.globalSearch.style.display = 'none';

        // ---- 项目级：自动全量加载全部入口调用链（浏览器端并行），完成后支持项目搜索 + 项目频率 ----
        if (!batchModel || batchModel.batch !== batch) {
            resetBatchModel(batch);
            // 关联全量加载生成的条目状态到 batchRowStates（供行内展开/搜索高亮按 index 取）
            els.projectSearch.hidden = true;
            loadAllBatchEntries(batch);          // async、不 await；统一进度由 batchProgressBar 呈现
        } else if (batchModel.loaded) {
            // 返回清单视图：恢复项目级面板（数据已载入内存，不重复下拉）
            showProjectPanels();
        }
    }

    /** 行内展开：首次展开时用全量加载缓存在行内渲染该入口调用链；再点收起 */
    async function toggleEntryBody(idx) {
        const st = batchRowStates[idx];
        if (!st) return;
        if (st.open) { closeEntryBody(st); return; }
        if (!st.rendered) {
            const result = await buildEntryBody(st);
            if (!result) { showError('该入口加载失败，无法展开'); return; }
        }
        st.open = true;
        st.rowEl.classList.add('open');
        updateCaret(st, true);
        st.body.style.display = '';
    }

    /** 从内存/按需拉取渲染某入口的行内树；返回 result 或 null */
    async function buildEntryBody(st) {
        const slot = batchModel && batchModel.entries[st.idx];
        let result = slot && slot.result;
        if (!result) {
            const e = st.entry;
            if (!e || e.failed || !e.fileName) {
                st.body.innerHTML = '<div class="bt-err">该入口分析失败或无可加载缓存</div>';
                st.body.style.display = '';
                return null;
            }
            st.body.innerHTML = '<div class="bt-loading">正在加载调用链…</div>';
            st.body.style.display = '';
            let resp;
            try {
                resp = await fetchEntryFile(e.fileName);
                if (!resp || !resp.ok) throw new Error((resp && resp.error) || '加载失败');
            } catch (err) {
                st.body.innerHTML = '<div class="bt-err">加载失败：' + escapeHtml((err && err.message) || String(err)) + '</div>';
                return null;
            }
            result = resp.result;
            if (slot) { slot.result = result; slot.status = 'ok'; }
        }
        st.result = result;
        st.body.innerHTML = '';
        st.roots = rootsOf(result, st.idx);
        st.roots.forEach((root) => st.body.appendChild(nodeEl(root, 0)));
        st.rendered = true;
        updateBatchRowStats(st);   // 行内统计 chips 按生效规则重算
        return result;
    }

    function closeEntryBody(st) {
        st.open = false;
        st.rowEl.classList.remove('open');
        st.body.style.display = 'none';
        updateCaret(st, false);
    }

    function updateCaret(st, openState) {
        const caret = st.rowEl.querySelector('.bt-caret');
        if (caret) caret.textContent = openState ? '▾' : '▸';
    }

    /** 加载批量中某个入口的完整分析结果并渲染（优先用已在内存的全量加载缓存，避免重复请求） */
    async function loadBatchEntry(fileName) {
        if (batchModel) {
            const slot = batchModel.entries.find((s) => s.entry && s.entry.fileName === fileName);
            if (slot && slot.result) {
                renderSingleEntryResult(slot.result, fileName);
                return;
            }
        }
        try {
            showLoading('正在加载该入口的完整调用链...');
            const data = await fetchJson('/api/projects/' + encodeURIComponent(currentProjectId)
                + '/cache/load-file?file=' + encodeURIComponent(fileName));
            if (!data.ok) { showError(data.error || '加载失败'); return; }
            currentResult = data.result;
            currentCacheFileName = fileName;
            renderSingleEntryResult(data.result, fileName);
        } catch (e) {
            showError(e.message);
        } finally {
            hideLoading();
        }
    }

    // ------------------------------------------------------------------
    // 批量全量加载：浏览器端并发拉取全部入口缓存 → 建项目级方法索引 + 项目级频率，
    // 树的 DOM 仍按入口按需渲染。统一进度：分析 0~80%，链加载 80~100%。
    // ------------------------------------------------------------------

    function resetBatchModel(batch) {
        batchModel = {
            batch,
            projectId: currentProjectId,
            // 预播种为与原清单 entries 顺序一致的槽位（下标即行号），失败的标记 err 但保留占位
            entries: (batch.entries || []).map((entry) => ({
                entry, result: null, status: entry && entry.failed ? 'err' : 'pending', err: null,
            })),
            done: 0, total: 0, failed: 0,
            loaded: false,    // 全部可加载项是否已就绪
            projectFreq: [],  // 项目级聚合频率
        };
        freqViewMode = 'project';
        // 换了批次：旧的搜索标记全部失效
        projSearchMarks = null;
        projSearchOrder = [];
        projSearchCursor = -1;
    }

    async function fetchEntryFile(fileName) {
        return fetchJson('/api/projects/' + encodeURIComponent(currentProjectId)
            + '/cache/load-file?file=' + encodeURIComponent(fileName));
    }

    /** 并发（limit 个 worker）加载全部入口，统一进度条继续从 80% 走到 100% */
    async function loadAllBatchEntries(batch) {
        const all = batch.entries || [];
        batchModel.entries = all.map((entry) => ({
            entry, result: null, status: entry && entry.failed ? 'err' : 'pending', err: null,
        }));
        // 可取加载的原始行号（失败/无缓存文件的不拉取）
        const positions = [];
        all.forEach((e, i) => { if (e && !e.failed && e.fileName) positions.push(i); });
        const total = positions.length;
        batchModel.total = total;
        if (total === 0) { finalizeBatchLoad(); return; }

        els.batchProgress.hidden = false;
        els.batchProgressBar.style.width = '80%';
        els.batchProgressText.textContent = '加载全部调用链 0/' + total + ' ...';
        setBatchLoadState('⏳ 正在加载全部调用链 0/' + total);

        const LIMIT = 8;
        let next = 0, done = 0, failed = 0;
        async function worker() {
            for (;;) {
                const pos = positions[next++];
                if (pos === undefined) return;
                const slot = batchModel.entries[pos];
                try {
                    const resp = await fetchEntryFile(slot.entry.fileName);
                    if (!resp || !resp.ok) throw new Error((resp && resp.error) || '加载失败');
                    slot.result = resp.result;
                    slot.status = 'ok';
                } catch (err) {
                    failed++;
                    slot.status = 'err';
                    slot.err = (err && err.message) || String(err);
                } finally {
                    done++;
                    els.batchProgressBar.style.width = Math.min(100, 80 + Math.round(20 * done / total)) + '%';
                    els.batchProgressText.textContent = '加载调用链 ' + done + '/' + total
                        + (failed ? '（失败 ' + failed + '）' : '');
                }
            }
        }
        await Promise.all(Array.from({ length: Math.min(LIMIT, total) }, () => worker()));

        batchModel.done = done;
        batchModel.failed = failed;
        finalizeBatchLoad();
    }

    /** 全量加载收尾：聚合频率，置加载完成态并展示项目级面板 */
    function finalizeBatchLoad() {
        const loaded = batchModel.entries.filter((s) => s.result).length;
        batchModel.loaded = true;
        batchModel.projectFreq = buildProjectFreq(batchModel.entries);
        const truncated = (batchModel.batch.entries || []).some((e) => e.stats && e.stats.truncated);

        els.batchProgressBar.style.width = '100%';
        els.batchProgressText.textContent = '✓ 加载完成 · 已加载 ' + loaded + '/' + batchModel.total + ' 个入口';
        setBatchLoadState(
            (truncated ? '存在截断入口，索引可能不完整 · ' : '')
            + '已加载 ' + loaded + '/' + batchModel.total + ' 个入口'
            + (batchModel.failed ? ' · 失败 ' + batchModel.failed + ' 个（点「重新分析」或下方行重试）' : '')
            + '，可在下方搜索某个方法被哪些交易入口调用');
        showProjectPanels();
    }

    /** 展示项目级面板：项目搜索 + 项目级频率（不再触载加载） */
    function showProjectPanels() {
        els.projectSearch.hidden = false;
        freqFilter = 'ALL';
        renderProjectFreq();
    }

    function setBatchLoadState(text) {
        if (els.projectLoadState) els.projectLoadState.textContent = text;
    }

    // ---------- 项目级方法索引与频率（基于已加载的 graph.methods / graph.edges） ----------

    /** 图方法唯一键：owner#name+descriptor（含返回类型，全局唯一；与后端 MethodKey 同口径） */
    function batchMethodKey(m) {
        return (m.owner || '') + '#' + (m.name || '') + (m.descriptor || '');
    }

    // ------------------------------------------------------------------
    // 方法签名统一展示：全限定类名#方法名(参数类型短名列表)，例：com.foo.Bar#write(Object, int)
    // ------------------------------------------------------------------
    const SIG_PRIM = { Z: 'boolean', B: 'byte', C: 'char', S: 'short', I: 'int', J: 'long', F: 'float', D: 'double', V: 'void' };

    /** JVM 描述符参数 → 简短类型名列表，例：'(Ljava/lang/String;I)' → ['String', 'int'] */
    function descriptorParamNames(descriptor) {
        const out = [];
        if (!descriptor) return out;
        const i = descriptor.indexOf('(');
        const j = descriptor.lastIndexOf(')');
        if (i < 0 || j <= i) return out;
        const body = descriptor.substring(i + 1, j);
        let k = 0, n = body.length;
        while (k < n) {
            let arr = '';
            while (body[k] === '[') { arr += '[]'; k++; }
            const c = body[k];
            if (c === 'L') {
                const semi = body.indexOf(';', k);
                const internal = body.substring(k + 1, semi < 0 ? n : semi);
                out.push(shortInternalName(internal) + arr);
                k = semi < 0 ? n : semi + 1;
            } else {
                out.push((SIG_PRIM[c] || c) + arr);
                k++;
            }
        }
        return out;
    }
    function shortInternalName(internal) {
        const slash = internal.lastIndexOf('/');
        const dollar = internal.lastIndexOf('$');
        const cut = Math.max(slash, dollar);
        return cut < 0 ? internal : internal.substring(cut + 1);
    }
    /** 统一可读签名：全限定类名#方法名(参数...)。无方法名(整类入口)只显示类名。 */
    function readableFullSig(className, methodName, descriptor) {
        const cls = className || '';
        if (!methodName) return cls;
        if (descriptor) {
            const params = descriptorParamNames(descriptor);
            return params.length > 0
                ? cls + '#' + methodName + '(' + params.join(', ') + ')'
                : cls + '#' + methodName + '()';
        }
        return cls + '#' + methodName;
    }
    /** 把统一可读签名拆成 类名 / 方法名 / 参数 三段，供分色展示 */
    function sigPartsFromString(sig) {
        const s = sig == null ? '' : String(sig);
        const hash = s.indexOf('#');
        if (hash < 0) return { cls: s, method: '', params: '' };
        const cls = s.slice(0, hash);
        const rest = s.slice(hash + 1);
        const paren = rest.indexOf('(');
        if (paren < 0) return { cls, method: rest, params: '' };
        return { cls, method: rest.slice(0, paren), params: rest.slice(paren) };
    }

    /** 分色渲染签名（HTML 字符串）：类名淡、方法名深、入参次之 */
    function sigHtmlFromString(sig) {
        const p = sigPartsFromString(sig);
        if (!p.method) {
            return '<span class="sig"><span class="sig-cls">' + escapeHtml(p.cls) + '</span></span>';
        }
        return '<span class="sig">'
            + '<span class="sig-cls">' + escapeHtml(p.cls) + '</span>'
            + '<span class="sig-sep">#</span>'
            + '<span class="sig-mname">' + escapeHtml(p.method) + '</span>'
            + (p.params ? '<span class="sig-params">' + escapeHtml(p.params) + '</span>' : '')
            + '</span>';
    }

    /** 分色渲染签名（DOM 节点），供只能用 textContent 的场景；外层 .sig 保证在 flex 容器里不被拆散 */
    function appendSigFromString(parent, sig) {
        const p = sigPartsFromString(sig);
        const wrap = document.createElement('span');
        wrap.className = 'sig';
        const mk = (cls, text) => {
            const el = document.createElement('span');
            el.className = cls;
            el.textContent = text;
            return el;
        };
        if (!p.method) {
            wrap.appendChild(mk('sig-cls', p.cls));
        } else {
            wrap.appendChild(mk('sig-cls', p.cls));
            wrap.appendChild(mk('sig-sep', '#'));
            wrap.appendChild(mk('sig-mname', p.method));
            if (p.params) wrap.appendChild(mk('sig-params', p.params));
        }
        parent.appendChild(wrap);
        return parent;
    }

    /** 判断一段参数是否已经是 JVM descriptor（形如 ''/J/I/[Ljava/lang/String;） */
    function looksLikeDescriptor(body) {
        let i = 0, n = body.length;
        while (i < n) {
            while (i < n && body[i] === '[') i++;
            if (i >= n) return false;
            const c = body[i];
            if (c === 'L') {
                const s = body.indexOf(';', i);
                if (s < 0) return false;
                i = s + 1;
            } else if ('ZBCSIFDV'.indexOf(c) >= 0) {
                i++;
            } else {
                return false;
            }
        }
        return true;   // 空 body 也是合法 descriptor（无参方法）
    }
    /** 可读参数类型名 → 单个 JVM 类型描述符（尽力转换，供"精确重载"检测，检测端会自动校正） */
    function readableTypeToDescriptor(name) {
        let arr = '', t = name;
        while (t.endsWith('[]')) { arr = '[' + arr; t = t.slice(0, -2); }
        const primR = { boolean: 'Z', byte: 'B', char: 'C', short: 'S', int: 'I', long: 'J', float: 'F', double: 'D', void: 'V' };
        if (primR[t]) return arr + primR[t];
        if (t === 'Object') return arr + 'Ljava/lang/Object;';
        if (t === 'String') return arr + 'Ljava/lang/String;';
        if (t === 'Class') return arr + 'Ljava/lang/Class;';
        return arr + 'L' + t.replace(/\./g, '/').replace(/\./g, '/') + ';';
    }
    function readableParamsToDescriptor(body) {
        const parts = body.split(',').map((s) => s.trim());
        return '(' + parts.filter(Boolean).map(readableTypeToDescriptor).join('') + ')';
    }

    /**
     * 跨入口聚合项目级频率。
     * 调用次数语义 = **不同调用位置的计数**：同一调用位置（同一调用方的同一行）
     * 不论出现在多少个交易入口的调用链里，都只计一次。
     */
    function buildProjectFreq(entries) {
        const agg = new Map();
        entries.forEach((slot) => {
            const g = slot.result && slot.result.graph;
            if (!g || !g.methods || !g.edges) return;
            const methods = g.methods, edges = g.edges;
            for (const e of edges) {
                const to = methods[e.to];
                if (!to) continue;
                const key = batchMethodKey(to);
                let rec = agg.get(key);
                if (!rec) {
                    // sites：跨入口去重后的"调用位置"集合（调用方方法 @ 行号）
                    rec = { sites: new Set(), source: to.source, display: to.display, callers: new Map() };
                    agg.set(key, rec);
                }
                const from = methods[e.from];
                const line = e.line || 0;
                const siteKey = (from ? batchMethodKey(from) : '?') + '@' + line;
                if (rec.sites.has(siteKey)) continue;   // 该位置已计过，不重复累加
                rec.sites.add(siteKey);
                if (from && rec.callers.size < 20) {
                    const d = from.display || '?';
                    if (!rec.callers.has(d)) rec.callers.set(d, line);
                }
            }
        });
        return Array.from(agg.values())
            .sort((a, b) => b.sites.size - a.sites.size || (a.display < b.display ? -1 : 1))
            .map((r) => ({
                method: r.display,
                source: r.source,
                callCount: r.sites.size,
                callers: Array.from(r.callers.entries()).map(([caller, line]) => ({ caller, line })),
            }));
    }

    /** 频率区数据源：项目级聚合 或 当前单入口的 methodFrequency */
    function currentFreqData() {
        if (freqViewMode === 'project' && batchModel && batchModel.projectFreq) return batchModel.projectFreq;
        return (currentResult && currentResult.methodFrequency) || [];
    }

    /** 规则变更后刷新频次列表：自动按当前视图取数（项目级聚合 / 单入口） */
    function refreshFreqView() {
        if (freqViewMode === 'project') {
            if (batchModel && batchModel.projectFreq) renderFreqList(batchModel.projectFreq);
        } else if (currentResult) {
            renderFreqList(currentResult.methodFrequency || []);
        }
    }

    /** 渲染项目级频率（复用 renderFreqList，来源过滤/样板规则自动生效） */
    function renderProjectFreq() {
        if (!batchModel || !batchModel.projectFreq) return;
        freqViewMode = 'project';
        els.freqSection.hidden = false;
        renderFreqList(batchModel.projectFreq);
    }

    // ---------- 项目级方法搜索：搜方法 → 列出调用它的所有交易入口 ----------

    function clearProjectSearch() {
        els.projectSearchResult.textContent = '';
        els.projectSearchResult.className = 'search-result';
        clearProjectMarks();
    }

    function runProjectSearch() {
        const term = els.projectSearchInput.value.trim();
        clearProjectSearch();
        if (!term) return;
        if (!batchModel || !batchModel.loaded) {
            els.projectSearchResult.className = 'search-result err';
            els.projectSearchResult.textContent = '全量加载尚未完成，请稍后再搜';
            return;
        }
        const exact = els.projectSearchMode.value === 'exact';
        const { marks, order } = computeSearchMarks(term, exact);
        if (order.length === 0) {
            els.projectSearchResult.className = 'search-result err';
            els.projectSearchResult.textContent = '未命中 —— 项目内没有方法匹配 "' + term + '"';
            return;
        }
        projSearchMarks = marks;
        projSearchOrder = order;
        projSearchCursor = -1;
        // 只标记不展开：入口行标「含命中」，树上节点按 命中/含命中 两级引导
        applyNodeMarks();
        markEntryRows();
        els.projectSearchResult.className = 'search-result ok';
        els.projectSearchResult.textContent = projSearchSummary();
    }

    /** 原始图方法是否命中（口径同 matchNode，输入为 graph.methods 中的原始方法） */
    function matchRawMethod(m, term, exact) {
        const q = (term || '').trim();
        if (!q) return false;
        const name = m.name || '', display = m.display || '', owner = m.owner || '';
        const className = owner.replace(/\//g, '.');
        if (exact) {
            return name === q || display === q || (className + '.' + name) === q;
        }
        const needle = q.toLowerCase();
        return [name, display, owner, className].some((v) => v && v.toLowerCase().indexOf(needle) >= 0);
    }

    /** 项目级搜索：按入口计算 命中集合 / 含命中集合（含命中 = 命中节点的祖先，沿边表反向闭包） */
    function computeSearchMarks(term, exact) {
        const marks = new Map();
        const order = [];
        const entries = (batchModel && batchModel.entries) || [];
        entries.forEach((slot, idx) => {
            const g = slot.result && slot.result.graph;
            if (!g || !g.methods) return;
            const targets = [];
            g.methods.forEach((m, i) => {
                if (!m) return;
                // 被 noise 规则剪掉的方法不参与搜索（视图里本来就看不到）
                if (isNoiseGraphMethod(m)) return;
                if (matchRawMethod(m, term, exact)) targets.push(i);
            });
            if (targets.length === 0) return;
            const hit = new Set(targets);
            const hasHit = new Set();
            const parents = graphIndex(slot.result).parents;
            const stack = targets.slice();
            while (stack.length) {
                const cur = stack.pop();
                (parents.get(cur) || []).forEach((p) => {
                    if (hit.has(p) || hasHit.has(p)) return;
                    hasHit.add(p);
                    stack.push(p);
                });
            }
            marks.set(idx, { hit, hasHit });
            targets.slice().sort((a, b) => a - b).forEach((gid) => order.push({ entryIdx: idx, gid }));
        });
        return { marks, order };
    }

    /** 节点所属入口的搜索标记；无标记返回 null */
    function markOf(node) {
        if (!projSearchMarks || node.entryIdx == null) return null;
        return projSearchMarks.get(node.entryIdx) || null;
    }

    /** 给单个树节点套用 命中 / 含命中 标记（徽标插在 .row-badges 内，位于 🔎 之前） */
    function applyNodeMark(node, row) {
        row.classList.remove('is-hit', 'has-hit');
        const badges = row.querySelector('.row-badges');
        const old = badges && badges.querySelector('.row-hit-mark');
        if (old) old.remove();
        const mk = markOf(node);
        if (!mk || !badges) return;
        const isHit = mk.hit.has(node.gid);
        if (!isHit && !mk.hasHit.has(node.gid)) return;
        row.classList.add(isHit ? 'is-hit' : 'has-hit');
        const tag = document.createElement('span');
        tag.className = 'row-hit-mark' + (isHit ? ' is-hit-mark' : '');
        tag.textContent = isHit ? '● 命中' : '⇣ 含命中';
        badges.appendChild(tag);
    }

    /** 对当前已渲染的树节点重刷标记（搜索/清除时调用） */
    function applyNodeMarks() {
        nodeRegistry.forEach((entry, node) => {
            if (entry && entry.rowEl) applyNodeMark(node, entry.rowEl);
        });
    }

    /** 入口清单行标记：含命中的入口加「含命中 N 处」 */
    function markEntryRows() {
        batchRowStates.forEach((st) => {
            if (!st || !st.rowEl) return;
            st.rowEl.classList.remove('has-hit');
            const old = st.rowEl.querySelector('.bt-hit-mark');
            if (old) old.remove();
        });
        if (!projSearchMarks) return;
        projSearchMarks.forEach((mk, idx) => {
            const st = batchRowStates[idx];
            if (!st || !st.rowEl) return;
            st.rowEl.classList.add('has-hit');
            const tag = document.createElement('span');
            tag.className = 'bt-hit-mark';
            tag.textContent = '含命中 ' + mk.hit.size + ' 处';
            const action = st.rowEl.querySelector('.bt-action');
            st.rowEl.insertBefore(tag, action || null);
        });
    }

    /** 清除项目级搜索标记（入口行 + 树上节点） */
    function clearProjectMarks() {
        projSearchMarks = null;
        projSearchOrder = [];
        projSearchCursor = -1;
        applyNodeMarks();
        markEntryRows();
    }

    /** 搜索状态文案 */
    function projSearchSummary() {
        if (!projSearchOrder.length) return '';
        const cursor = projSearchCursor >= 0
            ? ' · 当前位置 ' + (projSearchCursor + 1) + '/' + projSearchOrder.length
            : '';
        return '命中 ' + projSearchOrder.length + ' 处 · 已标记 ' + projSearchMarks.size
            + ' 个入口（展开逐层引导，点「下一个命中」可跳转）' + cursor;
    }

    /** 引导展开：无分叉（只有一个含命中的子节点）就一路穿透，遇分叉只展开这一层 */
    function expandGuided(node, setExpanded) {
        setExpanded(true);
        if (!projSearchMarks) return;
        let cur = node;
        for (;;) {
            const mk = markOf(cur);
            if (!mk || mk.hit.has(cur.gid)) return;   // 无标记 / 已到命中 → 停止
            const kids = cur.children || [];
            if (kids.length !== 1) return;            // 有分叉 → 只展开这一层
            const only = kids[0];
            if (mk.hit.has(only.gid)) return;         // 下一层就是命中，已可见
            if (!mk.hasHit.has(only.gid)) return;
            const ke = nodeRegistry.get(only);
            if (!ke || !ke.setExpanded) return;
            ke.setExpanded(true);
            cur = only;
        }
    }

    /** 展开某入口到指定命中的完整路径并定位（供「下一个命中」），返回命中节点行 */
    function revealHitPath(entryIdx, gid) {
        const st = batchRowStates[entryIdx];
        if (!st) return null;
        if (!st.rendered && !buildEntryBodySync(st)) return null;
        if (!st.open) {
            st.open = true;
            st.rowEl.classList.add('open');
            st.body.style.display = '';
            updateCaret(st, true);
        }
        const g = st.result && st.result.graph;
        if (!g) return null;
        // 命中 → 根：沿父边回溯出一条路径（取第一个父即可）
        const parents = graphIndex(st.result).parents;
        const chain = [gid];
        let cur = gid;
        for (let guard = 0; guard < 10000; guard++) {
            const ps = (parents.get(cur) || []).filter((p) => chain.indexOf(p) < 0);
            if (!ps.length) break;
            cur = ps[0];
            chain.push(cur);
        }
        chain.reverse();   // 根 → 命中
        let node = st.roots.find((r) => r.gid === chain[0]);
        if (!node) return null;
        for (let i = 0; i < chain.length - 1; i++) {
            const en = nodeRegistry.get(node);
            if (!en || !en.setExpanded) break;
            en.setExpanded(true);
            const next = (node.children || []).find((c) => c.gid === chain[i + 1]);
            if (!next) break;
            node = next;
        }
        const te = nodeRegistry.get(node);
        if (te && te.rowEl) {
            te.rowEl.classList.add('flash');
            setTimeout(() => te.rowEl.classList.remove('flash'), 1600);
            te.rowEl.scrollIntoView({ block: 'center', behavior: 'smooth' });
            return te.rowEl;
        }
        return null;
    }

    /** 下一个命中：像 Excel 查找下一个一样循环定位 */
    function nextProjectHit() {
        if (!projSearchOrder.length) {
            els.projectSearchResult.className = 'search-result err';
            els.projectSearchResult.textContent = '请先搜索，再定位下一个命中';
            return;
        }
        projSearchCursor = (projSearchCursor + 1) % projSearchOrder.length;
        const item = projSearchOrder[projSearchCursor];
        revealHitPath(item.entryIdx, item.gid);
        els.projectSearchResult.className = 'search-result ok';
        els.projectSearchResult.textContent = projSearchSummary();
    }

    /** 上一个命中：反向循环定位 */
    function prevProjectHit() {
        if (!projSearchOrder.length) {
            els.projectSearchResult.className = 'search-result err';
            els.projectSearchResult.textContent = '请先搜索，再定位上一个命中';
            return;
        }
        projSearchCursor = (projSearchCursor - 1 + projSearchOrder.length) % projSearchOrder.length;
        const item = projSearchOrder[projSearchCursor];
        revealHitPath(item.entryIdx, item.gid);
        els.projectSearchResult.className = 'search-result ok';
        els.projectSearchResult.textContent = projSearchSummary();
    }

    /** 在内存结果已就绪时同步建树（不拉网络），供搜索批量展开使用 */
    function buildEntryBodySync(st) {
        const slot = batchModel && batchModel.entries[st.idx];
        const result = slot && slot.result;
        if (!result) return null;
        st.result = result;
        st.body.innerHTML = '';
        st.roots = rootsOf(result, st.idx);
        st.roots.forEach((root) => st.body.appendChild(nodeEl(root, 0)));
        st.rendered = true;
        return result;
    }

    els.btnProjectSearch.addEventListener('click', runProjectSearch);
    els.projectSearchInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') runProjectSearch(); });
    els.projectSearchNextHit.addEventListener('click', nextProjectHit);
    els.projectSearchPrevHit.addEventListener('click', prevProjectHit);
    els.btnProjectSearchClear.addEventListener('click', clearProjectSearch);

    // ------------------------------------------------------------------
    // 图结构适配层：把 schema=2 的 result.graph 惰性还原为树视图节点。
    // 不整体物化冗余树：每个节点的 children 是 getter，首次访问才构建子节点；
    // 环方法作为叶子返回（子树已在上层路径，避免全局搜索无限递归）。
    // ------------------------------------------------------------------

    // 结果对象 → 其惰性根节点缓存的映射（保证渲染与全局搜索共用同一对象身份，命中 nodeRegistry）
    // 额外按 noise 规则哈希失效：规则变更时自动重建
    let _adapterCache = { node: null, idx: null, noiseHash: '', roots: [] };
    // noise 判定记忆：同一对象只判定一次（规则变更时由 compileActiveNoiseRules 整体丢弃）
    let noiseMemo = new WeakMap();       // graph.methods 原始方法对象 → 是否噪声
    let freqNoiseMemo = new WeakMap();   // methodFrequency 条目对象 → 是否噪声

    /** 启用中的 noise 规则哈希：用于 cache 失效 + 剪枝（值已在规则变更时算好） */
    function noiseRulesHash() {
        return activeNoiseHash;
    }

    /** 基于原始 GraphMethod 字段（owner/name/descriptor/source）判断 noise，避免字符串解析开销 */
    function isNoiseGraphMethod(gm) {
        if (!gm) return false;
        const hit = noiseMemo.get(gm);
        if (hit !== undefined) return hit;
        const v = matchCompiledRules(gm.source || 'EXTERNAL',
            (gm.owner || '').replace(/\//g, '.'), gm.name || '', descriptorParamCount(gm.descriptor));
        noiseMemo.set(gm, v);
        return v;
    }

    /** 从 JVM 描述符里取参数个数（不依赖方法名后缀） */
    function descriptorParamCount(descriptor) {
        if (!descriptor) return 0;
        const i = descriptor.indexOf('(');
        const j = descriptor.lastIndexOf(')');
        if (i < 0 || j <= i) return 0;
        const body = descriptor.substring(i + 1, j);
        let count = 0, k = 0, n = body.length;
        while (k < n) {
            while (body[k] === '[') k++;
            const c = body[k];
            if (c === 'L') {
                const semi = body.indexOf(';', k);
                k = semi < 0 ? n : semi + 1;
            } else if (c !== 'V') {
                k++;
            } else {
                break;
            }
            count++;
        }
        return count;
    }

    /** 清 adapter cache（规则变更/项目切换时调用） */
    function invalidateAdapterCache() {
        _adapterCache = { node: null, idx: null, noiseHash: '', roots: [] };
    }

    // 图索引缓存：同一 result 的邻接表/父表只建一次（原实现每次重算都重建）
    let _graphIndexCache = new WeakMap();

    /** 取（并缓存）某 result 的图索引：{adj: 出边表, parents: 父节点表} */
    function graphIndex(result) {
        let cached = _graphIndexCache.get(result);
        if (cached) return cached;
        const g = (result && result.graph) || {};
        const adj = {};
        const parents = new Map();
        (g.edges || []).forEach((e) => {
            (adj[e.from] = adj[e.from] || []).push(e);
            const arr = parents.get(e.to) || [];
            arr.push(e.from);
            parents.set(e.to, arr);
        });
        cached = { adj: adj, parents: parents };
        _graphIndexCache.set(result, cached);
        return cached;
    }

    /** 规则变更后自动重绘所有已展开的调用链（剪枝即时生效） */
    function reapplyFilterToTree() {
        // 先清搜索标记（依赖旧 registry 上的行元素，须在清空 registry 之前调用）
        if (typeof clearProjectSearch === 'function') clearProjectSearch();
        invalidateAdapterCache();

        // 批量清单视图：els.tree 里是 .bt-row 行，不能整树重绘
        const inBatchList = !!(els.tree && els.tree.querySelector('.bt-row'));

        // 重建已展开的批量行内调用链；同时清掉旧节点注册，避免残留
        nodeRegistry.clear();
        expandFns.length = 0;
        if (Array.isArray(batchRowStates)) {
            batchRowStates.forEach((st) => {
                if (!st || !st.result || !st.rendered || !st.body) return;
                st.body.innerHTML = '';
                st.roots = rootsOf(st.result, st.idx);
                st.roots.forEach((root) => st.body.appendChild(nodeEl(root, 0)));
            });
        }

        // 单入口视图（非批量清单）：整树重绘
        if (!inBatchList && els.tree && els.tree.children.length > 0 && currentResult) {
            clearSearchHits();
            els.tree.innerHTML = '';
            rootsOf(currentResult).forEach((root) => els.tree.appendChild(nodeEl(root, 0)));
        }
    }

    function rootsOf(result, entryIdx) {
        const curNoiseHash = noiseRulesHash();
        if (_adapterCache.node === result
            && _adapterCache.idx === entryIdx
            && _adapterCache.noiseHash === curNoiseHash) return _adapterCache.roots;
        // 兼容退路：无 graph 的旧结果（正常不再发生）
        if (!result || !result.graph) {
            const legacy = (result && result.roots) || [];
            return legacy;
        }
        const g = result.graph;
        const adj = graphIndex(result).adj;
        function methodView(m) {
            const owner = m.owner || '';
            const cn = owner.replace(/\//g, '.');
            const lastSlash = owner.lastIndexOf('/');
            return {
                name: m.name,
                display: readableFullSig(cn, m.name, m.descriptor) || m.display,
                className: cn,
                simpleClassName: lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner,
            };
        }
        function makeNode(id, parentEdge, depth) {
            const m = g.methods[id];
            if (!m) return null;
            // —— 剪枝：命中 noise 规则的方法及其整棵子树从视图里消失 ——
            if (isNoiseGraphMethod(m)) return null;
            const view = methodView(m);
            let kidsCache = null;
            const node = {
                gid: id,             // 图内方法 id：批量搜索/高亮定位用
                entryIdx: entryIdx,  // 所属入口序号：项目级搜索标记/引导用
                source: m.source,
                invokeType: parentEdge ? parentEdge.invoke : null,
                line: parentEdge ? (parentEdge.line || 0) : 0,
                cycle: !!m.cycle,
                truncated: false,
                method: view,
                get children() {
                    if (kidsCache) return kidsCache;
                    if (m.cycle) { kidsCache = []; return kidsCache; }
                    kidsCache = (adj[id] || [])
                        .map((e) => makeNode(e.to, e, depth + 1))
                        .filter(Boolean);   // 被 noise 过滤的子节点 makeNode 返回 null，此处剔除
                    return kidsCache;
                }
            };
            return node;
        }
        const roots = (g.roots || [])
            .map((id) => makeNode(id, null, 0))
            .filter(Boolean);   // 根方法自己就命中 noise 的情况
        _adapterCache = { node: result, idx: entryIdx, noiseHash: curNoiseHash, roots: roots };
        return _adapterCache.roots;
    }

    /** 单个入口的完整结果渲染（批量展开时 fileName 非空，显示「返回清单」） */
    function renderSingleEntryResult(result, fileName) {
        const entryCount = result.stats && result.stats.entryCount
            ? result.stats.entryCount
            : (result.roots ? result.roots.length : 0);
        els.resultTitle.innerHTML =
            '<span class="result-title-text">交易链路分析结果</span>'
            + '<span class="entry-count-inline">（共 ' + entryCount + ' 个入口）</span>'
            + (fileName
                ? '<span class="entry-count-inline"> · ' + escapeHtml(result.className || '')
                    + (result.methodName ? '#' + escapeHtml(result.methodName) : '') + '</span>'
                : '');
        renderStats(computeFilteredStats(result));
        renderWarnings(result);
        renderFreqAnalysis(result);
        expandFns = [];
        nodeRegistry.clear();
        clearSearchHits();
        closeSearchBar();
        resetGlobalSearch();
        els.tree.innerHTML = '';
        rootsOf(result).forEach((root) => els.tree.appendChild(nodeEl(root, 0)));
        els.resultSection.hidden = false;
        els.freqSection.hidden = false;
        els.btnExcel.disabled = false;
        currentExcelMode = 'entry';
        els.btnExcel.title = '导出当前入口的 Excel 报告';
        els.btnExpandAll.style.display = '';
        els.btnCollapseAll.style.display = '';
        els.legend.style.display = '';
        els.globalSearch.style.display = '';
        // 批量模式下显示「返回清单」
        if (fileName && currentBatchSummary) {
            els.btnBackToList.hidden = false;
        } else {
            els.btnBackToList.hidden = true;
        }
    }

    /** 回到批量清单视图 */
    function backToBatchList() {
        if (!currentBatchSummary) return;
        currentResult = currentBatchSummary;
        currentCacheFileName = null;
        renderBatchSummary(currentBatchSummary);
    }

    function hideBatchEntryHeader() {
        els.btnBackToList.hidden = true;
    }

    function renderStats(stats) {
        const chips = [
            ['总节点', stats.totalNodes],
            ['项目方法', stats.projectMethods],
            ['依赖方法', stats.dependencyMethods],
            ['外部方法', stats.externalMethods],
            ['入口方法', stats.entryCount],
            ['耗时', stats.durationMs + ' ms'],
        ];
        els.statsBar.innerHTML = chips.map(([k, v]) =>
            '<span class="stat-chip">' + k + '<b>' + v + '</b></span>').join('')
            + (stats.truncated
                ? '<span class="stat-chip" style="color:#b91c1c;border-color:#fecaca">结果已截断（深度/节点上限）</span>'
                : '');
    }

    /** 按当前生效过滤规则重算统计（口径同后端 fillStats：图内去重方法数；noise 方法及其子树不计入） */
    function computeFilteredStats(result) {
        const base = (result && result.stats) || {};
        const out = {
            entryCount: base.entryCount || 0,
            totalNodes: 0,
            projectMethods: 0,
            dependencyMethods: 0,
            externalMethods: 0,
            truncated: !!base.truncated,
            durationMs: base.durationMs || 0,
        };
        if (!result) return out;
        if (!result.graph || !result.graph.methods) {
            // 兼容退路：旧结果无图结构，按展示树迭代统计
            const stack = (result.roots || []).slice();
            while (stack.length) {
                const n = stack.pop();
                out.totalNodes++;
                const s = n.source || 'EXTERNAL';
                if (s === 'PROJECT') out.projectMethods++;
                else if (s === 'DEPENDENCY') out.dependencyMethods++;
                else out.externalMethods++;
                (n.children || []).forEach((c) => stack.push(c));
            }
            return out;
        }
        const g = result.graph;
        const adj = graphIndex(result).adj;
        const seen = new Set();
        const stack = (g.roots || []).slice();
        while (stack.length) {
            const id = stack.pop();
            if (seen.has(id)) continue;
            seen.add(id);
            const m = g.methods[id];
            // 剪枝口径与 rootsOf/makeNode 一致：noise 方法及其整棵子树不可见，不计数
            if (!m || isNoiseGraphMethod(m)) continue;
            out.totalNodes++;
            const s = m.source || 'EXTERNAL';
            if (s === 'PROJECT') out.projectMethods++;
            else if (s === 'DEPENDENCY') out.dependencyMethods++;
            else out.externalMethods++;
            if (m.cycle) continue;   // 环方法同树视图：展示但不展开
            const edges = adj[id] || [];
            for (let i = 0; i < edges.length; i++) stack.push(edges[i].to);
        }
        return out;
    }

    /** 批量清单某行：按生效规则重算该行的行内统计 chips（格式同 renderBatchSummary） */
    function updateBatchRowStats(st) {
        if (!st || !st.result || !st.rowEl) return;
        const fs = computeFilteredStats(st.result);
        const st0 = st.result.stats || {};
        const chips = [
            '节点 ' + fs.totalNodes,
            '项目 ' + fs.projectMethods,
            '依赖 ' + fs.dependencyMethods,
            '外部 ' + fs.externalMethods,
            st0.durationMs != null ? st0.durationMs + 'ms' : null,
            st0.truncated ? '截断' : null,
        ].filter(Boolean).join(' · ');
        const span = st.rowEl.querySelector('.bt-stats');
        if (span) span.textContent = chips;
    }

    /** 批量清单聚合统计：全量加载完成时按生效规则重算，否则退回后端统计 */
    function computeBatchFilteredStats(batch) {
        if (!(batchModel && batchModel.loaded && batchModel.entries)) return batch.stats || {};
        const agg = { entryCount: 0, totalNodes: 0, projectMethods: 0, dependencyMethods: 0, externalMethods: 0, truncated: false, durationMs: 0 };
        let counted = 0;
        batchModel.entries.forEach((slot) => {
            if (!slot || !slot.result) return;
            const fs = computeFilteredStats(slot.result);
            agg.entryCount += 1;
            agg.totalNodes += fs.totalNodes;
            agg.projectMethods += fs.projectMethods;
            agg.dependencyMethods += fs.dependencyMethods;
            agg.externalMethods += fs.externalMethods;
            if (fs.truncated) agg.truncated = true;
            agg.durationMs += fs.durationMs || 0;
            counted++;
        });
        return counted > 0 ? agg : (batch.stats || {});
    }

    /** 依据当前生效规则刷新统计条：单入口重算；批量清单重算聚合 + 已展开行的行内统计 */
    function updateFilteredStats() {
        if (!currentResult) return;
        if (els.tree && els.tree.querySelector('.bt-row')) {
            batchRowStates.forEach((st) => {
                if (st && st.rendered && st.result) updateBatchRowStats(st);
            });
            return;
        }
        renderStats(computeFilteredStats(currentResult));
    }

    /** 上次已整体刷新所依据的规则指纹（用于跳过无变化的重算） */
    let _lastAppliedRulesHash = null;

    /**
     * 规则变更后的统一刷新：频次列表 + 调用链剪枝 + 统计条。
     * 规则指纹未变化时直接跳过——打开弹窗、点「刷新过滤」这类常见操作不再触发全量重算。
     */
    function refreshAllFilteredViews() {
        const curHash = noiseRulesHash();
        if (curHash === _lastAppliedRulesHash) return;
        _lastAppliedRulesHash = curHash;
        refreshFreqView();
        reapplyFilterToTree();
        updateFilteredStats();
    }

    // ------------------------------------------------------------------
    // 方法调用次数分析：所有方法按被调次数降序，点击行展开查看调用位置
    // ------------------------------------------------------------------

    function renderFreqAnalysis(result) {
        const all = result.methodFrequency || [];
        // 每次新分析重置筛选为"全部"
        freqFilter = 'ALL';
        freqViewMode = 'entry';
        updateFreqFilterChips(all);
        renderFreqList(all);
    }

    /** 渲染筛选标签，显示各来源的方法数 */
    function updateFreqFilterChips(all) {
        const counts = { ALL: all.length, PROJECT: 0, DEPENDENCY: 0, EXTERNAL: 0 };
        all.forEach((m) => {
            const s = m.source || 'EXTERNAL';
            if (counts[s] !== undefined) counts[s]++;
        });
        els.freqFilterBar.querySelectorAll('.filter-chip').forEach((chip) => {
            const f = chip.dataset.filter;
            const label = chip.textContent.replace(/\s*\d+$/, '');
            chip.textContent = label + ' ' + counts[f];
            chip.classList.toggle('active', f === freqFilter);
        });
    }

    /** 后端 methodFrequency 的 Top-N 上限（与 AnalysisService.DEFAULT_METHOD_TOP_N 对齐） */
    const FREQ_TOP_N = 200;

    /** methodId → 可读方法签名（从当前单入口结果的节点表 graph.methods 解析） */
    function resolveFreqSignature(id) {
        const g = currentResult && currentResult.graph;
        const methods = g && g.methods;
        if (methods && id != null && methods[id]) {
            const m = methods[id];
            return m.display || ((m.owner || '').replace(/\//g, '.') + '#' + (m.name || ''));
        }
        return null;
    }

    /**
     * 归一化频次条目 → 展示用形态 {method, source, callCount, callers:[{caller,line}]}。
     * 后端条目（存 methodId/callerId）用节点表解析出签名；项目级聚合条目（前端计算，本就含
     * method 字符串）原样返回。
     */
    function normalizeFreqItem(item) {
        if (!item || item.method) return item;
        return {
            method: resolveFreqSignature(item.methodId) || ('#' + item.methodId),
            source: item.source,
            callCount: item.callCount,
            callers: (item.callers || []).map((c) => ({
                caller: c.caller || resolveFreqSignature(c.callerId) || ('#' + c.callerId),
                line: c.line,
            })),
        };
    }

    // 频次列表当前数据快照：调用方明细改为展开时才生成（首屏不再拼巨量 HTML）
    let _freqRows = [];

    /** 前端分页大小（OPT-19）：单页最多渲染 FREQ_PAGE_SIZE 行，避免一次拼 200 行巨量 HTML */
    const FREQ_PAGE_SIZE = 50;
    /** 当前页码（0 基）；切换来源 / 改动过滤 / 新分析时重置为 0 */
    let _freqPage = 0;

    /**
     * 渲染频次列表当前页（含翻页器）。
     * data-idx 仍用全局下标，行内操作（导出/过滤）与调用方明细展开沿用 _freqRows 索引。
     */
    function renderFreqPage() {
        const size = FREQ_PAGE_SIZE;
        const total = _freqRows.length;
        const totalPages = Math.max(1, Math.ceil(total / size));
        _freqPage = Math.min(Math.max(_freqPage, 0), totalPages - 1);
        const start = _freqPage * size;
        const rowsHtml = _freqRows.slice(start, start + size).map((item, i) => {
            const idx = start + i;                  // 全局下标 = 排行榜名次 - 1
            const top3 = idx < 3 ? ' mf-top3' : '';  // 前三名高亮改按名次判定，避免分页后错位
            return '<div class="mf-item' + top3 + '" data-idx="' + idx + '">'
                + '<div class="mf-row">'
                + '<span class="mf-toggle">▸</span>'
                + '<span class="mf-rank">' + (idx + 1) + '</span>'
                + '<span class="mf-body">'
                + '<span class="mf-method">' + sigHtmlFromString(item.method) + '</span>'
                + badgeHtml('source-' + (item.source || '').toLowerCase(),
                    SOURCE_LABEL[item.source] || item.source)
                + '</span>'
                + '<span class="mf-hot">'
                + '<span class="mf-count">' + item.callCount + '</span>'
                + '<span class="mf-count-unit">次</span>'
                + '</span>'
                + '<span class="mf-acts">'
                + '<button type="button" class="mf-act mf-export" title="导出该方法的全部调用位置（CSV）">导出</button>'
                + '<button type="button" class="mf-act mf-filter" title="把该方法加入过滤规则并立即生效">过滤</button>'
                + '</span>'
                + '</div>'
                + '<div class="mf-callers" style="display:none"></div>'   // 明细展开时才填充
                + '</div>';
        }).join('');
        const pagerHtml = totalPages > 1
            ? '<div class="freq-pager">'
                + '<button type="button" class="freq-pager-btn freq-pager-prev"'
                + (_freqPage <= 0 ? ' disabled' : '') + '>‹ 上一页</button>'
                + '<span class="freq-pager-info">第 ' + (_freqPage + 1) + ' / ' + totalPages
                + ' 页 · 共 ' + total + ' 条</span>'
                + '<button type="button" class="freq-pager-btn freq-pager-next"'
                + (_freqPage >= totalPages - 1 ? ' disabled' : '') + '>下一页 ›</button>'
                + '</div>'
            : '';
        els.freqList.innerHTML = rowsHtml + pagerHtml;
    }

    /** 按当前 freqFilter + 启用的样板规则过滤并渲染方法列表 */
    function renderFreqList(all) {
        // 后端 methodFrequency 现在只存 methodId/callerId：先归一化为展示用的字符串签名；
        // 项目级聚合（前端计算）本就是字符串形态、原样保留。
        const raw = all || [];
        const serverSide = raw.some((x) => x && x.methodId !== undefined && x.method === undefined);
        const topNTruncated = serverSide && raw.length >= FREQ_TOP_N;
        const normalized = raw.map(normalizeFreqItem);
        // 第一步：按样板规则过滤（跨所有来源），用于更新统计和来源标签
        const noiseFiltered = normalized.filter((m) => !isNoiseMethod(m));
        // 来源标签计数 = 样板规则过滤后的各来源数量
        updateFreqFilterChips(noiseFiltered);
        // 第二步：在样板过滤基础上再按当前来源筛选
        const list = noiseFiltered.filter((m) => {
            if (freqFilter !== 'ALL' && (m.source || 'EXTERNAL') !== freqFilter) return false;
            return true;
        });
        // 统计栏：方法总数 + 最高/最低被调次数 + 已过滤数量（+ Top-N 截断提示）
        const noiseRemoved = normalized.length - noiseFiltered.length;
        const topNChip = topNTruncated
            ? '<span class="stat-chip" style="color:#b45309;border-color:#fde68a">'
                + '仅显示被调最高的 ' + FREQ_TOP_N + ' 个（Top-N 截断）</span>'
            : '';
        if (list.length > 0) {
            const max = list[0].callCount;
            const min = list[list.length - 1].callCount;
            els.freqStatsBar.innerHTML = [
                ['方法总数', list.length],
                ['已过滤', noiseRemoved + ' 个'],
                ['最高被调', max + ' 次'],
                ['最低被调', min + ' 次'],
            ].map(([k, v]) =>
                '<span class="stat-chip">' + k + '<b>' + v + '</b></span>').join('') + topNChip;
        } else {
            els.freqStatsBar.innerHTML =
                '<span class="stat-chip">方法总数 <b>0</b></span>'
                + '<span class="stat-chip">已过滤 <b>' + noiseRemoved + ' 个</b></span>'
                + topNChip;
        }
        if (list.length === 0) {
            _freqRows = [];
            _freqPage = 0;
            els.freqList.innerHTML =
                '<div class="mf-empty">该来源下暂无可统计的方法调用数据</div>';
            return;
        }
        _freqRows = list;
        _freqPage = 0;   // 数据变化（切换来源 / 过滤 / 新分析）回到第一页
        renderFreqPage();
    }

    /** 懒渲染某行的调用方明细（首次展开时才生成 DOM，避免首屏拼巨量 HTML） */
    function fillFreqCallers(itemEl) {
        const callers = itemEl.querySelector('.mf-callers');
        if (!callers || callers.dataset.filled === '1') return;
        const item = _freqRows[Number(itemEl.dataset.idx)];
        const rows = ((item && item.callers) || [])
            .map((c) => '<div class="mf-caller">'
                + '<span class="mf-caller-mark">↳</span>'
                + '<span class="mf-caller-name">' + sigHtmlFromString(c.caller) + '</span>'
                + (c.line && c.line > 0 ? '<span class="line-no">L' + c.line + '</span>' : '')
                + '</div>')
            .join('');
        callers.innerHTML = rows || '<div class="mf-empty-sub">暂无调用方信息</div>';
        callers.dataset.filled = '1';
    }

    /** 批量展开/收起：展开时按需填充明细 */
    function setAllFreqCallers(open) {
        els.freqList.querySelectorAll('.mf-item').forEach((item) => {
            const c = item.querySelector('.mf-callers');
            const t = item.querySelector('.mf-toggle');
            if (!c) return;
            if (open) fillFreqCallers(item);
            c.style.display = open ? 'block' : 'none';
            if (t) t.textContent = open ? '▾' : '▸';
        });
    }

    // ------------------------------------------------------------------
    // 频次行操作：导出调用位置 / 加入过滤规则
    // ------------------------------------------------------------------

    /** 拆分展示用签名：全限定类名#方法名(参数短名) → 各部分 */
    function splitMethodSignature(method) {
        const s = method || '';
        const hashIdx = s.indexOf('#');
        const className = hashIdx >= 0 ? s.slice(0, hashIdx) : '';
        const rest = hashIdx >= 0 ? s.slice(hashIdx + 1) : s;
        const parenIdx = rest.indexOf('(');
        const methodName = parenIdx >= 0 ? rest.slice(0, parenIdx) : rest;
        const dot = className.lastIndexOf('.');
        return {
            className: className,
            simpleClass: dot >= 0 ? className.slice(dot + 1) : className,
            methodName: methodName,
            paramCount: parseParamCount(rest),
        };
    }

    /** 正则元字符转义：把方法名/类名当字面量匹配 */
    function escapeRegex(s) {
        return String(s == null ? '' : s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    /** 当前频次视图对应的分析结果集合（项目级聚合 → 全部已加载入口） */
    function freqTargetResults() {
        if (freqViewMode === 'project' && batchModel && batchModel.entries) {
            return batchModel.entries.map((s) => s && s.result).filter(Boolean);
        }
        return currentResult ? [currentResult] : [];
    }

    /** 图上方法的标准签名（与列表展示同口径） */
    function graphMethodSig(m) {
        return readableFullSig((m.owner || '').replace(/\//g, '.'), m.name, m.descriptor || '')
            || m.display || '';
    }

    /** 收集某方法的全部调用位置：直接扫图，不受页面调用方明细展示上限影响 */
    function collectMethodCallers(item) {
        const wantSource = item.source || null;
        const rows = [];
        const seen = new Set();
        freqTargetResults().forEach((result) => {
            const g = result && result.graph;
            if (!g || !g.methods || !g.edges) return;
            const targets = new Set();
            g.methods.forEach((m, i) => {
                if (!m) return;
                if (graphMethodSig(m) !== item.method && (m.display || '') !== item.method) return;
                if (wantSource && (m.source || 'EXTERNAL') !== wantSource) return;
                targets.add(i);
            });
            if (targets.size === 0) return;
            g.edges.forEach((e) => {
                if (!targets.has(e.to)) return;
                const from = g.methods[e.from];
                const caller = from ? graphMethodSig(from) : '?';
                const line = e.line || 0;
                // 去重键与项目级聚合口径一致：调用方方法键 @ 行号（同一调用位置只算一次）
                const key = (from ? batchMethodKey(from) : '?') + '@' + line;
                if (seen.has(key)) return;
                seen.add(key);
                rows.push({ caller: caller, line: line });
            });
        });
        return rows;
    }

    /** 导出某方法全部调用位置为 CSV */
    function exportFreqMethodCallers(item) {
        const rows = collectMethodCallers(item);
        if (rows.length === 0) {
            showToast('未找到该方法的调用位置', 'warn');
            return;
        }
        const esc = (v) => '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"';
        const csv = '\uFEFF' + ['被调方法,调用方方法,行号']
            .concat(rows.map((r) => [esc(item.method), esc(r.caller), r.line > 0 ? r.line : ''].join(',')))
            .join('\r\n');
        const sig = splitMethodSignature(item.method);
        const base = ((sig.simpleClass || 'method') + '_' + (sig.methodName || 'callers'))
            .replace(/[\\/:*?"<>|#]/g, '_');
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
        a.download = base + '_callers.csv';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(a.href);
        showToast('✓ 已导出 ' + rows.length + ' 处调用位置');
    }

    /** 判断是否已有等价的过滤规则（方法/类/来源/参数数全一致） */
    function sameNoiseRule(a, b) {
        return (a.methodPattern || '') === (b.methodPattern || '')
            && (a.classPattern || '') === (b.classPattern || '')
            && (a.source || 'ALL') === (b.source || 'ALL')
            && (a.paramCount != null ? a.paramCount : null) === (b.paramCount != null ? b.paramCount : null);
    }

    /** 保存地址：有项目 → 项目层；否则全局层 */
    function noiseRulesSaveTarget() {
        const pp = currentProjectPath();
        if (currentProjectId && pp) {
            return { url: 'api/noise-rules?projectPath=' + encodeURIComponent(pp), project: true };
        }
        return { url: 'api/noise-rules', project: false };
    }

    /** 把某方法加入过滤规则并立即保存生效（同时刷新调用链分析与调用次数分析） */
    function filterFreqMethod(item) {
        const sig = splitMethodSignature(item.method);
        if (!sig.methodName) {
            showToast('无法解析该方法签名，已跳过', 'error');
            return;
        }
        const rule = {
            id: 'mf-' + Date.now().toString(36),
            name: (sig.simpleClass ? sig.simpleClass + '#' : '') + sig.methodName,
            methodPattern: '^' + escapeRegex(sig.methodName) + '$',
            classPattern: sig.className ? '^' + escapeRegex(sig.className) + '$' : '',
            source: item.source || 'ALL',
            paramCount: sig.paramCount,
            enabled: true,
        };
        const target = noiseRulesSaveTarget();
        const existing = target.project ? projectRulesCache : globalRulesCache;
        if (existing.some((r) => sameNoiseRule(r, rule))) {
            showToast('该方法的过滤规则已存在，无需重复添加', 'warn');
            return;
        }
        const body = target.project
            ? { globalOverrides: globalOverrides, customRules: projectRulesCache.concat([rule]) }
            : globalRulesCache.concat([rule]);
        fetch(target.url, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        }).then((r) => r.json()).then(() => {
            // 重新拉取两层规则（含覆盖）→ 重算剪枝与统计
            return loadNoiseRules();
        }).then(() => {
            refreshAllFilteredViews();
            markNoiseConfigured();
            showToast('✓ 已加入过滤规则并生效：' + rule.name
                + (target.project ? '（本项目）' : '（全局）'));
        }).catch(() => showToast('过滤规则保存失败', 'error'));
    }

    // 事件委托：整个频次列表只挂一个监听（原实现每行挂闭包 + N 次 querySelectorAll）
    els.freqList.addEventListener('click', (e) => {
        // 翻页器按钮（分页渲染，OPT-19）：切页后重渲染当前页
        const pagerBtn = e.target.closest('.freq-pager-btn');
        if (pagerBtn && !pagerBtn.disabled) {
            _freqPage += pagerBtn.classList.contains('freq-pager-prev') ? -1 : 1;
            renderFreqPage();
            return;
        }
        // 行内操作按钮优先处理，且不触发展开/收起
        const act = e.target.closest('.mf-act');
        if (act) {
            const actItem = act.closest('.mf-item');
            const row = actItem && _freqRows[Number(actItem.dataset.idx)];
            if (!row) return;
            if (act.classList.contains('mf-export')) exportFreqMethodCallers(row);
            else filterFreqMethod(row);
            return;
        }
        const row = e.target.closest('.mf-row');
        if (!row) return;
        const itemEl = row.parentElement;
        const callers = itemEl.querySelector('.mf-callers');
        const toggle = row.querySelector('.mf-toggle');
        if (!callers) return;
        if (callers.style.display === 'block') {
            callers.style.display = 'none';
            if (toggle) toggle.textContent = '▸';
            return;
        }
        fillFreqCallers(itemEl);
        callers.style.display = 'block';
        if (toggle) toggle.textContent = '▾';
    });

    els.btnFreqExpandAll.addEventListener('click', () => setAllFreqCallers(true));
    els.btnFreqCollapseAll.addEventListener('click', () => setAllFreqCallers(false));

    /** 判断方法是否命中任一启用的样板规则（按条目对象记忆化，避免重复解析签名） */
    function isNoiseMethod(m) {
        if (!m || !m.method) return false;
        const hit = freqNoiseMemo.get(m);
        if (hit !== undefined) return hit;
        const src = m.source || 'EXTERNAL';
        const hashIdx = m.method.indexOf('#');
        const className = hashIdx >= 0 ? m.method.slice(0, hashIdx) : '';
        let methodWithArgs = hashIdx >= 0 ? m.method.slice(hashIdx + 1) : m.method;
        const parenIdx = methodWithArgs.indexOf('(');
        const methodName = parenIdx >= 0 ? methodWithArgs.slice(0, parenIdx) : methodWithArgs;
        const paramCount = parseParamCount(methodWithArgs);
        const v = matchCompiledRules(src, className, methodName, paramCount);
        freqNoiseMemo.set(m, v);
        return v;
    }

    /** 从方法签名解析参数个数，正确处理泛型中的逗号，如 Map<String, Integer> */
    function parseParamCount(methodWithArgs) {
        if (!methodWithArgs) return 0;
        const paren = methodWithArgs.indexOf('(');
        if (paren < 0) return 0;
        let closeParen = methodWithArgs.indexOf(')', paren);
        if (closeParen < 0) closeParen = methodWithArgs.length;
        const params = methodWithArgs.slice(paren + 1, closeParen).trim();
        if (!params) return 0;
        let depth = 0;
        let count = 1;
        for (let i = 0; i < params.length; i++) {
            const c = params.charAt(i);
            if (c === '<' || c === '(') depth++;
            else if (c === '>' || c === ')') depth--;
            else if (c === ',' && depth === 0) count++;
        }
        return count;
    }

    // 来源筛选：全部/项目/依赖/外部
    els.freqFilterBar.querySelectorAll('.filter-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
            freqFilter = chip.dataset.filter;
            els.freqFilterBar.querySelectorAll('.filter-chip')
                .forEach((c) => c.classList.toggle('active', c === chip));
            refreshFreqView();
        });
    });

    // ------------------------------------------------------------------
    // 样板方法过滤规则管理（两层：全局 + 项目级）
    // ------------------------------------------------------------------
    // 注意：currentProjectPath() 已在上方定义（第 373 行），此处不再重复
    //       噪声规则的 API URL 构造通过 noiseRulesApiUrl() 走同一份逻辑

    function noiseRulesApiUrl() {
        let url = 'api/noise-rules';
        if (noiseRuleScope === 'project') {
            const pp = currentProjectPath();
            if (pp) url += '?projectPath=' + encodeURIComponent(pp);
        }
        return url;
    }

    /** 实际生效的过滤规则集 = 全局层（套用项目覆盖）+ 项目自定义层（未选项目时仅全局） */
    function recomputeActiveNoiseRules() {
        if (currentProjectId && currentProjectPath()) {
            const overriddenGlobal = globalRulesCache.map(r => {
                if (r.id && globalOverrides.hasOwnProperty(r.id)) {
                    return Object.assign({}, r, { enabled: globalOverrides[r.id] });
                }
                return r;
            });
            activeNoiseRules = overriddenGlobal.concat(projectRulesCache);
        } else {
            activeNoiseRules = globalRulesCache.slice();
        }
        compileActiveNoiseRules();
    }

    /**
     * 预编译生效规则 + 预计算指纹。
     * 性能关键：正则只在这里编译一次，判定热路径只做 test()；指纹只算一次，避免反复 JSON.stringify。
     */
    function compileActiveNoiseRules() {
        const enabled = (activeNoiseRules || []).filter((r) => r && r.enabled);
        compiledActiveRules = enabled.map((r) => ({
            source: r.source || 'ALL',
            paramCount: r.paramCount != null ? r.paramCount : null,
            methodRe: compileRegex(r.methodPattern),
            classRe: compileRegex(r.classPattern),
        }));
        activeNoiseHash = enabled.map((r) => (r.source || 'ALL') + '~' + (r.methodPattern || '')
            + '~' + (r.classPattern || '') + '~' + (r.paramCount != null ? r.paramCount : '')).join('|');
        // 规则已变，丢弃旧的判定记忆
        noiseMemo = new WeakMap();
        freqNoiseMemo = new WeakMap();
    }

    /** 编译正则：空/未填 → null（匹配全部）；非法 → false（永不匹配） */
    function compileRegex(pattern) {
        if (!pattern || pattern.trim() === '') return null;
        try {
            return new RegExp(pattern);
        } catch (e) {
            return false;
        }
    }

    /** 按预编译规则判定噪声（gm 字段通道与签名通道共用同一套语义） */
    function matchCompiledRules(src, className, methodName, paramCount) {
        for (const cr of compiledActiveRules) {
            if (cr.source !== 'ALL' && cr.source !== src) continue;
            if (cr.methodRe === false) continue;                    // 非法正则 → 永不匹配
            if (cr.methodRe && !cr.methodRe.test(methodName)) continue;
            if (cr.classRe === false) continue;
            if (cr.classRe && !cr.classRe.test(className)) continue;
            if (cr.paramCount != null && cr.paramCount !== paramCount) continue;
            return true;
        }
        return false;
    }

    /** 从磁盘同步两层规则：更新两层缓存 + 编辑缓冲区指向当前 scope 层，并重绘规则列表 */
    function loadNoiseRules() {
        const pp = currentProjectPath();
        const gReq = fetch('api/noise-rules').then((r) => r.json()).catch(() => []);
        const pReq = (currentProjectId && pp)
            ? fetch('api/noise-rules/project-detail?projectPath=' + encodeURIComponent(pp))
                .then((r) => r.json()).catch(() => null)
            : Promise.resolve(null);
        return Promise.all([gReq, pReq]).then(([g, detail]) => {
            globalRulesCache = g || [];
            if (detail) {
                globalOverrides = detail.globalOverrides || {};
                projectRulesCache = detail.customRules || [];
            } else {
                globalOverrides = {};
                projectRulesCache = [];
            }
            recomputeActiveNoiseRules();
            noiseRules = (noiseRuleScope === 'project' ? projectRulesCache : globalRulesCache).slice();
            renderNoiseRulesList();
            return noiseRules;
        });
    }

    /** 弹窗固定为项目级视图（全局规则本体在系统配置页维护） */
    function openNoiseRulesPanel() {
        noiseRuleMode = 'panel';
        noiseRuleScope = 'project';
        updateNoiseRulesUI();
        updateNoiseRulesScopeHint();
        // loadNoiseRules 内部已重绘规则列表，这里不再重复调用
        loadNoiseRules().then(refreshAllFilteredViews);
        els.noiseRulesPanel.hidden = false;
        els.noiseRulesOverlay.hidden = false;
        // 首次打开给一次性的就地提示
        if (window.Guide) {
            Guide.tipOnce('noise-panel', els.noiseRulesPanel.querySelector('.nr-toolbar'),
                '这里只影响本项目：可覆盖全局规则的启用/禁用，也可加本项目专属规则。规则启用后，命中它的方法会从调用链视图和 Excel 导出中过滤掉。');
        }
    }

    function updateNoiseRulesScopeHint() {
        const hint = document.getElementById('nrScopeHint');
        if (!hint) return;
        if (noiseRuleScope === 'global') {
            hint.textContent = '全局规则对所有项目生效';
            hint.title = '';
        } else {
            const pp = currentProjectPath();
            if (pp) {
                const segs = pp.replace(/[\\/]+$/, '').split(/[\\/]/);
                hint.textContent = '当前项目: ' + segs[segs.length - 1];
                hint.title = pp;
            } else {
                hint.textContent = '⚠ 未选择项目';
                hint.title = '';
            }
        }
    }
    function closeNoiseRulesPanel() {
        els.noiseRulesPanel.hidden = true;
        els.noiseRulesOverlay.hidden = true;
    }

    /** 根据当前 mode + scope 切换弹窗/页面内按钮可见性 */
    function updateNoiseRulesUI() {
        const isGlobalPanel = noiseRuleMode === 'panel' && noiseRuleScope === 'global';
        els.btnNoiseRuleAdd.hidden = isGlobalPanel;
        els.btnNoiseRuleReset.hidden = noiseRuleMode === 'panel';
        els.btnNoiseRuleImport.hidden = isGlobalPanel;
    }

    function renderNoiseRulesList() {
        const container = noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
        const isGlobalPanel = noiseRuleMode === 'panel' && noiseRuleScope === 'global';

        if (noiseRuleScope === 'project') {
            container.innerHTML = renderGlobalOverridesSection() + renderCustomRulesSection();
            return;
        }

        if (noiseRules.length === 0) {
            container.innerHTML = '<div class="mf-empty">暂无规则，点击"新增规则"添加</div>';
            return;
        }
        container.innerHTML =
            '<div class="nr-head-row">'
            + '<span>规则名称</span><span>方法名正则</span><span>类名正则</span><span>来源</span><span>参数个数</span><span>启用</span>'
            + (isGlobalPanel ? '' : '<span>操作</span>')
            + '</div>'
            + noiseRules.map((r, idx) => {
                // 内置规则（随 jar 打包）：本体只读，只有「启用」开关可改；不能删除
                const ro = isGlobalPanel || !!r.builtin;
                const roAttr = ro ? ' disabled' : '';
                const actions = isGlobalPanel
                    ? ''
                    : (r.builtin
                        ? '<span class="nr-builtin-tag" title="工具自带的内置规则，本体不可修改；可切换启用状态">内置</span>'
                        : '<button type="button" class="btn small warn nr-del">删除</button>');
                return '<div class="nr-item' + (ro ? ' readonly' : '') + '" data-idx="' + idx + '"'
                    + ' data-id="' + escapeHtml(r.id || '') + '"'
                    + ' data-builtin="' + (r.builtin ? '1' : '0') + '"'
                    + ' title="' + escapeHtml(r.name || '未命名规则') + '">'
                    + '<input class="nr-name" value="' + escapeHtml(r.name || '') + '" placeholder="规则名称"' + roAttr + '>'
                    + '<input class="nr-method" value="' + escapeHtml(r.methodPattern || '') + '" placeholder="方法名正则，如 getInstance"' + roAttr + ' title="方法名正则">'
                    + '<input class="nr-class" value="' + escapeHtml(r.classPattern || '') + '" placeholder="类名正则，可选"' + roAttr + ' title="类名正则">'
                    + '<select class="nr-source"' + roAttr + ' title="来源">'
                    + ['ALL', 'PROJECT', 'DEPENDENCY', 'EXTERNAL'].map((s) =>
                        '<option value="' + s + '"' + ((r.source || 'ALL') === s ? ' selected' : '') + '>'
                        + ({ ALL: '全部', PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' })[s]
                        + '</option>').join('')
                    + '</select>'
                    + '<input class="nr-paramcount" type="number" min="0" value="'
                        + (r.paramCount != null ? r.paramCount : '') + '" placeholder="参数数"' + roAttr + ' title="参数个数（留空不限）">'
                    + '<label class="nr-enable" title="启用"><input type="checkbox" ' + (r.enabled ? 'checked' : '') + '></label>'
                    + actions
                    + '</div>';
            }).join('');
    }

    /** 全局规则覆盖区：完整规则信息 + 全局启用状态 + 本项目三态覆盖（规则本体只读，在系统配置页维护） */
    function renderGlobalOverridesSection() {
        const rules = globalRulesCache;
        const srcLabel = { ALL: '全部', PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' };
        if (!rules || rules.length === 0) {
            return '<div class="nr-overrides-section"><div class="nr-section-title">全局规则覆盖（仅对本项目生效）</div><div class="mf-empty">暂无全局规则，可在「系统配置 → 过滤规则」中维护</div></div>';
        }
        const items = rules.map(r => {
            const ov = r.id && globalOverrides.hasOwnProperty(r.id)
                ? (globalOverrides[r.id] ? 'enabled' : 'disabled')
                : 'inherit';
            return '<div class="nr-item nr-override-item" data-rule-id="' + escapeHtml(r.id || '') + '" title="' + escapeHtml(r.name || '未命名规则') + '">'
                + '<input class="nr-name" readonly value="' + escapeHtml(r.name || '') + '" title="规则名称">'
                + '<input class="nr-method" readonly value="' + escapeHtml(r.methodPattern || '') + '" title="方法名正则">'
                + '<input class="nr-class" readonly value="' + escapeHtml(r.classPattern || '') + '" title="类名正则">'
                + '<input class="nr-source" readonly value="' + escapeHtml(srcLabel[r.source || 'ALL'] || '全部') + '" title="来源">'
                + '<input class="nr-paramcount" readonly value="' + (r.paramCount != null ? r.paramCount : '') + '" title="参数个数（留空不限）">'
                + '<span class="nr-global-state ' + (r.enabled ? 'on' : 'off') + '">' + (r.enabled ? '启用' : '禁用') + '</span>'
                + '<select class="nr-override-select" title="本项目覆盖">'
                + '<option value="inherit"' + (ov === 'inherit' ? ' selected' : '') + '>继承全局</option>'
                + '<option value="enabled"' + (ov === 'enabled' ? ' selected' : '') + '>启用</option>'
                + '<option value="disabled"' + (ov === 'disabled' ? ' selected' : '') + '>禁用</option>'
                + '</select></div>';
        }).join('');
        return '<div class="nr-overrides-section">'
            + '<div class="nr-section-title">全局规则覆盖（仅对本项目生效；规则本体在「系统配置 → 过滤规则」维护）</div>'
            + '<div class="nr-head-row nr-override-head">'
            + '<span>规则名称</span><span>方法名正则</span><span>类名正则</span><span>来源</span><span>参数个数</span><span>全局</span><span>本项目覆盖</span></div>'
            + '<div class="nr-override-list">' + items + '</div></div>'
            + '<div class="nr-section-sep"></div>';
    }

    function renderCustomRulesSection() {
        const items = noiseRules.map((r, idx) =>
            '<div class="nr-item" data-idx="' + idx + '" data-id="' + escapeHtml(r.id || '') + '"'
            + ' data-builtin="0" title="' + escapeHtml(r.name || '未命名规则') + '">'
            + '<input class="nr-name" value="' + escapeHtml(r.name || '') + '" placeholder="规则名称">'
            + '<input class="nr-method" value="' + escapeHtml(r.methodPattern || '') + '" placeholder="方法名正则，如 getInstance" title="方法名正则">'
            + '<input class="nr-class" value="' + escapeHtml(r.classPattern || '') + '" placeholder="类名正则，可选" title="类名正则">'
            + '<select class="nr-source" title="来源">'
            + ['ALL', 'PROJECT', 'DEPENDENCY', 'EXTERNAL'].map((s) =>
                '<option value="' + s + '"' + ((r.source || 'ALL') === s ? ' selected' : '') + '>'
                + ({ ALL: '全部', PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' })[s]
                + '</option>').join('')
            + '</select>'
            + '<input class="nr-paramcount" type="number" min="0" value="'
                + (r.paramCount != null ? r.paramCount : '') + '" placeholder="参数数" title="参数个数（留空不限）">'
            + '<label class="nr-enable" title="启用"><input type="checkbox" ' + (r.enabled ? 'checked' : '') + '></label>'
            + '<button type="button" class="btn small warn nr-del">删除</button>'
            + '</div>'
        ).join('');
        const headRow = '<div class="nr-head-row"><span>规则名称</span><span>方法名正则</span><span>类名正则</span><span>来源</span><span>参数个数</span><span>启用</span><span>操作</span></div>';
        return '<div class="nr-custom-section">'
            + '<div class="nr-section-title">自定义规则（仅本项目）</div>'
            + headRow
            + (items || '<div class="mf-empty">暂无自定义规则，点击"新增规则"添加</div>')
            + '</div>';
    }

    /** 从弹窗 / 页面输入收集规则（排除覆盖区项目） */
    function collectNoiseRulesFromPanel() {
        const container = noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
        const items = container.querySelectorAll('.nr-item:not(.nr-override-item)');
        const out = [];
        items.forEach((item) => {
            const pcInput = item.querySelector('.nr-paramcount').value.trim();
            out.push({
                // 必须沿用原有 id：项目级「全局规则覆盖」按 id 匹配，id 一变覆盖就全部失效
                id: item.dataset.id
                    || ('rule-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6)),
                builtin: item.dataset.builtin === '1',
                name: item.querySelector('.nr-name').value.trim(),
                methodPattern: item.querySelector('.nr-method').value.trim(),
                classPattern: item.querySelector('.nr-class').value.trim(),
                source: item.querySelector('.nr-source').value,
                paramCount: pcInput === '' ? null : parseInt(pcInput, 10),
                enabled: item.querySelector('.nr-enable input').checked,
            });
        });
        return out;
    }

    /** 从面板收集全局规则覆盖配置 */
    function collectOverridesFromPanel() {
        const overrides = {};
        const container = noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
        container.querySelectorAll('.nr-override-item').forEach((item) => {
            const ruleId = item.dataset.ruleId;
            const val = item.querySelector('.nr-override-select').value;
            if (val === 'enabled') overrides[ruleId] = true;
            else if (val === 'disabled') overrides[ruleId] = false;
        });
        return overrides;
    }

    /** 将弹窗/页面当前输入同步到内存并实时刷新频次列表 + 调用链剪枝预览。
     *  全局只读态（Step3 弹窗的全局 Tab）不实时生效：点「保存生效」后应用 */
    function applyNoiseRulesFromPanel() {
        if (noiseRuleMode === 'panel' && noiseRuleScope === 'global') return;
        noiseRules = collectNoiseRulesFromPanel();
        if (noiseRuleScope === 'project') {
            projectRulesCache = noiseRules.slice();
            globalOverrides = collectOverridesFromPanel();
        } else {
            globalRulesCache = noiseRules.slice();
        }
        recomputeActiveNoiseRules();
        refreshAllFilteredViews();
    }

    // Step3 入口：打开弹窗，默认切到「当前项目」Tab（全局 Tab 只读）
    els.btnNoiseRulesProject.addEventListener('click', () => openNoiseRulesPanel());
    els.btnNoiseRulesClose.addEventListener('click', closeNoiseRulesPanel);
    els.noiseRulesOverlay.addEventListener('click', closeNoiseRulesPanel);
    // 刷新过滤：弹窗/页面打开时同步未保存的编辑；关闭时从磁盘重新拉取两层规则并重新过滤
    els.btnFreqRefresh.addEventListener('click', () => {
        if (!els.noiseRulesPanel.hidden || !els.viewNoiseRules.hidden) {
            applyNoiseRulesFromPanel();
        } else {
            loadNoiseRules().then(refreshAllFilteredViews);
        }
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.noiseRulesPanel.hidden) closeNoiseRulesPanel();
    });

    // 弹窗 / 页面内任意输入变化都实时同步到内存并刷新频次列表（无需保存即可预览）
    els.noiseRulesList.addEventListener('change', (e) => {
        applyNoiseRulesFromPanel();
    });
    els.noiseRulesListPage.addEventListener('change', (e) => {
        applyNoiseRulesFromPanel();
    });

    // 全选启用 / 全不选 / 反选（作用在当前可见的列表）
    function forEachNoiseRuleCheckbox(fn) {
        const container = noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
        container.querySelectorAll('.nr-enable input').forEach(fn);
    }
    els.btnNoiseRuleSelectAll.addEventListener('click', () => {
        forEachNoiseRuleCheckbox((cb) => { cb.checked = true; });
        applyNoiseRulesFromPanel();
    });
    els.btnNoiseRuleSelectNone.addEventListener('click', () => {
        forEachNoiseRuleCheckbox((cb) => { cb.checked = false; });
        applyNoiseRulesFromPanel();
    });
    els.btnNoiseRuleInvert.addEventListener('click', () => {
        forEachNoiseRuleCheckbox((cb) => { cb.checked = !cb.checked; });
        applyNoiseRulesFromPanel();
    });
    els.btnNrPageSelectAll.addEventListener('click', () => {
        forEachNoiseRuleCheckbox((cb) => { cb.checked = true; });
        applyNoiseRulesFromPanel();
    });
    els.btnNrPageSelectNone.addEventListener('click', () => {
        forEachNoiseRuleCheckbox((cb) => { cb.checked = false; });
        applyNoiseRulesFromPanel();
    });
    els.btnNrPageInvert.addEventListener('click', () => {
        forEachNoiseRuleCheckbox((cb) => { cb.checked = !cb.checked; });
        applyNoiseRulesFromPanel();
    });

    // 导出规则：下载 JSON 文件（弹窗 / 页面共用）
    function exportNoiseRules() {
        let url = 'api/noise-rules/export';
        if (noiseRuleScope === 'project') {
            const pp = currentProjectPath();
            if (pp) url += '?projectPath=' + encodeURIComponent(pp);
        }
        window.location.href = url;
    }
    els.btnNoiseRuleExport.addEventListener('click', exportNoiseRules);
    els.btnNrPageExport.addEventListener('click', exportNoiseRules);

    // 导入规则：触发文件选择（弹窗 / 页面共用）
    function handleNoiseRuleImportFile(e, fileInput) {
        const file = e.target.files[0];
        if (!file) return;
        if (!confirm('导入将覆盖当前层级的所有规则，确定继续？')) {
            fileInput.value = '';
            return;
        }
        const fd = new FormData();
        fd.append('file', file);
        let url = 'api/noise-rules/import';
        if (noiseRuleScope === 'project') {
            const pp = currentProjectPath();
            if (pp) url += '?projectPath=' + encodeURIComponent(pp);
        }
        fetch(url, { method: 'POST', body: fd })
            .then((r) => {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then((data) => {
                noiseRules = data || [];
                if (noiseRuleScope === 'project') projectRulesCache = noiseRules.slice();
                else globalRulesCache = noiseRules.slice();
                recomputeActiveNoiseRules();
                renderNoiseRulesList();
                refreshAllFilteredViews();
            })
            .catch((err) => alert('导入失败：' + err.message + '（请确认是合法的 noise-rules.json 文件）'));
    }
    els.btnNoiseRuleImport.addEventListener('click', () => {
        els.noiseRuleImportFile.value = '';
        els.noiseRuleImportFile.click();
    });
    els.noiseRuleImportFile.addEventListener('change', (e) => handleNoiseRuleImportFile(e, els.noiseRuleImportFile));
    els.btnNrPageImport.addEventListener('click', () => {
        els.noiseRulePageImportFile.value = '';
        els.noiseRulePageImportFile.click();
    });
    els.noiseRulePageImportFile.addEventListener('change', (e) => handleNoiseRuleImportFile(e, els.noiseRulePageImportFile));

    // 新增规则（弹窗 / 页面共用）
    function addNoiseRule() {
        noiseRules.push({
            id: 'rule-' + Date.now(),
            builtin: false,
            name: '新规则',
            methodPattern: '',
            classPattern: '',
            source: 'ALL',
            paramCount: null,
            enabled: true,
        });
        renderNoiseRulesList();
        applyNoiseRulesFromPanel();
    }
    els.btnNoiseRuleAdd.addEventListener('click', addNoiseRule);
    els.btnNrPageAdd.addEventListener('click', addNoiseRule);

    // 删除规则（弹窗 / 页面共用）
    function handleNoiseRuleDelete(e) {
        if (e.target.classList.contains('nr-del')) {
            const idx = parseInt(e.target.closest('.nr-item').dataset.idx, 10);
            const rule = noiseRules[idx];
            if (!rule) return;
            if (rule.builtin) {
                showToast('内置规则不能删除，取消它的「启用」即可不再生效', 'warn');
                return;
            }
            if (rule.enabled) {
                showToast('该规则当前为启用状态，请先取消启用再删除', 'warn');
                return;
            }
            noiseRules.splice(idx, 1);
            renderNoiseRulesList();
            applyNoiseRulesFromPanel();
        }
    }
    els.noiseRulesList.addEventListener('click', handleNoiseRuleDelete);
    els.noiseRulesListPage.addEventListener('click', handleNoiseRuleDelete);

    // 恢复默认 / 清空规则（页面视图下可用；弹窗内隐藏）
    els.btnNoiseRuleReset.addEventListener('click', () => resetNoiseRules());
    els.btnNrPageReset.addEventListener('click', () => resetNoiseRules());

    function resetNoiseRules() {
        const msg = noiseRuleScope === 'project'
            ? '确定清空当前项目的规则？清空后将回退到仅使用全局默认。'
            : '确定恢复默认规则？当前未保存的修改将丢失。';
        if (!confirm(msg)) return;
        let url = 'api/noise-rules/reset';
        if (noiseRuleScope === 'project') {
            const pp = currentProjectPath();
            if (pp) url += '?projectPath=' + encodeURIComponent(pp);
        }
        fetch(url, { method: 'POST' })
            .then((r) => r.json()).then((data) => {
                noiseRules = data || [];
                if (noiseRuleScope === 'project') {
                    projectRulesCache = noiseRules.slice();
                    globalOverrides = {};
                } else {
                    globalRulesCache = noiseRules.slice();
                }
                recomputeActiveNoiseRules();
                renderNoiseRulesList();
                refreshAllFilteredViews();
            });
    }

    // 保存生效（弹窗模式保存后关闭；页面模式保持打开）
    function saveNoiseRules() {
        if (noiseRuleScope === 'project') {
            const overrides = collectOverridesFromPanel();
            const customRules = collectNoiseRulesFromPanel();
            fetch(noiseRulesApiUrl(), {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ globalOverrides: overrides, customRules: customRules }),
            }).then((r) => r.json()).then((data) => {
                globalOverrides = data.globalOverrides || {};
                projectRulesCache = data.customRules || [];
                noiseRules = projectRulesCache.slice();
                recomputeActiveNoiseRules();
                if (noiseRuleMode === 'panel') closeNoiseRulesPanel();
                renderNoiseRulesList();
                refreshAllFilteredViews();
                markNoiseConfigured();
                showToast('✓ 项目级过滤规则已保存并生效');
            }).catch(() => alert('保存失败，请检查规则格式'));
            return;
        }
        const rules = collectNoiseRulesFromPanel();
        fetch(noiseRulesApiUrl(), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rules),
        }).then((r) => r.json()).then((data) => {
            noiseRules = data || [];
            if (noiseRuleScope === 'project') projectRulesCache = noiseRules.slice();
            else globalRulesCache = noiseRules.slice();
            recomputeActiveNoiseRules();
            if (noiseRuleMode === 'panel') closeNoiseRulesPanel();
            renderNoiseRulesList();
            refreshAllFilteredViews();
            markNoiseConfigured();
            const scopeLabel = noiseRuleScope === 'project' ? '项目级' : '全局';
            showToast('✓ ' + scopeLabel + '过滤规则已保存并生效');
        }).catch(() => alert('保存失败，请检查规则格式'));
    }

    /** 引导用：记下"用户已经动过过滤规则"，并刷新引导 */
    function markNoiseConfigured() {
        if (window.Guide) Guide.markSeen(Guide.NOISE_CONFIGURED);
        guideRefresh();
    }
    els.btnNoiseRuleSave.addEventListener('click', saveNoiseRules);
    els.btnNrPageSave.addEventListener('click', saveNoiseRules);

    function renderWarnings(result) {
        const items = [];
        if (result.warnings) result.warnings.forEach((w) => items.push('⚠ ' + w));
        if (items.length === 0) {
            els.warnings.hidden = true;
            return;
        }
        els.warnings.innerHTML = items.map((i) => '<div>' + escapeHtml(i) + '</div>').join('');
        els.warnings.hidden = false;
    }

    function nodeEl(node, depth) {
        const wrap = document.createElement('div');
        wrap.className = 'node-wrap';

        const row = document.createElement('div');
        row.className = 'node-row' + (depth === 0 ? ' root' : '');

        const hasKids = node.children && node.children.length > 0;
        const toggle = document.createElement('span');
        toggle.className = 'toggle';
        toggle.textContent = hasKids ? '▸' : '•';

        const method = document.createElement('span');
        method.className = 'method';
        // 分色渲染：全限定类名 / 方法名 / 入参 一眼可分
        appendSigFromString(method, node.method.display);

        const badges = document.createElement('span');
        badges.className = 'row-badges';
        badges.appendChild(badge('source-' + node.source.toLowerCase(),
            SOURCE_LABEL[node.source] || node.source));
        if (node.invokeType) {
            badges.appendChild(badge('invoke', INVOKE_LABEL[node.invokeType] || node.invokeType));
        }
        if (node.line && node.line > 0) {
            const lineNo = document.createElement('span');
            lineNo.className = 'line-no';
            lineNo.textContent = 'L' + node.line;
            badges.appendChild(lineNo);
        }
        if (node.cycle) badges.appendChild(badge('cycle', '♻ 环'));
        if (node.truncated) badges.appendChild(badge('truncated', '✂ 截断'));

        row.appendChild(toggle);
        row.appendChild(method);
        row.appendChild(badges);
        applyNodeMark(node, row);

        // 每个方法行一个搜索按钮：查它的调用链里是否调用了某方法
        const searchBtn = document.createElement('button');
        searchBtn.type = 'button';
        searchBtn.className = 'row-search-btn';
        searchBtn.title = '搜索此方法的调用链是否调用了某方法（模糊/精确）';
        searchBtn.textContent = '🔎';
        searchBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleSearchBar(node, wrap);
        });
        row.appendChild(searchBtn);

        wrap.appendChild(row);

        const entry = { rowEl: row, setExpanded: null };
        nodeRegistry.set(node, entry);

        if (hasKids) {
            const kids = document.createElement('div');
            kids.className = 'children';
            kids.style.display = 'none';
            let rendered = false;
            const setExpanded = (show) => {
                if (show && !rendered) {
                    // 懒渲染：首次展开时才构建子 DOM（大树不卡顿）
                    node.children.forEach((c) => kids.appendChild(nodeEl(c, depth + 1)));
                    rendered = true;
                }
                kids.style.display = show ? '' : 'none';
                toggle.textContent = show ? '▾' : '▸';
            };
            entry.setExpanded = setExpanded;
            expandFns.push(setExpanded);
            // 展开时：命中路径若无分叉则一路穿透，遇分叉只展开这一层（逐层引导）
            row.addEventListener('click', () => {
                if (kids.style.display === 'none') expandGuided(node, setExpanded);
                else setExpanded(false);
            });
            wrap.appendChild(kids);
        } else {
            row.addEventListener('click', () => { /* 叶子/环节点无可展开 */ });
        }
        return wrap;
    }

    function badge(cls, text) {
        const b = document.createElement('span');
        b.className = 'badge ' + cls;
        b.textContent = text;
        return b;
    }

    /** 返回 HTML 字符串形式的徽标（用于 innerHTML 拼接，避免 DOM 对象被拼成 "[object...]"） */
    function badgeHtml(cls, text) {
        return '<span class="badge ' + cls + '">' + escapeHtml(text) + '</span>';
    }

    function escapeHtml(s) {
        return s.replace(/[&<>"']/g, (c) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
        }[c]));
    }

    // ------------------------------------------------------------------
    // 调用链搜索：每个方法行的 🔎，搜其子树是否调用了某方法
    // ------------------------------------------------------------------

    function matchNode(n, term, exact) {
        const m = n.method || {};
        const q = term.trim();
        if (exact) {
            // 精确：方法名 / display / 全限定 类.方法 / 简单类名.方法
            return m.name === q
                || m.display === q
                || (m.className || '') + '.' + m.name === q
                || (m.simpleClassName || '') + '.' + m.name === q;
        }
        // 模糊：包含即命中（忽略大小写）
        const needle = q.toLowerCase();
        return [m.name, m.display, m.className, m.simpleClassName]
            .some((v) => v && v.toLowerCase().indexOf(needle) >= 0);
    }

    /** 收集 startNode 子树内所有命中节点及展开路径（不含 startNode 自身） */
    function collectMatches(startNode, term, exact) {
        const results = [];
        (function walk(n, ancestors) {
            (n.children || []).forEach((c) => {
                if (matchNode(c, term, exact)) results.push({ node: c, ancestors });
                walk(c, ancestors.concat([c]));
            });
        })(startNode, []);
        return results;
    }

    function clearSearchHits() {
        hitRows.forEach((r) => r.classList.remove('search-hit'));
        hitRows = [];
    }

    function closeSearchBar() {
        if (activeSearch) {
            activeSearch.bar.remove();
            activeSearch = null;
        }
    }

    /** 展开命中节点的祖先路径（懒渲染同步注册子节点）并高亮命中行 */
    function focusMatches(root, matches) {
        matches.forEach((mt) => {
            [root].concat(mt.ancestors).forEach((anc) => {
                const reg = nodeRegistry.get(anc);
                if (reg && reg.setExpanded) reg.setExpanded(true);
            });
            const reg = nodeRegistry.get(mt.node);
            if (reg && reg.rowEl) {
                reg.rowEl.classList.add('search-hit');
                hitRows.push(reg.rowEl);
            }
        });
        if (hitRows[0] && hitRows[0].scrollIntoView) {
            hitRows[0].scrollIntoView({ block: 'center', behavior: 'smooth' });
        }
    }

    function toggleSearchBar(node, wrap) {
        if (activeSearch && activeSearch.node === node) { closeSearchBar(); return; }
        closeSearchBar();
        clearSearchHits();

        const row = nodeRegistry.get(node).rowEl;

        const bar = document.createElement('div');
        bar.className = 'node-search-bar';

        const input = document.createElement('input');
        input.type = 'text';
        input.placeholder = '方法名 / 类名，如 buildRoots 或 ClassReader';

        const mode = document.createElement('select');
        const optFuzzy = document.createElement('option');
        optFuzzy.value = 'fuzzy'; optFuzzy.textContent = '模糊';
        const optExact = document.createElement('option');
        optExact.value = 'exact'; optExact.textContent = '精确';
        mode.appendChild(optFuzzy);
        mode.appendChild(optExact);

        const btnGo = document.createElement('button');
        btnGo.type = 'button';
        btnGo.className = 'btn small';
        btnGo.textContent = '搜索';

        const btnClose = document.createElement('button');
        btnClose.type = 'button';
        btnClose.className = 'btn small';
        btnClose.textContent = '✕';
        btnClose.title = '关闭';

        const result = document.createElement('span');
        result.className = 'search-result';

        bar.appendChild(input);
        bar.appendChild(mode);
        bar.appendChild(btnGo);
        bar.appendChild(btnClose);
        bar.appendChild(result);

        wrap.insertBefore(bar, row.nextSibling);

        const run = () => {
            const term = input.value.trim();
            clearSearchHits();
            result.textContent = '';
            result.className = 'search-result';
            if (!term) return;
            const exact = mode.value === 'exact';
            const matches = collectMatches(node, term, exact);
            if (matches.length === 0) {
                result.className = 'search-result err';
                result.textContent = '未命中 —— ' + (node.method ? node.method.display : '')
                    + ' 的调用链没有调用 "' + term + '"';
                return;
            }
            matches.forEach((mt) => {
                // 自上而下展开祖先路径（懒渲染同步注册子节点），再高亮命中行
                mt.ancestors.forEach((anc) => {
                    const reg = nodeRegistry.get(anc);
                    if (reg && reg.setExpanded) reg.setExpanded(true);
                });
                const reg = nodeRegistry.get(mt.node);
                if (reg && reg.rowEl) {
                    reg.rowEl.classList.add('search-hit');
                    hitRows.push(reg.rowEl);
                }
            });
            result.className = 'search-result ok';
            result.textContent = '命中 ' + matches.length + ' 个方法';
            if (hitRows[0] && hitRows[0].scrollIntoView) {
                hitRows[0].scrollIntoView({ block: 'center', behavior: 'smooth' });
            }
        };

        btnGo.addEventListener('click', run);
        input.addEventListener('keydown', (e) => { if (e.key === 'Enter') run(); });
        btnClose.addEventListener('click', closeSearchBar);

        activeSearch = { bar, node };
        input.focus();
    }

    // ------------------------------------------------------------------
    // 全局搜索：搜所有入口方法的调用链是否调用了某方法（类分析多入口场景）
    // ------------------------------------------------------------------

    function resetGlobalSearch() {
        els.globalSearchInput.value = '';
        els.globalSearchResult.textContent = '';
        els.globalSearchResult.className = 'search-result';
        els.globalSearchChips.innerHTML = '';
    }

    function runGlobalSearch() {
        const term = els.globalSearchInput.value.trim();
        clearSearchHits();
        closeSearchBar();
        els.globalSearchChips.innerHTML = '';
        els.globalSearchResult.textContent = '';
        els.globalSearchResult.className = 'search-result';
        if (!term || !currentResult) return;

        const exact = els.globalSearchMode.value === 'exact';

        // 每个入口：入口自身 + 其子树内全部命中
        const roots = rootsOf(currentResult);
        const perRoot = [];
        roots.forEach((root) => {
            const matches = [];
            if (matchNode(root, term, exact)) matches.push({ node: root, ancestors: [] });
            collectMatches(root, term, exact).forEach((m) => matches.push(m));
            if (matches.length > 0) perRoot.push({ root, matches });
        });

        const total = perRoot.reduce((s, p) => s + p.matches.length, 0);
        const entryCount = roots.length;

        if (perRoot.length === 0) {
            els.globalSearchResult.className = 'search-result err';
            els.globalSearchResult.textContent =
                '未命中 —— ' + entryCount + ' 个入口方法的调用链均没有调用 "' + term + '"';
            return;
        }

        els.globalSearchResult.className = 'search-result ok';
        els.globalSearchResult.textContent = '命中 ' + perRoot.length + ' / '
            + entryCount + ' 个入口 · 共 ' + total + ' 处调用（点击入口名查看）';

        // 每个命中入口一个 chip：点击展开该入口全部命中并高亮
        perRoot.forEach((p) => {
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'search-chip';
            chip.innerHTML = '<span class="chip-name"></span>'
                + '<span class="chip-count">' + p.matches.length + '</span>';
            appendSigFromString(chip.querySelector('.chip-name'),
                p.root.method ? p.root.method.display : '');
            chip.title = '定位 ' + (p.root.method ? p.root.method.display : '') + ' 的 '
                + p.matches.length + ' 处命中';
            chip.addEventListener('click', () => {
                clearSearchHits();
                focusMatches(p.root, p.matches);
            });
            els.globalSearchChips.appendChild(chip);
        });
    }

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

    els.btnExpandAll.addEventListener('click', () => {
        showLoading('正在展开全部节点……');
        setTimeout(() => {
            expandFns.forEach((f) => f(true));
            hideLoading();
        }, 20);
    });

    els.btnCollapseAll.addEventListener('click', () => {
        expandFns.forEach((f) => f(false));
    });

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
            // 已完成：恢复 gitProjectPath，用户可以直接进入分析
            gitProjectPath = st.projectPath;
            sourceMode = 'git';
            renderGitStatus({
                status: 'DONE',
                message: (st.message || '✓ 项目已导入') + ' —— 请从项目列表中点击"进入分析"'
            });
            refreshProjectList();
        } else if (st.status === 'FAILED') {
            renderGitStatus(st);
        } else {
            // 在途中（PENDING/CLONING），继续轮询
            gitPollTimer = setInterval(() => pollGitStatus(st.jobId), 2000);
            renderGitStatus(st);
            els.btnGitPrepare.disabled = true;
        }
    });

    // ==================================================================
    // Step 2: 已确认的交易入口清单 —— 渲染 + 操作
    // ==================================================================

    // 后端 JSON 返回的是普通 Object，没有 Java 里的 key() 方法
    function entryKey(item) {
        return item.className + '#' + (item.methodName || '') + '#' + (item.descriptor || '');
    }

    function renderEntryList() {
        if (!currentEntryList) currentEntryList = { confirmed: [], excluded: [] };
        const confirmed = currentEntryList.confirmed || [];
        const excluded = currentEntryList.excluded || [];

        // 统计
        els.entryListStats.textContent = confirmed.length + ' 个已确认'
            + (excluded.length > 0 ? ' · ' + excluded.length + ' 个已排除' : '');
        els.batchAnalyzeStats.textContent = confirmed.length + ' 个入口待分析';

        // confirmed 主体
        if (confirmed.length === 0) {
            els.entryConfirmedList.innerHTML =
                '<div class="entry-empty">还没有已确认的入口 → 点上面的「🔍 补充扫描」或「➕ 手动添加」来建清单</div>';
        } else {
            els.entryConfirmedList.innerHTML = confirmed.map((item, i) => renderEntryRow(item, 'confirmed', i + 1)).join('');
        }

        // excluded 折叠区
        if (excluded.length > 0) {
            els.entryExcludedDetails.hidden = false;
            els.entryExcludedCount.textContent = '(' + excluded.length + ')';
            els.entryExcludedList.innerHTML = excluded.map((item, i) => renderEntryRow(item, 'excluded', i + 1)).join('');
        } else {
            els.entryExcludedDetails.hidden = true;
        }
        updateEntryToolbar();
        // 清单为空时给一次性的就地提示（只出现一次）
        if (confirmed.length === 0 && currentProjectId && window.Guide) {
            Guide.tipOnce('empty-entries', els.entryConfirmedList,
                '清单是分析的输入：「🔍 自动扫描加入清单」会按扫描策略自动识别 Controller / Job 等入口，识别不准的可以「➕ 手动添加」。');
        }
        guideRefresh();
    }

    /** 渲染清单行；num 为纯展示序号（1、2、3…），不与方法绑定 */
    function renderEntryRow(item, mode, num) {
        const fullCls = item.className || '';
        const method = item.methodName || '';
        const desc = item.descriptor || '';
        const groupBadge = item.group ? `<span class="entry-badge group-${item.group}">${item.group}</span>` : '';
        const sourceBadge = item.source === 'MANUAL'
            ? '<span class="entry-badge group-MANUAL">手动</span>'
            : '';

        // 完整签名（统一可读格式）：全限定类名#方法名(参数类型短名列表)
        const fullSig = readableFullSig(fullCls, method, desc) || fullCls;

        if (mode === 'excluded') {
            const reason = item.excludeReason;
            return `<div class="entry-ex-row">
                <div class="entry-row" data-key="${entryKey(item)}">
                    <span class="entry-idx" title="序号">${num}</span>
                    ${sourceBadge}${groupBadge}
                    <span class="entry-sig" title="${escapeHtml(fullSig)}">${sigHtmlFromString(fullSig)}</span>
                    <button class="entry-restore-btn" onclick="restoreEntry('${entryKey(item).replace(/'/g, "\\'")}')">恢复</button>
                </div>
                ${reason ? `<div class="entry-reason" title="${escapeHtml(reason)}">排除原因：${escapeHtml(reason)}</div>` : ''}
            </div>`;
        }
        const checked = entrySelKeys.has(entryKey(item)) ? ' checked' : '';
        return `<div class="entry-row${checked ? ' selected' : ''}" data-key="${entryKey(item)}">
            <span class="entry-idx" title="序号">${num}</span>
            <input type="checkbox" class="entry-cb"${checked}>
            ${sourceBadge}${groupBadge}
            <span class="entry-sig" title="${escapeHtml(fullSig)}">${sigHtmlFromString(fullSig)}</span>
            <button class="entry-exclude-btn" onclick="excludeEntry('${entryKey(item).replace(/'/g, "\\'")}')">排除</button>
        </div>`;
    }

    /** 刷新清单顶部操作栏：全选态 / 已选数量 / 批量排除按钮可用性 */
    function updateEntryToolbar() {
        const confirmed = (currentEntryList && currentEntryList.confirmed) || [];
        const has = confirmed.length > 0;
        els.entryConfirmToolbar.hidden = !has;
        if (!has) { entrySelKeys.clear(); return; }
        const sel = confirmed.filter(i => entrySelKeys.has(entryKey(i))).length;
        els.entrySelectedCount.textContent = sel > 0 ? '已选 ' + sel + ' 个' : '';
        els.batchExcludeCount.textContent = sel > 0 ? ' (' + sel + ')' : '';
        els.btnBatchExclude.disabled = sel === 0;
        els.entryCheckAll.checked = sel > 0 && sel === confirmed.length;
        els.entryCheckAll.indeterminate = sel > 0 && sel < confirmed.length;
    }

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
            saveUrl: page ? '/api/scan-strategy/global' : '/api/scan-strategy/project/' + currentProjectId,
            resetUrl: page ? '/api/scan-strategy/global/reset' : '/api/scan-strategy/project/' + currentProjectId + '/reset',
        };
    }

    /** 从后端加载当前项目的扫描策略并渲染下拉 */
    async function loadScanStrategy() {
        if (!currentProjectId) return;
        try {
            scanStrategy = await fetchJson('/api/scan-strategy/project/' + currentProjectId);
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
        if (!currentProjectId || !scanStrategy) return;
        const newId = els.scanProfileSelect.value;
        try {
            const fresh = await fetchJson('/api/scan-strategy/project/' + currentProjectId);
            fresh.activeProfileId = newId;
            await putJson('/api/scan-strategy/project/' + currentProjectId, fresh);
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
        if (!currentProjectId) { showError('请先进入项目'); return; }
        clearError();
        const profile = scanStrategy && scanStrategy.profiles
            ? scanStrategy.profiles.find(p => p.id === (scanStrategy.activeProfileId || 'builtin-standard'))
            : null;
        const profileLabel = profile ? profile.name : '标准扫描';
        showLoading('正在按「' + profileLabel + '」扫描交易入口，需解析项目字节码，请稍候…');
        try {
            const resp = await postJson(
                '/api/projects/' + currentProjectId + '/entries/scan',
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

    function parseEntryString(raw) {
        // 支持: com.demo.OrderController
        //       com.demo.OrderController#createOrder
        //       com.demo.OrderController#createOrder(Order)          ← 可读精确重载
        //       com.demo.OrderController#createOrder(Lcom/demo/Order;)V  ← 兼容旧 JVM 描述符
        const hashIdx = raw.indexOf('#');
        if (hashIdx < 0) {
            return { className: raw, methodName: null, descriptor: '' };
        }
        const cls = raw.substring(0, hashIdx);
        const afterHash = raw.substring(hashIdx + 1);
        const parenIdx = afterHash.indexOf('(');
        if (parenIdx < 0) {
            return { className: cls, methodName: afterHash, descriptor: '' };
        }
        const method = afterHash.substring(0, parenIdx);
        // 找最后一个 )
        const closeParen = afterHash.lastIndexOf(')');
        if (closeParen >= 0) {
            const body = afterHash.substring(parenIdx + 1, closeParen);
            // 已经是 JVM descriptor 原样保留，否则把可读参数列表转成 descriptor
            const descriptor = looksLikeDescriptor(body) ? '(' + body + ')' : readableParamsToDescriptor(body);
            return { className: cls, methodName: method, descriptor };
        }
        return { className: cls, methodName: method, descriptor: '' };
    }

    // 🔎 扫描按钮：调用 scan-manual，渲染扫描结果列表
    els.addEntryVerify.addEventListener('click', async () => {
        if (!currentProjectId) { showError('请先进入项目'); return; }
        const input = readAddEntryInput();
        if (!input.className) { setVerifyStatus('请先填类名', 'err'); return; }

        els.addEntryVerify.disabled = true;
        els.addEntryScanWrap.hidden = false;
        els.addEntryScanList.innerHTML = '<div class="scan-result-empty">扫描中...</div>';
        els.addEntryScanStats.textContent = '扫描中...';
        setVerifyStatus('扫描中...', '');
        currentScanCandidates = [];
        try {
            const resp = await postJson('/api/projects/' + currentProjectId + '/entries/scan-manual', {
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
        if (!currentProjectId) { showError('请先进入项目'); closeAddEntryModal(); return; }
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
            const resp = await postJson('/api/projects/' + currentProjectId + '/entries/add-batch', checkedItems);
            closeAddEntryModal();
            await autoLoadEntryList(currentProjectId);
            let msg = '✓ ' + resp.added + ' 个添加成功';
            if (resp.existed > 0) msg += '，' + resp.existed + ' 个已存在（自动跳过）';
            showError(msg, true);
        } catch (e) {
            showError('添加失败: ' + e.message);
        }
    });

    // 排除（confirmed → excluded）：单条入口也走统一弹窗，便于选原因
    window.excludeEntry = function (key) {
        if (!currentProjectId) return;
        const items = ((currentEntryList && currentEntryList.confirmed) || [])
            .filter(it => entryKey(it) === key);
        if (items.length === 0) return;
        openExcludeModal(items);
    };

    // 恢复（excluded → confirmed）
    window.restoreEntry = function (key) {
        if (!currentProjectId) return;
        postJson('/api/projects/' + currentProjectId + '/entries/restore', { key: key })
            .then(() => autoLoadEntryList(currentProjectId))
            .catch(e => showError('恢复失败: ' + e.message));
    };

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
        if (e.target.checked) entrySelKeys.add(key); else entrySelKeys.delete(key);
        row.classList.toggle('selected', e.target.checked);
        updateEntryToolbar();
    });

    /** 全选 / 全不选 */
    els.entryCheckAll.addEventListener('change', () => {
        const confirmed = (currentEntryList && currentEntryList.confirmed) || [];
        const checked = els.entryCheckAll.checked;
        confirmed.forEach(it => {
            if (checked) entrySelKeys.add(entryKey(it)); else entrySelKeys.delete(entryKey(it));
        });
        els.entryConfirmedList.querySelectorAll('.entry-row').forEach(row => {
            const cb = row.querySelector('.entry-cb');
            if (cb) { cb.checked = checked; row.classList.toggle('selected', checked); }
        });
        updateEntryToolbar();
    });

    /** 打开批量排除弹窗 */
    els.btnBatchExclude.addEventListener('click', () => {
        const confirmed = (currentEntryList && currentEntryList.confirmed) || [];
        const items = confirmed.filter(it => entrySelKeys.has(entryKey(it)));
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
        if (!currentProjectId || excludeModalItems.length === 0) return;
        const unified = els.excludeReasonUnified.checked;
        const perItemInputs = els.excludePerItemReasons.querySelectorAll('.ex-reason-item input');
        const payload = excludeModalItems.map((item, idx) => {
            const raw = unified ? els.excludeReasonText.value : (perItemInputs[idx] ? perItemInputs[idx].value : '');
            return { key: entryKey(item), reason: (raw || '').trim() || '用户排除' };
        });
        els.excludeModalConfirm.disabled = true;
        try {
            const resp = await postJson('/api/projects/' + currentProjectId + '/entries/exclude/batch', payload);
            closeExcludeModal();
            entrySelKeys.clear();
            await autoLoadEntryList(currentProjectId);
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
        if (!currentProjectId) return;
        const confirmed = (currentEntryList && currentEntryList.confirmed) || [];
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
            currentRequest = req;
            await autoLoadCacheForProject(currentProjectId);
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
        const confirmed = (currentEntryList && currentEntryList.confirmed) || [];
        return {
            view: currentView,
            projectCount: guideProjectCount,
            currentProjectId: currentProjectId,
            entryCount: confirmed.length,
            hasResult: guideHasResult,
            resultStale: guideResultStale,
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
                if (!currentProjectId) { switchView('projects'); break; }
                switchView('analyze');
                scrollTo(els.btnEntryScan);
                break;
            case 'analyze':
            case 'reanalyze':
                if (!currentProjectId) { switchView('projects'); break; }
                switchView('analyze');
                scrollTo(els.btnBatchAnalyze);
                break;
            case 'noiseRules':
                if (currentProjectId) openNoiseRulesPanel();
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
