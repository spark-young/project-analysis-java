/*
 * ui.js —— 基础 UI 层（OPT-27 C2，从 app.js **逐行搬运**，零行为修改）
 * ==================================================
 *
 * 拥有：$ / els（DOM 缓存，加载时构建一次）/ switchView / showError / clearError /
 *       showToast / showConfirm / settleConfirm（含确认弹窗的按钮点击与 Escape 绑定，
 *       原 app.js:718-724 随函数一同搬入，纯监听注册、时序无行为影响）/
 *       loading 系列（resetLoadingPanel / showLoading / loadingSetStep /
 *       loadingSetProgress / hideLoading）/ badge / badgeHtml / escapeHtml（单一来源）。
 *
 * 共享状态：switchView 读写 App.state.currentView / App.state.noiseRuleMode（js/state.js）；
 *           guideRefresh 仍归 app.js 所有，经 Ui.init({ guideRefresh }) 注入（单一来源）。
 * 时序：本文件由 index.html 在 app.js 之前、</body> 前同步加载——els 构建时机与
 *       原先（app.js IIFE 执行期）完全一致：脚本上方 DOM 已解析完毕。
 */
(function (root, factory) {
    'use strict';
    var Ui = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Ui;                  // Node
    }
    if (root) {
        root.Ui = Ui;                         // 浏览器（app.js 之前加载，state.js 先于本文件）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // ---- 依赖注入（方案 §4.3）：app.js 启动时调用 Ui.init({ guideRefresh }) ----
    var hooks = {
        guideRefresh: function () { /* app.js 未 init 前的兜底：与原实现同样静默跳过 */ }
    };
    function init(h) {
        if (h && h.guideRefresh) hooks.guideRefresh = h.guideRefresh;
    }

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

    // ==================================================================
    // 视图切换（原 app.js:330-343 逐行搬运；仅 3 个标识符改走共享通道：
    //   noiseRuleMode/currentView → App.state，guideRefresh → hooks 注入）
    // ==================================================================

    function switchView(to) {
        const showProjects = to === 'projects';
        const showAnalyze = to === 'analyze';
        const showNoiseRules = to === 'noiseRules';
        if (!showNoiseRules) App.state.noiseRuleMode = 'panel';
        els.viewProjects.hidden = !showProjects;
        els.viewAnalyze.hidden = !showAnalyze;
        els.viewNoiseRules.hidden = !showNoiseRules;
        els.navToProjects.classList.toggle('active', showProjects);
        els.navToAnalyze.classList.toggle('active', showAnalyze);
        els.navToNoiseRules.classList.toggle('active', showNoiseRules);
        App.state.currentView = to;
        hooks.guideRefresh();
    }

    // ==================================================================
    // 顶部错误条 / toast / 确认弹窗（原 app.js:670-724 逐行搬运）
    // ==================================================================

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

    // ==================================================================
    // 徽标与 HTML 转义（原 app.js:3433-3449 逐行搬运；escapeHtml 保持单一来源）
    // ==================================================================

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

    return {
        init: init,
        $: $,
        els: els,
        switchView: switchView,
        showError: showError,
        clearError: clearError,
        showToast: showToast,
        showConfirm: showConfirm,
        settleConfirm: settleConfirm,
        showLoading: showLoading,
        loadingSetStep: loadingSetStep,
        hideLoading: hideLoading,
        badge: badge,
        badgeHtml: badgeHtml,
        escapeHtml: escapeHtml
    };
});
