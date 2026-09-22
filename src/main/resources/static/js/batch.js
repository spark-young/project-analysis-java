/*
 * batch.js —— 批量加载与频次分析 + 项目级搜索定位（OPT-27 C6 簇）
 * ================================================================================
 * 纯搬运自 app.js 原「批量结果清单视图 / 批量全量加载 / 项目级方法索引与频率 / 项目级
 * 方法搜索 / 方法签名展示工具 / 方法调用次数分析」各段（原 IIFE 行号 245-913 与
 * 1242-1632 中的 C6 函数，四段不连续区间的 C5/C7 函数未搬），函数体逐行照搬
 * （async/await 与模板字符串原样保留），仅机械适配（零行为变化）：
 *   1. 跨簇共享状态 → App.state.*（batchModel/batchRowStates/freqFilter，归属见 js/state.js
 *      头注释；currentProjectId/currentResult/currentBatchSummary/currentCacheFileName/
 *      currentExcelMode 为 C3/C4 已迁字段）
 *   2. 调用未搬簇函数 → init(hooks) 晚绑定：renderStats/computeBatchFilteredStats/
 *      renderWarnings/hideBatchEntryHeader/renderSingleEntryResult/rootsOf/nodeEl/
 *      updateBatchRowStats/graphIndex/isNoiseGraphMethod/isNoiseMethod/filterFreqMethod
 *      （C5 结果簇与 C7 噪声簇，均未搬）；共享可变对象用 getter hook 活读：
 *      getNodeRegistry（C5 的 const Map，C6 清空/读、C5 写）/ getSourceLabel（C5 常量表）/
 *      clearExpandFns（C5 的 expandFns 数组，C6 仅就地 length=0 截断）
 *   3. 与已搬簇走命名空间：Ui.* / Api.* / Sig.*
 *   4. 事件绑定收进 init()，注册顺序 = 原 IIFE 行号顺序（909-913 → 1573-1609 → 1625-1632）
 * 模块私有状态（有意不进 App.state，依据见 state.js 头注释）：freqViewMode /
 * projSearchMarks / projSearchOrder / projSearchCursor / _freqRows / _freqPage
 * （grep 确认本簇外零引用）。
 */
(function (root, factory) {
    'use strict';
    const Batch = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Batch;               // Node（契约测试可 require）
    }
    if (root) {
        root.Batch = Batch;                   // 浏览器（entries.js 之后、app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // 基础 UI / 请求层薄引用（与 app.js 顶部同模式）
    const els = Ui.els;
    const showError = Ui.showError;
    const showLoading = Ui.showLoading;
    const hideLoading = Ui.hideLoading;
    const showToast = Ui.showToast;
    const badgeHtml = Ui.badgeHtml;
    const escapeHtml = Ui.escapeHtml;
    const fetchJson = Api.fetchJson;

    // 模块私有状态（见文件头注释第 19-21 行说明）
    let freqViewMode = 'entry';
    let projSearchMarks = null;
    let projSearchOrder = [];
    let projSearchCursor = -1;
    let projSearchFilteredCount = 0;   // 命中但被过滤规则剪掉的方法数（页面上不可见，Excel 的「被过滤方法」Sheet 有）

    // 晚绑定钩子：app.js 在 init 时注入
    const hooks = {
        renderStats: null,                // C5 统计条（未搬）
        computeBatchFilteredStats: null,  // C5 批量聚合统计（未搬）
        renderWarnings: null,             // C5 警告区（未搬）
        hideBatchEntryHeader: null,       // C5 返回清单按钮（未搬）
        renderSingleEntryResult: null,    // C5 单入口渲染（未搬）
        rootsOf: null,                    // C5 惰性树适配层（未搬）
        nodeEl: null,                     // C5 树节点渲染（未搬）
        updateBatchRowStats: null,        // C5 行内统计重算（未搬）
        graphIndex: null,                 // C5 图索引缓存（未搬）
        isNoiseGraphMethod: null,         // C5 图方法噪声判定（未搬）
        isNoiseMethod: null,              // C7 频次条目噪声判定（未搬）
        filterFreqMethod: null,           // C7 频次行「加入过滤规则」（未搬）
        getNodeRegistry: null,            // C5 nodeRegistry（const Map）活读 getter
        getSourceLabel: null,             // C5 SOURCE_LABEL 常量表活读 getter
        clearExpandFns: null              // C5 expandFns 就地清空（length=0）钩子
    };

    /** init：注入跨簇依赖 + 注册事件绑定（顺序 = 原 IIFE 行号顺序 909/910/911/912/913/1574/1608/1609/1625） */
    function init(h) {
        hooks.renderStats = h.renderStats;
        hooks.computeBatchFilteredStats = h.computeBatchFilteredStats;
        hooks.renderWarnings = h.renderWarnings;
        hooks.hideBatchEntryHeader = h.hideBatchEntryHeader;
        hooks.renderSingleEntryResult = h.renderSingleEntryResult;
        hooks.rootsOf = h.rootsOf;
        hooks.nodeEl = h.nodeEl;
        hooks.updateBatchRowStats = h.updateBatchRowStats;
        hooks.graphIndex = h.graphIndex;
        hooks.isNoiseGraphMethod = h.isNoiseGraphMethod;
        hooks.isNoiseMethod = h.isNoiseMethod;
        hooks.filterFreqMethod = h.filterFreqMethod;
        hooks.getNodeRegistry = h.getNodeRegistry;
        hooks.getSourceLabel = h.getSourceLabel;
        hooks.clearExpandFns = h.clearExpandFns;
        bindEvents();
    }

    function bindEvents() {
        els.btnProjectSearch.addEventListener('click', runProjectSearch);
        els.projectSearchInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') runProjectSearch(); });
        els.projectSearchNextHit.addEventListener('click', nextProjectHit);
        els.projectSearchPrevHit.addEventListener('click', prevProjectHit);
        els.btnProjectSearchClear.addEventListener('click', clearProjectSearch);

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
                else hooks.filterFreqMethod(row);
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

        // 来源筛选：全部/项目/依赖/外部
        els.freqFilterBar.querySelectorAll('.filter-chip').forEach((chip) => {
            chip.addEventListener('click', () => {
                App.state.freqFilter = chip.dataset.filter;
                els.freqFilterBar.querySelectorAll('.filter-chip')
                    .forEach((c) => c.classList.toggle('active', c === chip));
                refreshFreqView();
            });
        });
    }

    /** 批量结果清单视图：显示所有入口 + 每入口摘要，点击某行加载该入口的完整调用链 */
    function renderBatchSummary(batch) {
        App.state.currentBatchSummary = batch;
        const entries = batch.entries || [];
        const done = entries.filter((e) => !e.failed).length;
        const failedCount = entries.length - done;

        els.resultTitle.innerHTML =
            '<span class="result-title-text">交易链路分析结果</span>'
            + '<span class="entry-count-inline">（共 ' + entries.length + ' 个入口，成功 ' + done + ' 个'
            + (failedCount > 0 ? '，失败 ' + failedCount + ' 个' : '）') + '）</span>';
        hooks.renderStats(hooks.computeBatchFilteredStats(batch));
        hooks.renderWarnings({ warnings: batch.warnings || [] });
        hooks.hideBatchEntryHeader();

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
        App.state.batchRowStates.length = 0;
        hooks.getNodeRegistry().clear();
        hooks.clearExpandFns();
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
            App.state.batchRowStates[idx] = st;
            row.setAttribute('data-idx', idx);
            row.addEventListener('click', () => toggleEntryBody(idx));
        });
        markEntryRows();   // 重绘清单后恢复「含命中」入口标记

        els.freqSection.hidden = true;
        els.resultSection.hidden = false;
        // 批量清单：可直接导出整个项目的 Excel 报告
        App.state.currentExcelMode = 'project';
        els.btnExcel.disabled = false;
        els.btnExcel.title = '导出整个项目的 Excel 报告（全部入口调用链 + 项目级方法频率）';
        // 清单视图无树可展开/收起
        els.btnExpandAll.style.display = 'none';
        els.btnCollapseAll.style.display = 'none';
        els.legend.style.display = 'none';
        els.globalSearch.style.display = 'none';

        // ---- 项目级：自动全量加载全部入口调用链（浏览器端并行），完成后支持项目搜索 + 项目频率 ----
        if (!App.state.batchModel || App.state.batchModel.batch !== batch) {
            resetBatchModel(batch);
            // 关联全量加载生成的条目状态到 batchRowStates（供行内展开/搜索高亮按 index 取）
            els.projectSearch.hidden = true;
            loadAllBatchEntries(batch);          // async、不 await；统一进度由 batchProgressBar 呈现
        } else if (App.state.batchModel.loaded) {
            // 返回清单视图：恢复项目级面板（数据已载入内存，不重复下拉）
            showProjectPanels();
        }
    }

    /** 行内展开：首次展开时用全量加载缓存在行内渲染该入口调用链；再点收起 */
    async function toggleEntryBody(idx) {
        const st = App.state.batchRowStates[idx];
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
        const slot = App.state.batchModel && App.state.batchModel.entries[st.idx];
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
        st.roots = hooks.rootsOf(result, st.idx);
        st.roots.forEach((root) => st.body.appendChild(hooks.nodeEl(root, 0)));
        st.rendered = true;
        hooks.updateBatchRowStats(st);   // 行内统计 chips 按生效规则重算
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

    // ------------------------------------------------------------------
    // 批量全量加载：浏览器端并发拉取全部入口缓存 → 建项目级方法索引 + 项目级频率，
    // 树的 DOM 仍按入口按需渲染。统一进度：分析 0~80%，链加载 80~100%。
    // ------------------------------------------------------------------

    function resetBatchModel(batch) {
        App.state.batchModel = {
            batch,
            projectId: App.state.currentProjectId,
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
        return fetchJson('/api/projects/' + encodeURIComponent(App.state.currentProjectId)
            + '/cache/load-file?file=' + encodeURIComponent(fileName));
    }

    /** 并发（limit 个 worker）加载全部入口，统一进度条继续从 80% 走到 100% */
    async function loadAllBatchEntries(batch) {
        const all = batch.entries || [];
        App.state.batchModel.entries = all.map((entry) => ({
            entry, result: null, status: entry && entry.failed ? 'err' : 'pending', err: null,
        }));
        // 可取加载的原始行号（失败/无缓存文件的不拉取）
        const positions = [];
        all.forEach((e, i) => { if (e && !e.failed && e.fileName) positions.push(i); });
        const total = positions.length;
        App.state.batchModel.total = total;
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
                const slot = App.state.batchModel.entries[pos];
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

        App.state.batchModel.done = done;
        App.state.batchModel.failed = failed;
        finalizeBatchLoad();
    }

    /** 全量加载收尾：聚合频率，置加载完成态并展示项目级面板 */
    function finalizeBatchLoad() {
        const loaded = App.state.batchModel.entries.filter((s) => s.result).length;
        App.state.batchModel.loaded = true;
        App.state.batchModel.projectFreq = buildProjectFreq(App.state.batchModel.entries);
        const truncated = (App.state.batchModel.batch.entries || []).some((e) => e.stats && e.stats.truncated);

        els.batchProgressBar.style.width = '100%';
        els.batchProgressText.textContent = '✓ 加载完成 · 已加载 ' + loaded + '/' + App.state.batchModel.total + ' 个入口';
        setBatchLoadState(
            (truncated ? '存在截断入口，索引可能不完整 · ' : '')
            + '已加载 ' + loaded + '/' + App.state.batchModel.total + ' 个入口'
            + (App.state.batchModel.failed ? ' · 失败 ' + App.state.batchModel.failed + ' 个（点「重新分析」或下方行重试）' : '')
            + '，可在下方搜索某个方法被哪些交易入口调用');
        showProjectPanels();
    }

    /** 展示项目级面板：项目搜索 + 项目级频率（不再触载加载） */
    function showProjectPanels() {
        els.projectSearch.hidden = false;
        App.state.freqFilter = 'ALL';
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
    // 解析/格式化逻辑统一收敛到共享模块 Sig（sig.js，先于 app.js 加载，见 index.html）。
    // ------------------------------------------------------------------
    /** 统一可读签名：全限定类名#方法名(参数...)。无方法名(整类入口)只显示类名。 */
    function readableFullSig(className, methodName, descriptor) {
        return Sig.fullSignature(className, methodName, descriptor);
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

    /** 规则变更后刷新频次列表：自动按当前视图取数（项目级聚合 / 单入口） */
    function refreshFreqView() {
        if (freqViewMode === 'project') {
            if (App.state.batchModel && App.state.batchModel.projectFreq) renderFreqList(App.state.batchModel.projectFreq);
        } else if (App.state.currentResult) {
            renderFreqList(App.state.currentResult.methodFrequency || []);
        }
    }

    /** 渲染项目级频率（复用 renderFreqList，来源过滤/样板规则自动生效） */
    function renderProjectFreq() {
        if (!App.state.batchModel || !App.state.batchModel.projectFreq) return;
        freqViewMode = 'project';
        els.freqSection.hidden = false;
        renderFreqList(App.state.batchModel.projectFreq);
    }

    // ---------- 项目级方法搜索：搜方法 → 列出调用它的所有交易入口 ----------

    function clearProjectSearch() {
        els.projectSearchResult.textContent = '';
        els.projectSearchResult.className = 'search-result';
        projSearchFilteredCount = 0;
        clearProjectMarks();
    }

    function runProjectSearch() {
        const term = els.projectSearchInput.value.trim();
        clearProjectSearch();
        if (!term) return;
        if (!App.state.batchModel || !App.state.batchModel.loaded) {
            els.projectSearchResult.className = 'search-result err';
            els.projectSearchResult.textContent = '全量加载尚未完成，请稍后再搜';
            return;
        }
        const exact = els.projectSearchMode.value === 'exact';
        const { marks, order, filtered } = computeSearchMarks(term, exact);
        projSearchFilteredCount = filtered;
        if (order.length === 0) {
            els.projectSearchResult.className = 'search-result err';
            // 与 Excel 口径对齐：页面上看不见的方法是被「过滤规则」剪掉的，不是项目里没有。
            // 不说明白的话，用户会以为搜索漏了（Excel 的「被过滤方法」Sheet 确实能搜到）。
            els.projectSearchResult.textContent = filtered > 0
                ? '未命中可见调用链 —— 但有 ' + filtered + ' 处匹配被「过滤规则」剪掉了（Excel 的「被过滤方法」Sheet 可查；'
                    + '想在这里看到，可在「过滤规则」里停用对应规则后点「⟳ 刷新过滤」）'
                : '未命中 —— 项目内没有方法匹配 "' + term + '"';
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
        let filtered = 0;
        const entries = (App.state.batchModel && App.state.batchModel.entries) || [];
        entries.forEach((slot, idx) => {
            const g = slot.result && slot.result.graph;
            if (!g || !g.methods) return;
            const targets = [];
            g.methods.forEach((m, i) => {
                if (!m) return;
                // 被 noise 规则剪掉的方法在视图里不存在，不能标记；但命中数要单独统计出来，
                // 否则用户会以为"项目里没有这个方法"（Excel 的「被过滤方法」Sheet 里能看到）
                if (hooks.isNoiseGraphMethod(m)) {
                    if (matchRawMethod(m, term, exact)) filtered++;
                    return;
                }
                if (matchRawMethod(m, term, exact)) targets.push(i);
            });
            if (targets.length === 0) return;
            const hit = new Set(targets);
            const hasHit = new Set();
            const parents = hooks.graphIndex(slot.result).parents;
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
        return { marks, order, filtered };
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
        hooks.getNodeRegistry().forEach((entry, node) => {
            if (entry && entry.rowEl) applyNodeMark(node, entry.rowEl);
        });
    }

    /** 入口清单行标记：含命中的入口加「含命中 N 处」 */
    function markEntryRows() {
        App.state.batchRowStates.forEach((st) => {
            if (!st || !st.rowEl) return;
            st.rowEl.classList.remove('has-hit');
            const old = st.rowEl.querySelector('.bt-hit-mark');
            if (old) old.remove();
        });
        if (!projSearchMarks) return;
        projSearchMarks.forEach((mk, idx) => {
            const st = App.state.batchRowStates[idx];
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
        // 被过滤规则剪掉的命中单独提示，避免与"项目里没有"混淆（Excel 的「被过滤方法」Sheet 能查到）
        const filtered = projSearchFilteredCount > 0
            ? ' · 另有 ' + projSearchFilteredCount + ' 处被过滤规则剪掉（Excel「被过滤方法」可查）'
            : '';
        return '命中 ' + projSearchOrder.length + ' 处 · 已标记 ' + projSearchMarks.size
            + ' 个入口（展开逐层引导，点「下一个命中」可跳转）' + cursor + filtered;
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
            const ke = hooks.getNodeRegistry().get(only);
            if (!ke || !ke.setExpanded) return;
            ke.setExpanded(true);
            cur = only;
        }
    }

    /** 展开某入口到指定命中的完整路径并定位（供「下一个命中」），返回命中节点行 */
    function revealHitPath(entryIdx, gid) {
        const st = App.state.batchRowStates[entryIdx];
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
        const parents = hooks.graphIndex(st.result).parents;
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
            const en = hooks.getNodeRegistry().get(node);
            if (!en || !en.setExpanded) break;
            en.setExpanded(true);
            const next = (node.children || []).find((c) => c.gid === chain[i + 1]);
            if (!next) break;
            node = next;
        }
        const te = hooks.getNodeRegistry().get(node);
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
        const slot = App.state.batchModel && App.state.batchModel.entries[st.idx];
        const result = slot && slot.result;
        if (!result) return null;
        st.result = result;
        st.body.innerHTML = '';
        st.roots = hooks.rootsOf(result, st.idx);
        st.roots.forEach((root) => st.body.appendChild(hooks.nodeEl(root, 0)));
        st.rendered = true;
        return result;
    }

    // ------------------------------------------------------------------
    // 方法调用次数分析：所有方法按被调次数降序，点击行展开查看调用位置
    // ------------------------------------------------------------------

    function renderFreqAnalysis(result) {
        const all = result.methodFrequency || [];
        // 每次新分析重置筛选为"全部"
        App.state.freqFilter = 'ALL';
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
            chip.classList.toggle('active', f === App.state.freqFilter);
        });
    }

    /** 后端 methodFrequency 的 Top-N 上限（与 AnalysisService.DEFAULT_METHOD_TOP_N 对齐） */
    const FREQ_TOP_N = 200;

    /** methodId → 可读方法签名（从当前单入口结果的节点表 graph.methods 解析） */
    function resolveFreqSignature(id) {
        const g = App.state.currentResult && App.state.currentResult.graph;
        const methods = g && g.methods;
        if (methods && id != null && methods[id]) {
            const m = methods[id];
            // 兜底签名统一走 Sig（原实现漏掉参数列表 cls#name，与权威格式 cls#name(...) 漂移）
            return m.display || Sig.fullSignature((m.owner || '').replace(/\//g, '.'), m.name, m.descriptor);
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
                    hooks.getSourceLabel()[item.source] || item.source)
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
        const noiseFiltered = normalized.filter((m) => !hooks.isNoiseMethod(m));
        // 来源标签计数 = 样板规则过滤后的各来源数量
        updateFreqFilterChips(noiseFiltered);
        // 第二步：在样板过滤基础上再按当前来源筛选
        const list = noiseFiltered.filter((m) => {
            if (App.state.freqFilter !== 'ALL' && (m.source || 'EXTERNAL') !== App.state.freqFilter) return false;
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

    /** 拆解权威签名 → { className, simpleClass, methodName, paramCount }（逻辑统一在 Sig，见 sig.js） */
    function splitMethodSignature(method) {
        return Sig.splitSignature(method);
    }

    /** 当前频次视图对应的分析结果集合（项目级聚合 → 全部已加载入口） */
    function freqTargetResults() {
        if (freqViewMode === 'project' && App.state.batchModel && App.state.batchModel.entries) {
            return App.state.batchModel.entries.map((s) => s && s.result).filter(Boolean);
        }
        return App.state.currentResult ? [App.state.currentResult] : [];
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

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------
    return {
        init: init,
        renderBatchSummary: renderBatchSummary,
        toggleEntryBody: toggleEntryBody,
        buildEntryBody: buildEntryBody,
        resetBatchModel: resetBatchModel,
        loadAllBatchEntries: loadAllBatchEntries,
        finalizeBatchLoad: finalizeBatchLoad,
        showProjectPanels: showProjectPanels,
        readableFullSig: readableFullSig,
        sigHtmlFromString: sigHtmlFromString,
        appendSigFromString: appendSigFromString,
        refreshFreqView: refreshFreqView,
        renderProjectFreq: renderProjectFreq,
        clearProjectSearch: clearProjectSearch,
        renderFreqAnalysis: renderFreqAnalysis,
        splitMethodSignature: splitMethodSignature,
        applyNodeMark: applyNodeMark,
        expandGuided: expandGuided
    };
});
