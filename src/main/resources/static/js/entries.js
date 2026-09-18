/*
 * entries.js —— 入口清单视图：渲染 / 勾选 / 过滤 / 发起分析 / Excel 导出（OPT-27 C4 簇）
 * ================================================================================
 * 纯搬运自 app.js 原「交易入口扫描 + 勾选分析 / Excel 下载」段与「Step 2 清单渲染」段，
 * 函数体逐行照搬（async/await 与模板字符串原样保留），仅机械适配（零行为变化）：
 *   1. 跨簇共享状态 → App.state.*（entrySelKeys/currentExcelMode，归属见 js/state.js 头注释）
 *   2. 调用未搬簇函数 → init(hooks) 晚绑定：renderResult(C5)/guideRefresh(app.js)/
 *      readableFullSig+sigHtmlFromString(C5 签名渲染)；C6 状态 freqFilter/batchModel 用
 *      getter hook（getFreqFilter/getBatchModel）在调用时读取，保持活读语义
 *   3. 与已搬簇走命名空间：Ui.* / Api.* / Projects.currentProjectPath()
 *   4. 事件绑定收进 init()，注册顺序 = 原 IIFE 行号顺序（319/320/324/329/340）
 * 模块私有状态（有意不进 App.state，依据见 state.js 头注释）：entryItems（含 DOM 引用，
 * grep 确认本簇外零引用）。excludeModalItems 属 C8 弹窗状态，仍留在 app.js。
 */
(function (root, factory) {
    'use strict';
    const Entries = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Entries;             // Node（契约测试可 require）
    }
    if (root) {
        root.Entries = Entries;               // 浏览器（projects.js 之后、app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // 基础 UI / 请求层薄引用（与 app.js 顶部同模式）
    const els = Ui.els;
    const showError = Ui.showError;
    const clearError = Ui.clearError;
    const showLoading = Ui.showLoading;
    const hideLoading = Ui.hideLoading;
    const escapeHtml = Ui.escapeHtml;
    const postJson = Api.postJson;
    const currentProjectPath = Projects.currentProjectPath;

    // 晚绑定钩子：app.js 在 init 时注入
    const hooks = {
        renderResult: null,        // C5 结果簇（未搬）
        guideRefresh: null,        // 新手引导刷新（app.js 单一来源）
        readableFullSig: null,     // C5 签名渲染（未搬）
        sigHtmlFromString: null,   // C5 签名渲染（未搬）
        getFreqFilter: null,       // C6 状态 freqFilter 的活读 getter（未搬）
        getBatchModel: null        // C6 状态 batchModel 的活读 getter（未搬）
    };

    /** init：注入跨簇依赖 + 注册事件绑定（顺序 = 原 IIFE 行号顺序 319/320/324/329/340） */
    function init(h) {
        hooks.renderResult = h.renderResult;
        hooks.guideRefresh = h.guideRefresh;
        hooks.readableFullSig = h.readableFullSig;
        hooks.sigHtmlFromString = h.sigHtmlFromString;
        hooks.getFreqFilter = h.getFreqFilter;
        hooks.getBatchModel = h.getBatchModel;
        bindEvents();
    }

    // ------------------------------------------------------------------
    // 交易入口扫描 + 勾选分析
    // ------------------------------------------------------------------

    let entryItems = [];  // { dto, rowEl, checkEl }（模块私有，见头注释）

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
            App.state.currentResult = result;
            App.state.currentRequest = req;
            hooks.renderResult(result);
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

    // ------------------------------------------------------------------
    // Excel 下载
    // ------------------------------------------------------------------

    /** 项目级 Excel：把所有已加载入口的缓存文件名 + 当前来源筛选发给后端，聚合导出 */
    async function downloadProjectExcel() {
        const batchModel = hooks.getBatchModel();   // C6 状态活读（getter 注入，见头注释）
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
                    freqSourceFilter: hooks.getFreqFilter(),
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
    // Step 2: 已确认的交易入口清单 —— 渲染 + 操作
    // ------------------------------------------------------------------

    // 后端 JSON 返回的是普通 Object，没有 Java 里的 key() 方法
    function entryKey(item) {
        return item.className + '#' + (item.methodName || '') + '#' + (item.descriptor || '');
    }

    function renderEntryList() {
        if (!App.state.currentEntryList) App.state.currentEntryList = { confirmed: [], excluded: [] };
        const confirmed = App.state.currentEntryList.confirmed || [];
        const excluded = App.state.currentEntryList.excluded || [];

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
        if (confirmed.length === 0 && App.state.currentProjectId && window.Guide) {
            Guide.tipOnce('empty-entries', els.entryConfirmedList,
                '清单是分析的输入：「🔍 自动扫描加入清单」会按扫描策略自动识别 Controller / Job 等入口，识别不准的可以「➕ 手动添加」。');
        }
        hooks.guideRefresh();
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
        const fullSig = hooks.readableFullSig(fullCls, method, desc) || fullCls;

        if (mode === 'excluded') {
            const reason = item.excludeReason;
            return `<div class="entry-ex-row">
                <div class="entry-row" data-key="${entryKey(item)}">
                    <span class="entry-idx" title="序号">${num}</span>
                    ${sourceBadge}${groupBadge}
                    <span class="entry-sig" title="${escapeHtml(fullSig)}">${hooks.sigHtmlFromString(fullSig)}</span>
                    <button class="entry-restore-btn">恢复</button>
                </div>
                ${reason ? `<div class="entry-reason" title="${escapeHtml(reason)}">排除原因：${escapeHtml(reason)}</div>` : ''}
            </div>`;
        }
        const checked = App.state.entrySelKeys.has(entryKey(item)) ? ' checked' : '';
        return `<div class="entry-row${checked ? ' selected' : ''}" data-key="${entryKey(item)}">
            <span class="entry-idx" title="序号">${num}</span>
            <input type="checkbox" class="entry-cb"${checked}>
            ${sourceBadge}${groupBadge}
            <span class="entry-sig" title="${escapeHtml(fullSig)}">${hooks.sigHtmlFromString(fullSig)}</span>
            <button class="entry-exclude-btn">排除</button>
        </div>`;
    }

    /** 刷新清单顶部操作栏：全选态 / 已选数量 / 批量排除按钮可用性 */
    function updateEntryToolbar() {
        const confirmed = (App.state.currentEntryList && App.state.currentEntryList.confirmed) || [];
        const has = confirmed.length > 0;
        els.entryConfirmToolbar.hidden = !has;
        if (!has) { App.state.entrySelKeys.clear(); return; }
        const sel = confirmed.filter(i => App.state.entrySelKeys.has(entryKey(i))).length;
        els.entrySelectedCount.textContent = sel > 0 ? '已选 ' + sel + ' 个' : '';
        els.batchExcludeCount.textContent = sel > 0 ? ' (' + sel + ')' : '';
        els.btnBatchExclude.disabled = sel === 0;
        els.entryCheckAll.checked = sel > 0 && sel === confirmed.length;
        els.entryCheckAll.indeterminate = sel > 0 && sel < confirmed.length;
    }

    // ------------------------------------------------------------------
    // 事件绑定（init 时注册；顺序 = 原 app.js IIFE 内行号顺序）
    // ------------------------------------------------------------------

    function bindEvents() {
        // 原 :319
        els.entryFilter.addEventListener('input', applyEntryFilter);

        // 原 :320
        els.btnEntryAll.addEventListener('click', () => {
            entryItems.forEach((it) => { it.checkEl.checked = true; });
            updateEntryCount();
        });

        // 原 :324
        els.btnEntryNone.addEventListener('click', () => {
            entryItems.forEach((it) => { it.checkEl.checked = false; });
            updateEntryCount();
        });

        // 原 :329
        els.btnAnalyzeEntries.addEventListener('click', async () => {
            clearError();
            const checked = collectCheckedEntries();
            if (checked.length === 0) { showError('请至少勾选一个交易入口'); return; }
            await analyzeCheckedEntries(checked);
        });

        // 原 :340
        els.btnExcel.addEventListener('click', async () => {
            // 批量清单视图 → 项目级导出（全部入口）
            if (App.state.currentExcelMode === 'project') {
                await downloadProjectExcel();
                return;
            }
            if (!App.state.currentResult || !App.state.currentRequest) return;
            clearError();
            showLoading('正在生成 Excel 报告……');
            try {
                // 导出时带上当前来源筛选 + 批量展开的入口缓存文件名，Excel 与页面展示保持一致
                const exportReq = Object.assign({}, App.state.currentRequest, {
                    freqSourceFilter: hooks.getFreqFilter(),
                    cacheFileName: App.state.currentCacheFileName || '',
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
    }

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------
    return {
        init: init,
        renderEntries: renderEntries,
        collectCheckedEntries: collectCheckedEntries,
        buildEntryRequest: buildEntryRequest,
        analyzeCheckedEntries: analyzeCheckedEntries,
        updateEntryCount: updateEntryCount,
        applyEntryFilter: applyEntryFilter,
        downloadProjectExcel: downloadProjectExcel,
        entryKey: entryKey,
        renderEntryList: renderEntryList,
        renderEntryRow: renderEntryRow,
        updateEntryToolbar: updateEntryToolbar
    };
});
