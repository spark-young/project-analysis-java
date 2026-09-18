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
        getNodeRegistry: () => nodeRegistry,     // C5 const Map，C6 清空/读、C5 写（身份恒定）
        getSourceLabel: () => SOURCE_LABEL,      // C5 常量表（C5 nodeEl 共用，单一来源）
        clearExpandFns: () => { expandFns.length = 0; },   // C5 数组，C6 仅就地截断
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

    /** 引导用：用户是否配置过过滤规则 */
    function guideNoiseConfigured() {
        if (window.Guide && Guide.isSeen(Guide.NOISE_CONFIGURED)) return true;
        if (projectRulesCache && projectRulesCache.length > 0) return true;
        return !!(globalOverrides && Object.keys(globalOverrides).length > 0);
    }
    // entrySelKeys 已迁入 App.state（OPT-27 C4：C4 渲染读 + C8 桥接绑定写，清零归属见 state.js）
    let excludeModalItems = [];    // 批量排除弹窗当前承载的条目（C8 弹窗状态，本簇外零引用，随 C8 外搬）
    let expandFns = [];      // 全部展开/收起用
    let searchTimer = null;
    const nodeRegistry = new Map();  // 数据节点 → { rowEl, setExpanded }
    let hitRows = [];                // 当前搜索高亮的行
    let activeSearch = null;         // 当前打开的行内搜索栏 { bar, node }
    // freqFilter / batchModel / batchRowStates 已迁入 App.state（OPT-27 C6：batch.js
    // 读写、C5 统计簇与 entries.js hook 活读，清零/重建归属见 js/state.js 头注释）；
    // freqViewMode / projSearchMarks / projSearchOrder / projSearchCursor 为 batch.js
    // 模块私有（grep 确认本簇外零引用，不进 App.state）。
    let noiseRules = [];             // 过滤规则编辑缓冲区（弹窗/页面正在展示的那一层）
    let globalRulesCache = [];       // 全局层规则缓存（参与合并过滤）
    let projectRulesCache = [];      // 项目层自定义规则缓存（参与合并过滤）
    let globalOverrides = {};        // 项目级全局规则覆盖：{ ruleId: true/false }
    let activeNoiseRules = [];       // 实际生效的过滤规则集 = 全局层（套覆盖）+ 项目自定义层
    let compiledActiveRules = [];    // 启用中规则的预编译结果（正则只编译一次，判定只做 test）
    let activeNoiseHash = '';        // 生效规则集指纹（规则变更时算一次，后续直接比对）
    let noiseRuleScope = 'global';   // 当前查看/编辑的层级：'global' | 'project'
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
    els.navToNoiseRules.addEventListener('click', () => {
        App.state.noiseRuleMode = 'page';
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
        App.state.noiseRuleMode = 'panel';
        if (App.state.currentProjectId) {
            switchView('analyze');
        } else {
            switchView('projects');
        }
    });

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
    // 结果渲染
    // ------------------------------------------------------------------

    const SOURCE_LABEL = { PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' };
    const INVOKE_LABEL = {
        VIRTUAL: '虚调用', STATIC: '静态', INTERFACE: '接口',
        SPECIAL: '构造/super', DYNAMIC: 'lambda', IMPL: '接口实现分派',
    };

    function renderResult(result) {
        App.state.guideHasResult = true;
        App.state.guideResultStale = false;
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


    // ------------------------------------------------------------------
    // "可读参数 → JVM 描述符"与入口串解析：逻辑已统一到共享模块 Sig（sig.js）。
    // 调用点见 parseEntryString（下方委托 Sig.parseEntryString）。
    // ------------------------------------------------------------------


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
            (gm.owner || '').replace(/\//g, '.'), gm.name || '', Sig.paramCountFromDescriptor(gm.descriptor));
        noiseMemo.set(gm, v);
        return v;
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
        if (Array.isArray(App.state.batchRowStates)) {
            App.state.batchRowStates.forEach((st) => {
                if (!st || !st.result || !st.rendered || !st.body) return;
                st.body.innerHTML = '';
                st.roots = rootsOf(st.result, st.idx);
                st.roots.forEach((root) => st.body.appendChild(nodeEl(root, 0)));
            });
        }

        // 单入口视图（非批量清单）：整树重绘
        if (!inBatchList && els.tree && els.tree.children.length > 0 && App.state.currentResult) {
            clearSearchHits();
            els.tree.innerHTML = '';
            rootsOf(App.state.currentResult).forEach((root) => els.tree.appendChild(nodeEl(root, 0)));
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
        App.state.currentExcelMode = 'entry';
        els.btnExcel.title = '导出当前入口的 Excel 报告';
        els.btnExpandAll.style.display = '';
        els.btnCollapseAll.style.display = '';
        els.legend.style.display = '';
        els.globalSearch.style.display = '';
        // 批量模式下显示「返回清单」
        if (fileName && App.state.currentBatchSummary) {
            els.btnBackToList.hidden = false;
        } else {
            els.btnBackToList.hidden = true;
        }
    }

    /** 回到批量清单视图 */
    function backToBatchList() {
        if (!App.state.currentBatchSummary) return;
        App.state.currentResult = App.state.currentBatchSummary;
        App.state.currentCacheFileName = null;
        renderBatchSummary(App.state.currentBatchSummary);
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
        if (!(App.state.batchModel && App.state.batchModel.loaded && App.state.batchModel.entries)) return batch.stats || {};
        const agg = { entryCount: 0, totalNodes: 0, projectMethods: 0, dependencyMethods: 0, externalMethods: 0, truncated: false, durationMs: 0 };
        let counted = 0;
        App.state.batchModel.entries.forEach((slot) => {
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
        if (!App.state.currentResult) return;
        if (els.tree && els.tree.querySelector('.bt-row')) {
            App.state.batchRowStates.forEach((st) => {
                if (st && st.rendered && st.result) updateBatchRowStats(st);
            });
            return;
        }
        renderStats(computeFilteredStats(App.state.currentResult));
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
    // 频次行操作：导出调用位置 / 加入过滤规则
    // ------------------------------------------------------------------


    /** 正则元字符转义：把方法名/类名当字面量匹配 */
    function escapeRegex(s) {
        return String(s == null ? '' : s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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
        if (App.state.currentProjectId && pp) {
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


    /** 判断方法是否命中任一启用的样板规则（按条目对象记忆化，避免重复解析签名） */
    function isNoiseMethod(m) {
        if (!m || !m.method) return false;
        const hit = freqNoiseMemo.get(m);
        if (hit !== undefined) return hit;
        const src = m.source || 'EXTERNAL';
        // 签名拆解统一走 Sig（原内联解析与 splitMethodSignature / 后端 parseMethodSignature 重复）
        const p = Sig.splitSignature(m.method);
        const v = matchCompiledRules(src, p.className, p.methodName, p.paramCount);
        freqNoiseMemo.set(m, v);
        return v;
    }


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
        if (App.state.currentProjectId && currentProjectPath()) {
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
        // 预编译统一走 Sig（与后端 NoiseRuleService.isNoiseOnRules 同口径，见 sig.js）
        compiledActiveRules = Sig.compileRules(activeNoiseRules);
        activeNoiseHash = enabled.map((r) => (r.source || 'ALL') + '~' + (r.methodPattern || '')
            + '~' + (r.classPattern || '') + '~' + (r.paramCount != null ? r.paramCount : '')).join('|');
        // 规则已变，丢弃旧的判定记忆
        noiseMemo = new WeakMap();
        freqNoiseMemo = new WeakMap();
    }

    /** 按预编译规则判定噪声（gm 字段通道与签名通道共用同一套语义；匹配逻辑在 Sig，见 sig.js） */
    function matchCompiledRules(src, className, methodName, paramCount) {
        return Sig.matchCompiledRules(compiledActiveRules, src, className, methodName, paramCount);
    }

    /** 从磁盘同步两层规则：更新两层缓存 + 编辑缓冲区指向当前 scope 层，并重绘规则列表 */
    function loadNoiseRules() {
        const pp = currentProjectPath();
        const gReq = fetch('api/noise-rules').then((r) => r.json()).catch(() => []);
        const pReq = (App.state.currentProjectId && pp)
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
        App.state.noiseRuleMode = 'panel';
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
        const isGlobalPanel = App.state.noiseRuleMode === 'panel' && noiseRuleScope === 'global';
        els.btnNoiseRuleAdd.hidden = isGlobalPanel;
        els.btnNoiseRuleReset.hidden = App.state.noiseRuleMode === 'panel';
        els.btnNoiseRuleImport.hidden = isGlobalPanel;
    }

    function renderNoiseRulesList() {
        const container = App.state.noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
        const isGlobalPanel = App.state.noiseRuleMode === 'panel' && noiseRuleScope === 'global';

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
        const container = App.state.noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
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
        const container = App.state.noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
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
        if (App.state.noiseRuleMode === 'panel' && noiseRuleScope === 'global') return;
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
        const container = App.state.noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
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
                if (App.state.noiseRuleMode === 'panel') closeNoiseRulesPanel();
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
            if (App.state.noiseRuleMode === 'panel') closeNoiseRulesPanel();
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

    // badge / badgeHtml / escapeHtml 已抽出 js/ui.js（OPT-27 C2），见顶部薄委托。

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
        if (!term || !App.state.currentResult) return;

        const exact = els.globalSearchMode.value === 'exact';

        // 每个入口：入口自身 + 其子树内全部命中
        const roots = rootsOf(App.state.currentResult);
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
