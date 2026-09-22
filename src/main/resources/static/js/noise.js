/*
 * noise.js —— 噪声过滤规则面板与管理（OPT-27 C7 簇）
 * ================================================================================
 * 纯搬运自 app.js 原「样板方法过滤规则管理」各段（C7 函数 + 事件绑定，行号 160-931），
 * 函数体逐行照搬（模板字符串原样保留），仅机械适配（零行为变化）：
 *   1. 跨簇共享状态 → App.state.*（currentProjectId/batchRowStates/currentResult
 *      /noiseRuleMode 为 C2/C3/C5 已迁字段）
 *   2. 调用已搬簇函数 → 命名空间访问（Ui.* / Batch.* / ResultView.* / Projects.*）
 *   3. 调用未搬簇函数 → init(hooks) 晚绑定：guideRefresh（app.js 引导，未搬）
 * 模块私有状态（有意不进 App.state，依据见 js/state.js 头注释）：
 *   noiseRules / globalRulesCache / projectRulesCache / globalOverrides /
 *   activeNoiseRules / compiledActiveRules / activeNoiseHash / noiseRuleScope /
 *   noiseMemo / freqNoiseMemo / _lastAppliedRulesHash
 *   （grep 确认本簇外零引用，app.js 通过 getter 活读 projectRulesCache/globalOverrides）。
 */
(function (root, factory) {
    'use strict';
    const Noise = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Noise;               // Node（契约测试可 require）
    }
    if (root) {
        root.Noise = Noise;                   // 浏览器（result.js 之后、app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // 基础 UI
    const els = Ui.els;
    const escapeHtml = Ui.escapeHtml;
    const showToast = Ui.showToast;
    const showConfirm = Ui.showConfirm;
    const switchView = Ui.switchView;

    // C3 项目模块
    const currentProjectPath = Projects.currentProjectPath;

    // C6 批量模块
    const splitMethodSignature = Batch.splitMethodSignature;
    const refreshFreqView = Batch.refreshFreqView;

    // C5 结果树模块
    const reapplyFilterToTree = ResultView.reapplyFilterToTree;
    const updateBatchRowStats = ResultView.updateBatchRowStats;
    const renderStats = ResultView.renderStats;
    const computeFilteredStats = ResultView.computeFilteredStats;

    // 晚绑定钩子：app.js 在 init 时注入（引导刷新等未搬簇函数）
    const _hooks = {
        guideRefresh: null,
    };

    // ================================================================
    // 模块私有状态（原 app.js 行 160-167 / 296-297 / 328）
    // ================================================================
    let noiseRules = [];             // 过滤规则编辑缓冲区（弹窗/页面正在展示的那一层）
    let globalRulesCache = [];       // 全局层规则缓存（参与合并过滤）
    let projectRulesCache = [];      // 项目层自定义规则缓存（参与合并过滤）
    let globalOverrides = {};        // 项目级全局规则覆盖：{ ruleId: true/false }
    let activeNoiseRules = [];       // 实际生效的过滤规则集 = 全局层（套覆盖）+ 项目自定义层
    let compiledActiveRules = [];    // 启用中规则的预编译结果（正则只编译一次，判定只做 test）
    let activeNoiseHash = '';        // 生效规则集指纹（规则变更时算一次，后续直接比对）
    let noiseRuleScope = 'global';   // 当前查看/编辑的层级：'global' | 'project'

    // noise 判定记忆：同一对象只判定一次（规则变更时由 compileActiveNoiseRules 整体丢弃）
    let noiseMemo = new WeakMap();       // graph.methods 原始方法对象 → 是否噪声
    let freqNoiseMemo = new WeakMap();   // methodFrequency 条目对象 → 是否噪声

    /** 上次已整体刷新所依据的规则指纹（用于跳过无变化的重算） */
    let _lastAppliedRulesHash = null;

    // ================================================================
    // C7 函数定义（按 app.js 原始行号顺序照搬）
    // ================================================================

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
        const p = Sig.splitSignature(m.method);
        const v = matchCompiledRules(src, p.className, p.methodName, p.paramCount);
        freqNoiseMemo.set(m, v);
        return v;
    }

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
        compiledActiveRules = Sig.compileRules(activeNoiseRules);
        activeNoiseHash = enabled.map((r) => (r.source || 'ALL') + '~' + (r.methodPattern || '')
            + '~' + (r.classPattern || '') + '~' + (r.paramCount != null ? r.paramCount : '')).join('|');
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
        loadNoiseRules().then(refreshAllFilteredViews);
        els.noiseRulesPanel.hidden = false;
        els.noiseRulesOverlay.hidden = false;
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

    /** 将弹窗/页面当前输入同步到内存并实时刷新频次列表 + 调用链剪枝预览 */
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

    // 全选启用 / 全不选 / 反选（作用在当前可见的列表）
    function forEachNoiseRuleCheckbox(fn) {
        const container = App.state.noiseRuleMode === 'page' ? els.noiseRulesListPage : els.noiseRulesList;
        container.querySelectorAll('.nr-enable input').forEach(fn);
    }

    // ---- 导出规则 ----
    function exportNoiseRules() {
        let url = 'api/noise-rules/export';
        if (noiseRuleScope === 'project') {
            const pp = currentProjectPath();
            if (pp) url += '?projectPath=' + encodeURIComponent(pp);
        }
        window.location.href = url;
    }

    // ---- 导入规则 ----
    async function handleNoiseRuleImportFile(e, fileInput) {
        const file = e.target.files[0];
        if (!file) return;
        if (!(await showConfirm('导入将覆盖当前层级的所有规则，确定继续？', '导入规则'))) {
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
            .catch((err) => showToast('导入失败：' + err.message + '（请确认是合法的 noise-rules.json 文件）', 'error', 5000));
    }

    // ---- 新增规则 ----
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

    // ---- 删除规则 ----
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

    // ---- 恢复默认 / 清空规则 ----
    async function resetNoiseRules() {
        const msg = noiseRuleScope === 'project'
            ? '确定清空当前项目的规则？清空后将回退到仅使用全局默认。'
            : '确定恢复默认规则？当前未保存的修改将丢失。';
        if (!(await showConfirm(msg, noiseRuleScope === 'project' ? '清空项目规则' : '恢复默认规则'))) return;
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

    // ---- 保存生效 ----
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
            }).catch(() => showToast('保存失败，请检查规则格式', 'error', 5000));
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
        }).catch(() => showToast('保存失败，请检查规则格式', 'error', 5000));
    }

    /** 引导用：记下"用户已经动过过滤规则"，并刷新引导 */
    function markNoiseConfigured() {
        if (window.Guide) Guide.markSeen(Guide.NOISE_CONFIGURED);
        if (_hooks.guideRefresh) _hooks.guideRefresh();
    }

    // ================================================================
    // 事件绑定（按 app.js 原始顺序照搬）
    // ================================================================

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
    els.btnNoiseRuleExport.addEventListener('click', exportNoiseRules);
    els.btnNrPageExport.addEventListener('click', exportNoiseRules);

    // 导入规则：触发文件选择（弹窗 / 页面共用）
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
    els.btnNoiseRuleAdd.addEventListener('click', addNoiseRule);
    els.btnNrPageAdd.addEventListener('click', addNoiseRule);

    // 删除规则（弹窗 / 页面共用）
    els.noiseRulesList.addEventListener('click', handleNoiseRuleDelete);
    els.noiseRulesListPage.addEventListener('click', handleNoiseRuleDelete);

    // 恢复默认 / 清空规则（页面视图下可用；弹窗内隐藏）
    els.btnNoiseRuleReset.addEventListener('click', () => resetNoiseRules());
    els.btnNrPageReset.addEventListener('click', () => resetNoiseRules());

    // 保存生效（弹窗模式保存后关闭；页面模式保持打开）
    els.btnNoiseRuleSave.addEventListener('click', saveNoiseRules);
    els.btnNrPageSave.addEventListener('click', saveNoiseRules);

    // ================================================================
    // 入口（Step3 弹窗 / 噪声规则页面）导航绑定（原 app.js 行 188-209）
    // ================================================================
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

    // ================================================================
    // init：注入晚绑定钩子
    // ================================================================

    function init(h) {
        if (h.guideRefresh) _hooks.guideRefresh = h.guideRefresh;
    }

    // ================================================================
    // C7 只读访问器（app.js guideNoiseConfigured 活读模块私有状态）
    // ================================================================

    function getProjectRulesCache() { return projectRulesCache; }

    function getGlobalOverrides() { return globalOverrides; }

    // ================================================================
    // 公共 API
    // ================================================================

    const Noise = {
        init: init,
        noiseRulesHash: noiseRulesHash,
        isNoiseGraphMethod: isNoiseGraphMethod,
        isNoiseMethod: isNoiseMethod,
        matchCompiledRules: matchCompiledRules,
        loadNoiseRules: loadNoiseRules,
        renderNoiseRulesList: renderNoiseRulesList,
        updateFilteredStats: updateFilteredStats,
        refreshAllFilteredViews: refreshAllFilteredViews,
        filterFreqMethod: filterFreqMethod,
        openNoiseRulesPanel: openNoiseRulesPanel,
        closeNoiseRulesPanel: closeNoiseRulesPanel,
        applyNoiseRulesFromPanel: applyNoiseRulesFromPanel,
        exportNoiseRules: exportNoiseRules,
        handleNoiseRuleImportFile: handleNoiseRuleImportFile,
        addNoiseRule: addNoiseRule,
        handleNoiseRuleDelete: handleNoiseRuleDelete,
        resetNoiseRules: resetNoiseRules,
        saveNoiseRules: saveNoiseRules,
        markNoiseConfigured: markNoiseConfigured,
        getProjectRulesCache: getProjectRulesCache,
        getGlobalOverrides: getGlobalOverrides,
    };

    return Noise;
});