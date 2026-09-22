/*
 * result.js —— 结果树渲染与搜索（OPT-27 C5 簇）
 * ================================================================================
 * 纯搬运自 app.js 原「结果渲染」各段（行号 249-576 与 1196-1511 中的 C5 函数），
 * 函数体逐行照搬（模板字符串原样保留），仅机械适配（零行为变化）：
 *   1. 跨簇共享状态 → App.state.*（guideHasResult/guideResultStale/batchModel/
 *      batchRowStates/currentResult/currentBatchSummary/currentCacheFileName/
 *      currentExcelMode 为 C2/C3/C4/C6 已迁字段）
 *   2. 调用已搬簇函数 → 命名空间访问（Ui.* / Batch.*）
 *   3. 调用未搬簇函数 → init(hooks) 晚绑定：isNoiseGraphMethod / noiseRulesHash /
 *      guideRefresh（C7 噪声簇与 app.js 引导，均未搬）
 *   4. 图索引缓存 _graphIndexCache（C5 使用 graphIndex + computeFilteredStats）
 *      → 该弱引用缓存仅在 graphIndex 内部，随 result 对象生命周期自动 GC
 * 模块私有状态（有意不进 App.state，依据见 js/state.js 头注释）：
 *   expandFns / nodeRegistry / hitRows / activeSearch / _adapterCache
 *   （grep 确认本簇外零引用，C6 通过 getter hook 活读 nodeRegistry 与 expandFns）。
 */
(function (root, factory) {
    'use strict';
    const ResultView = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = ResultView;               // Node（契约测试可 require）
    }
    if (root) {
        root.ResultView = ResultView;              // 浏览器（batch.js 之后、app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // 基础 UI / 已搬簇薄引用（与 app.js 顶部同模式）
    const els = Ui.els;
    const showLoading = Ui.showLoading;
    const hideLoading = Ui.hideLoading;
    const badge = Ui.badge;
    const escapeHtml = Ui.escapeHtml;

    // C6 批量模块（appendSigFromString 等）
    const appendSigFromString = Batch.appendSigFromString;
    const readableFullSig = Batch.readableFullSig;
    const renderBatchSummary = Batch.renderBatchSummary;
    const renderFreqAnalysis = Batch.renderFreqAnalysis;
    const expandGuided = Batch.expandGuided;
    const applyNodeMark = Batch.applyNodeMark;
    const clearProjectSearch = Batch.clearProjectSearch;

    // 晚绑定钩子：app.js 在 init 时注入（C7 噪声判定、引导等未搬簇函数）
    const _hooks = {
        guideRefresh: null,
        isNoiseGraphMethod: null,
        noiseRulesHash: null,
    };

    // ================================================================
    // 模块私有状态（见文件头注释说明）
    // ================================================================

    let expandFns = [];               // 全部展开/收起用
    const nodeRegistry = new Map();   // 数据节点 → { rowEl, setExpanded }
    let hitRows = [];                 // 当前搜索高亮的行
    let activeSearch = null;          // 当前打开的行内搜索栏 { bar, node }

    // 结果对象 → 其惰性根节点缓存的映射（保证渲染与全局搜索共用同一对象身份，命中 nodeRegistry）
    // 额外按 noise 规则哈希失效：规则变更时自动重建
    let _adapterCache = { node: null, idx: null, noiseHash: '', roots: [] };

    const SOURCE_LABEL = { PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' };
    const INVOKE_LABEL = {
        VIRTUAL: '虚调用', STATIC: '静态', INTERFACE: '接口',
        SPECIAL: '构造/super', DYNAMIC: 'lambda', IMPL: '接口实现分派',
    };

    // 图索引缓存：同一 result 的邻接表/父表只建一次
    let _graphIndexCache = new WeakMap();

    // ================================================================
    // C5 函数（按原 app.js 行号顺序排列，方便 trace）
    // ================================================================

    function renderResult(result) {
        App.state.guideHasResult = true;
        App.state.guideResultStale = false;
        if (_hooks.guideRefresh) _hooks.guideRefresh();
        if (result && result.kind === 'batch') {
            renderBatchSummary(result);
            if (window.Guide) {
                Guide.tipOnce('result-area', els.statsBar,
                    '过滤规则改完会自动作用到这里；也可点右上角「⟳ 刷新过滤」手动重刷统计与调用链。');
            }
            return;
        }
        renderSingleEntryResult(result, null);
        if (window.Guide) {
            Guide.tipOnce('result-area', els.statsBar,
                '过滤规则改完会自动作用到这里；也可点右上角「⟳ 刷新过滤」手动重刷统计与调用链。');
        }
    }

    function invalidateAdapterCache() {
        _adapterCache = { node: null, idx: null, noiseHash: '', roots: [] };
    }

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

    function reapplyFilterToTree() {
        if (typeof clearProjectSearch === 'function') clearProjectSearch();
        invalidateAdapterCache();
        const inBatchList = !!(els.tree && els.tree.querySelector('.bt-row'));
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
        if (!inBatchList && els.tree && els.tree.children.length > 0 && App.state.currentResult) {
            clearSearchHits();
            els.tree.innerHTML = '';
            rootsOf(App.state.currentResult).forEach((root) => els.tree.appendChild(nodeEl(root, 0)));
        }
    }

    function rootsOf(result, entryIdx) {
        const curNoiseHash = _hooks.noiseRulesHash ? _hooks.noiseRulesHash() : '';
        if (_adapterCache.node === result
            && _adapterCache.idx === entryIdx
            && _adapterCache.noiseHash === curNoiseHash) return _adapterCache.roots;
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
            if (_hooks.isNoiseGraphMethod && _hooks.isNoiseGraphMethod(m)) return null;
            const view = methodView(m);
            let kidsCache = null;
            const node = {
                gid: id,
                entryIdx: entryIdx,
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
                        .filter(Boolean);
                    return kidsCache;
                }
            };
            return node;
        }
        const roots = (g.roots || [])
            .map((id) => makeNode(id, null, 0))
            .filter(Boolean);
        _adapterCache = { node: result, idx: entryIdx, noiseHash: curNoiseHash, roots: roots };
        return _adapterCache.roots;
    }

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
        if (fileName && App.state.currentBatchSummary) {
            els.btnBackToList.hidden = false;
        } else {
            els.btnBackToList.hidden = true;
        }
    }

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
            if (!m || (_hooks.isNoiseGraphMethod && _hooks.isNoiseGraphMethod(m))) continue;
            out.totalNodes++;
            const s = m.source || 'EXTERNAL';
            if (s === 'PROJECT') out.projectMethods++;
            else if (s === 'DEPENDENCY') out.dependencyMethods++;
            else out.externalMethods++;
            if (m.cycle) continue;
            const edges = adj[id] || [];
            for (let i = 0; i < edges.length; i++) stack.push(edges[i].to);
        }
        return out;
    }

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
                    node.children.forEach((c) => kids.appendChild(nodeEl(c, depth + 1)));
                    rendered = true;
                }
                kids.style.display = show ? '' : 'none';
                toggle.textContent = show ? '▾' : '▸';
            };
            entry.setExpanded = setExpanded;
            expandFns.push(setExpanded);
            row.addEventListener('click', () => {
                if (kids.style.display === 'none') expandGuided(node, setExpanded);
                else setExpanded(false);
            });
            wrap.appendChild(kids);
        } else {
            row.addEventListener('click', () => { });
        }
        return wrap;
    }

    function matchNode(n, term, exact) {
        const m = n.method || {};
        const q = term.trim();
        if (exact) {
            return m.name === q
                || m.display === q
                || (m.className || '') + '.' + m.name === q
                || (m.simpleClassName || '') + '.' + m.name === q;
        }
        const needle = q.toLowerCase();
        return [m.name, m.display, m.className, m.simpleClassName]
            .some((v) => v && v.toLowerCase().indexOf(needle) >= 0);
    }

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

    // ================================================================
    // 全部展开 / 收起（原在 app.js 事件绑定中直接访问 expandFns）
    // ================================================================

    function expandAll() {
        showLoading('正在展开全部节点……');
        setTimeout(() => {
            expandFns.forEach((f) => f(true));
            hideLoading();
        }, 20);
    }

    function collapseAll() {
        expandFns.forEach((f) => f(false));
    }

    // ================================================================
    // C6 访问器（活读 C5 私有状态，避免直接暴露可变引用）
    // ================================================================

    function getNodeRegistry() { return nodeRegistry; }

    function getSourceLabel() { return SOURCE_LABEL; }

    function clearExpandFns() { expandFns.length = 0; }

    // ================================================================
    // init：注入晚绑定钩子
    // ================================================================

    function init(h) {
        if (h.guideRefresh) _hooks.guideRefresh = h.guideRefresh;
        if (h.isNoiseGraphMethod) _hooks.isNoiseGraphMethod = h.isNoiseGraphMethod;
        if (h.noiseRulesHash) _hooks.noiseRulesHash = h.noiseRulesHash;
    }

    // ================================================================
    // 公共 API
    // ================================================================

    const ResultView = {
        init: init,
        renderResult: renderResult,
        renderWarnings: renderWarnings,
        nodeEl: nodeEl,
        graphIndex: graphIndex,
        rootsOf: rootsOf,
        renderStats: renderStats,
        computeFilteredStats: computeFilteredStats,
        matchNode: matchNode,
        collectMatches: collectMatches,
        clearSearchHits: clearSearchHits,
        toggleSearchBar: toggleSearchBar,
        focusMatches: focusMatches,
        resetGlobalSearch: resetGlobalSearch,
        runGlobalSearch: runGlobalSearch,
        invalidateAdapterCache: invalidateAdapterCache,
        reapplyFilterToTree: reapplyFilterToTree,
        renderSingleEntryResult: renderSingleEntryResult,
        hideBatchEntryHeader: hideBatchEntryHeader,
        updateBatchRowStats: updateBatchRowStats,
        computeBatchFilteredStats: computeBatchFilteredStats,
        backToBatchList: backToBatchList,
        expandAll: expandAll,
        collapseAll: collapseAll,
        getNodeRegistry: getNodeRegistry,
        getSourceLabel: getSourceLabel,
        clearExpandFns: clearExpandFns,
    };

    return ResultView;
});