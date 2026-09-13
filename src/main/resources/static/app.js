/* Java 方法调用链分析工具 - 前端逻辑（原生 JS，无外部依赖） */
(function () {
    'use strict';

    const $ = (sel) => document.querySelector(sel);

    const els = {
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
        className: $('#className'),
        classList: $('#classList'),
        methodName: $('#methodName'),
        maxDepth: $('#maxDepth'),
        btnScanEntries: $('#btnScanEntries'),
        btnAnalyze: $('#btnAnalyze'),
        btnExcel: $('#btnExcel'),
        errorBanner: $('#errorBanner'),
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
        freqSection: $('#freqSection'),
        freqStatsBar: $('#freqStatsBar'),
        freqFilterBar: $('#freqFilterBar'),
        freqList: $('#freqList'),
        btnFreqExpandAll: $('#btnFreqExpandAll'),
        btnFreqCollapseAll: $('#btnFreqCollapseAll'),
        btnFreqRefresh: $('#btnFreqRefresh'),
        btnNoiseRules: $('#btnNoiseRules'),
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
        globalSearchInput: $('#globalSearchInput'),
        globalSearchMode: $('#globalSearchMode'),
        btnGlobalSearch: $('#btnGlobalSearch'),
        btnGlobalSearchClear: $('#btnGlobalSearchClear'),
        globalSearchResult: $('#globalSearchResult'),
        globalSearchChips: $('#globalSearchChips'),
        loading: $('#loading'),
        loadingText: $('#loadingText'),
    };

    let currentResult = null;
    let currentRequest = null;  // 最近一次成功分析的请求（Excel 复用）
    let expandFns = [];      // 全部展开/收起用
    let searchTimer = null;
    const nodeRegistry = new Map();  // 数据节点 → { rowEl, setExpanded }
    let hitRows = [];                // 当前搜索高亮的行
    let activeSearch = null;         // 当前打开的行内搜索栏 { bar, node }
    let freqFilter = 'ALL';          // 方法调用次数分析的来源筛选：ALL/PROJECT/DEPENDENCY/EXTERNAL
    let noiseRules = [];             // 样板方法过滤规则（从后端加载）

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

    function showLoading(text) {
        els.loadingText.textContent = text || '正在分析……';
        els.loading.hidden = false;
    }
    function hideLoading() { els.loading.hidden = true; }

    function showError(message) {
        els.errorBanner.textContent = message;
        els.errorBanner.hidden = false;
    }
    function clearError() {
        els.errorBanner.hidden = true;
        els.errorBanner.textContent = '';
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

    function buildRequest() {
        return {
            projectPath: currentProjectPath(),
            className: els.className.value.trim(),
            methodName: els.methodName.value.trim() || null,
            maxDepth: parseInt(els.maxDepth.value, 10),
        };
    }

    function validateForm() {
        if (sourceMode === 'git' && !gitProjectPath) {
            showError('请先点击"拉取并编译"完成 Git 项目准备'); return false;
        }
        if (!currentProjectPath()) { showError('请填写项目路径'); return false; }
        return true;
    }

    // ------------------------------------------------------------------
    // 项目检查 / 类名补全
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

    els.className.addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(async () => {
            const path = currentProjectPath();
            const q = els.className.value.trim();
            if (!path || !q || q.includes('.')) return;
            try {
                const resp = await fetch('/api/classes/search?path=' + encodeURIComponent(path)
                    + '&q=' + encodeURIComponent(q));
                if (!resp.ok) return;
                const list = await resp.json();
                els.classList.innerHTML = list
                    .map((c) => '<option value="' + c + '"></option>').join('');
            } catch (e) { /* 忽略补全失败 */ }
        }, 300);
    });

    // ------------------------------------------------------------------
    // 分析
    // ------------------------------------------------------------------

    els.btnAnalyze.addEventListener('click', async () => {
        clearError();
        if (!validateForm()) return;
        if (els.className.value.trim()) {
            // 手动模式：指定类名（方法名可选）分析
            showLoading('正在索引项目与依赖……');
            els.resultSection.hidden = true;
            try {
                const req = buildRequest();
                const result = await postJson('/api/analyze', req);
                currentResult = result;
                currentRequest = req;
                renderResult(result);
            } catch (e) {
                showError(e.message);
            } finally {
                hideLoading();
            }
            return;
        }

        // 免类名模式：自动扫描全部交易入口并分析
        const path = currentProjectPath();
        showLoading('正在扫描交易入口（REST / Dubbo / ElasticJob / main）……');
        try {
            const scan = await postJson('/api/scan/entries', { projectPath: path });
            const allEntryItems = (scan.groups || []).flatMap((g) => g.entries || []);
            if (allEntryItems.length === 0) {
                // 引导用户
                els.entrySection.hidden = false;
                els.entryGroups.innerHTML =
                    '<div class="hint">未发现交易入口（REST / Dubbo / ElasticJob / main）。'
                    + '你仍然可以：1) 在上方"类名"处手动填一个类再点"开始分析"；'
                    + '2) 若这是普通无框架工程，建议填具体的类与方法名分析。</div>';
                showError('未发现可自动分析的交易入口');
                return;
            }
            // 复用勾选收集逻辑：把全部入口当作"已勾选"传过去（免类名自动全量分析）
            const checked = allEntryItems.map((dto) => ({ dto }));
            await analyzeCheckedEntries(checked);
        } catch (e) {
            showError(e.message);
        } finally {
            hideLoading();
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

    function renderGitStatus(st) {
        els.gitStatus.hidden = false;
        els.gitStatus.className = 'git-status '
            + (st.status === 'DONE' ? 'ok' : st.status === 'FAILED' ? 'err' : 'busy');
        let html = '<div class="git-status-line"><b>' + (GIT_STATUS_LABEL[st.status] || st.status) + '</b>'
            + (st.projectName ? ' · ' + escapeHtml(st.projectName) : '');
        if (st.status !== 'DONE' && st.status !== 'FAILED' && st.message) {
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
                scanEntries();   // 编译完成自动扫描交易入口
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
    // 交易入口扫描 + 勾选分析
    // ------------------------------------------------------------------

    async function scanEntries() {
        clearError();
        const path = currentProjectPath();
        if (!path) {
            showError(sourceMode === 'git' ? '请先完成 Git 拉取编译' : '请填写项目路径');
            return;
        }
        showLoading('正在扫描交易入口（REST / Dubbo / ElasticJob / main）……');
        try {
            const result = await postJson('/api/scan/entries', { projectPath: path });
            renderEntries(result);
        } catch (e) {
            showError(e.message);
        } finally {
            hideLoading();
        }
    }

    els.btnScanEntries.addEventListener('click', scanEntries);

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

    function buildEntryRequest(checked) {
        return {
            projectPath: currentProjectPath(),
            maxDepth: parseInt(els.maxDepth.value, 10),
            entries: checked.map((i) => ({
                className: i.dto.className,
                methodName: i.dto.methodName,
                methodDescriptor: i.dto.methodDescriptor,
            })),
        };
    }

    async function analyzeCheckedEntries(checked) {
        const req = buildEntryRequest(checked);
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
        if (!currentResult || !currentRequest) return;
        clearError();
        showLoading('正在生成 Excel 报告……');
        try {
            // 导出时带上当前来源筛选，Excel 与页面展示保持一致
            const exportReq = Object.assign({}, currentRequest, { freqSourceFilter: freqFilter });
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

    // ------------------------------------------------------------------
    // 结果渲染
    // ------------------------------------------------------------------

    const SOURCE_LABEL = { PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' };
    const INVOKE_LABEL = {
        VIRTUAL: '虚调用', STATIC: '静态', INTERFACE: '接口',
        SPECIAL: '构造/super', DYNAMIC: 'lambda', IMPL: '接口实现分派',
    };

    function renderResult(result) {
        els.resultTitle.textContent = result.className
            ? result.className
                + (result.methodName ? '.' + result.methodName + '（' + result.stats.entryCount + ' 个重载入口）'
                    : '（整个类 · ' + result.stats.entryCount + ' 个方法入口）')
            : '交易入口分析 · ' + result.stats.entryCount + ' 个入口';
        renderStats(result.stats);
        renderWarnings(result);
        renderFreqAnalysis(result);
        expandFns = [];
        nodeRegistry.clear();
        clearSearchHits();
        closeSearchBar();
        resetGlobalSearch();
        els.tree.innerHTML = '';
        result.roots.forEach((root) => els.tree.appendChild(nodeEl(root, 0)));
        els.resultSection.hidden = false;
        els.freqSection.hidden = false;
        els.btnExcel.disabled = false;
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

    // ------------------------------------------------------------------
    // 方法调用次数分析：所有方法按被调次数降序，点击行展开查看调用位置
    // ------------------------------------------------------------------

    function renderFreqAnalysis(result) {
        const all = result.methodFrequency || [];
        // 每次新分析重置筛选为"全部"
        freqFilter = 'ALL';
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

    /** 按当前 freqFilter + 启用的样板规则过滤并渲染方法列表 */
    function renderFreqList(all) {
        // 第一步：按样板规则过滤（跨所有来源），用于更新统计和来源标签
        const noiseFiltered = (all || []).filter((m) => !isNoiseMethod(m));
        // 来源标签计数 = 样板规则过滤后的各来源数量
        updateFreqFilterChips(noiseFiltered);
        // 第二步：在样板过滤基础上再按当前来源筛选
        const list = noiseFiltered.filter((m) => {
            if (freqFilter !== 'ALL' && (m.source || 'EXTERNAL') !== freqFilter) return false;
            return true;
        });
        // 统计栏：方法总数 + 最高/最低被调次数 + 已过滤数量
        const noiseRemoved = (all || []).length - noiseFiltered.length;
        if (list.length > 0) {
            const max = list[0].callCount;
            const min = list[list.length - 1].callCount;
            els.freqStatsBar.innerHTML = [
                ['方法总数', list.length],
                ['已过滤', noiseRemoved + ' 个'],
                ['最高被调', max + ' 次'],
                ['最低被调', min + ' 次'],
            ].map(([k, v]) =>
                '<span class="stat-chip">' + k + '<b>' + v + '</b></span>').join('');
        } else {
            els.freqStatsBar.innerHTML =
                '<span class="stat-chip">方法总数 <b>0</b></span>'
                + '<span class="stat-chip">已过滤 <b>' + noiseRemoved + ' 个</b></span>';
        }
        if (list.length === 0) {
            els.freqList.innerHTML =
                '<div class="mf-empty">该来源下暂无可统计的方法调用数据</div>';
            bindFreqRowEvents();
            return;
        }
        els.freqList.innerHTML = list.map((item, idx) => {
            const callerRows = (item.callers || [])
                .map((c) => '<div class="mf-caller">'
                    + '<span class="mf-caller-mark">↳</span>'
                    + '<span class="mf-caller-name">' + escapeHtml(c.caller) + '</span>'
                    + (c.line && c.line > 0 ? '<span class="line-no">L' + c.line + '</span>' : '')
                    + '</div>')
                .join('');
            return '<div class="mf-item" data-idx="' + idx + '">'
                + '<div class="mf-row">'
                + '<span class="mf-toggle">▸</span>'
                + '<span class="mf-rank">' + (idx + 1) + '</span>'
                + '<span class="mf-body">'
                + '<span class="mf-method">' + escapeHtml(item.method) + '</span>'
                + badgeHtml('source-' + (item.source || '').toLowerCase(),
                    SOURCE_LABEL[item.source] || item.source)
                + '</span>'
                + '<span class="mf-hot">'
                + '<span class="mf-count">' + item.callCount + '</span>'
                + '<span class="mf-count-unit">次</span>'
                + '</span>'
                + '</div>'
                + '<div class="mf-callers" style="display:none">'
                + (callerRows || '<div class="mf-empty-sub">暂无调用方信息</div>')
                + '</div>'
                + '</div>';
        }).join('');
        bindFreqRowEvents();
    }

    /** 判断方法是否命中任一启用的样板规则 */
    function isNoiseMethod(m) {
        if (!m || !m.method) return false;
        const src = m.source || 'EXTERNAL';
        const hashIdx = m.method.indexOf('#');
        const className = hashIdx >= 0 ? m.method.slice(0, hashIdx) : '';
        let methodWithArgs = hashIdx >= 0 ? m.method.slice(hashIdx + 1) : m.method;
        const parenIdx = methodWithArgs.indexOf('(');
        const methodName = parenIdx >= 0 ? methodWithArgs.slice(0, parenIdx) : methodWithArgs;
        const paramCount = parseParamCount(methodWithArgs);

        for (const r of noiseRules) {
            if (!r.enabled) continue;
            // 来源匹配
            if (r.source && r.source !== 'ALL' && r.source !== src) continue;
            // 方法名匹配
            if (!regexMatch(r.methodPattern, methodName)) continue;
            // 类名匹配（规则未配置则跳过）
            if (r.classPattern && r.classPattern.trim() !== '') {
                if (!regexMatch(r.classPattern, className)) continue;
            }
            // 参数个数匹配（规则未配置则跳过）
            if (r.paramCount != null && r.paramCount !== paramCount) continue;
            return true;
        }
        return false;
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

    /** 正则匹配（find 语义，匹配到即可） */
    function regexMatch(pattern, input) {
        // 空正则 = 匹配所有（与后端一致）
        if (!pattern || pattern.trim() === '') return true;
        if (!input) return false;
        try {
            return new RegExp(pattern).test(input);
        } catch (e) {
            return false;
        }
    }

    /** 绑定方法行的展开/收起事件 */
    function bindFreqRowEvents() {
        els.freqList.querySelectorAll('.mf-item').forEach((item) => {
            const row = item.querySelector('.mf-row');
            const callers = item.querySelector('.mf-callers');
            const toggle = item.querySelector('.mf-toggle');
            if (!row || !callers) return;
            row.onclick = () => {
                const isOpen = callers.style.display === 'block';
                if (isOpen) {
                    callers.style.display = 'none';
                    toggle.textContent = '▸';
                } else {
                    callers.style.display = 'block';
                    toggle.textContent = '▾';
                }
            };
        });
    }

    els.btnFreqExpandAll.addEventListener('click', () => {
        els.freqList.querySelectorAll('.mf-item').forEach((item) => {
            const c = item.querySelector('.mf-callers');
            const t = item.querySelector('.mf-toggle');
            if (c) c.style.display = 'block';
            if (t) t.textContent = '▾';
        });
    });
    els.btnFreqCollapseAll.addEventListener('click', () => {
        els.freqList.querySelectorAll('.mf-item').forEach((item) => {
            const c = item.querySelector('.mf-callers');
            const t = item.querySelector('.mf-toggle');
            if (c) c.style.display = 'none';
            if (t) t.textContent = '▸';
        });
    });

    // 来源筛选：全部/项目/依赖/外部
    els.freqFilterBar.querySelectorAll('.filter-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
            freqFilter = chip.dataset.filter;
            els.freqFilterBar.querySelectorAll('.filter-chip')
                .forEach((c) => c.classList.toggle('active', c === chip));
            if (currentResult) renderFreqList(currentResult.methodFrequency || []);
        });
    });

    // ------------------------------------------------------------------
    // 样板方法过滤规则管理
    // ------------------------------------------------------------------
    function loadNoiseRules() {
        fetch('api/noise-rules').then((r) => r.json()).then((data) => {
            noiseRules = data || [];
            if (currentResult) renderFreqList(currentResult.methodFrequency || []);
        }).catch(() => { noiseRules = []; });
    }

    function openNoiseRulesPanel() {
        renderNoiseRulesList();
        els.noiseRulesPanel.hidden = false;
        els.noiseRulesOverlay.hidden = false;
    }
    function closeNoiseRulesPanel() {
        els.noiseRulesPanel.hidden = true;
        els.noiseRulesOverlay.hidden = true;
    }

    function renderNoiseRulesList() {
        if (noiseRules.length === 0) {
            els.noiseRulesList.innerHTML = '<div class="mf-empty">暂无规则，点击"新增规则"添加</div>';
            return;
        }
        els.noiseRulesList.innerHTML = noiseRules.map((r, idx) =>
            '<div class="nr-item" data-idx="' + idx + '">'
            + '<div class="nr-row1">'
            + '<input class="nr-name" value="' + escapeHtml(r.name || '') + '" placeholder="规则名称">'
            + '<label class="nr-enable"><input type="checkbox" ' + (r.enabled ? 'checked' : '') + '> 启用</label>'
            + '<button type="button" class="btn small warn nr-del">删除</button>'
            + '</div>'
            + '<div class="nr-row2">'
            + '<span class="nr-label">方法名正则</span>'
            + '<input class="nr-method" value="' + escapeHtml(r.methodPattern || '') + '" placeholder="如 getInstance">'
            + '<span class="nr-label">类名正则</span>'
            + '<input class="nr-class" value="' + escapeHtml(r.classPattern || '') + '" placeholder="可选，如 .*Factory">'
            + '</div>'
            + '<div class="nr-row3">'
            + '<span class="nr-label">来源</span>'
            + '<select class="nr-source">'
            + ['ALL', 'PROJECT', 'DEPENDENCY', 'EXTERNAL'].map((s) =>
                '<option value="' + s + '"' + ((r.source || 'ALL') === s ? ' selected' : '') + '>'
                + ({ ALL: '全部', PROJECT: '项目', DEPENDENCY: '依赖', EXTERNAL: '外部' })[s]
                + '</option>').join('')
            + '</select>'
            + '<span class="nr-label">参数个数</span>'
            + '<input class="nr-paramcount" type="number" min="0" value="'
                + (r.paramCount != null ? r.paramCount : '') + '" placeholder="不限">'
            + '</div>'
            + '</div>'
        ).join('');
    }

    /** 从弹窗输入收集规则 */
    function collectNoiseRulesFromPanel() {
        const items = els.noiseRulesList.querySelectorAll('.nr-item');
        const out = [];
        items.forEach((item) => {
            const pcInput = item.querySelector('.nr-paramcount').value.trim();
            out.push({
                id: 'rule-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
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

    /** 将弹窗当前输入同步到内存 noiseRules 并实时刷新频次列表（无需保存即可预览过滤效果） */
    function applyNoiseRulesFromPanel() {
        noiseRules = collectNoiseRulesFromPanel();
        if (currentResult) renderFreqList(currentResult.methodFrequency || []);
    }

    els.btnNoiseRules.addEventListener('click', openNoiseRulesPanel);
    els.btnNoiseRulesClose.addEventListener('click', closeNoiseRulesPanel);
    els.noiseRulesOverlay.addEventListener('click', closeNoiseRulesPanel);
    // 刷新过滤：若弹窗已打开则先同步弹窗内的最新编辑（含未保存），再重新渲染频次列表
    els.btnFreqRefresh.addEventListener('click', () => {
        if (!els.noiseRulesPanel.hidden) {
            applyNoiseRulesFromPanel();
        } else if (currentResult) {
            renderFreqList(currentResult.methodFrequency || []);
        }
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.noiseRulesPanel.hidden) closeNoiseRulesPanel();
    });

    // 弹窗内任意输入变化都实时同步到内存并刷新频次列表（无需保存即可预览）
    els.noiseRulesList.addEventListener('change', (e) => {
        applyNoiseRulesFromPanel();
    });

    // 全选启用 / 全不选 / 反选
    els.btnNoiseRuleSelectAll.addEventListener('click', () => {
        els.noiseRulesList.querySelectorAll('.nr-enable input').forEach((cb) => { cb.checked = true; });
        applyNoiseRulesFromPanel();
    });
    els.btnNoiseRuleSelectNone.addEventListener('click', () => {
        els.noiseRulesList.querySelectorAll('.nr-enable input').forEach((cb) => { cb.checked = false; });
        applyNoiseRulesFromPanel();
    });
    els.btnNoiseRuleInvert.addEventListener('click', () => {
        els.noiseRulesList.querySelectorAll('.nr-enable input').forEach((cb) => { cb.checked = !cb.checked; });
        applyNoiseRulesFromPanel();
    });

    // 导出规则：下载 JSON 文件
    els.btnNoiseRuleExport.addEventListener('click', () => {
        window.location.href = 'api/noise-rules/export';
    });

    // 导入规则：触发文件选择
    els.btnNoiseRuleImport.addEventListener('click', () => {
        els.noiseRuleImportFile.value = '';
        els.noiseRuleImportFile.click();
    });
    els.noiseRuleImportFile.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;
        if (!confirm('导入将覆盖当前所有规则，确定继续？')) {
            els.noiseRuleImportFile.value = '';
            return;
        }
        const fd = new FormData();
        fd.append('file', file);
        fetch('api/noise-rules/import', { method: 'POST', body: fd })
            .then((r) => {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then((data) => {
                noiseRules = data || [];
                renderNoiseRulesList();
                if (currentResult) renderFreqList(currentResult.methodFrequency || []);
            })
            .catch((err) => alert('导入失败：' + err.message + '（请确认是合法的 noise-rules.json 文件）'));
    });

    els.btnNoiseRuleAdd.addEventListener('click', () => {
        noiseRules.push({
            id: 'rule-' + Date.now(),
            name: '新规则',
            methodPattern: '',
            classPattern: '',
            source: 'ALL',
            paramCount: null,
            enabled: true,
        });
        renderNoiseRulesList();
        applyNoiseRulesFromPanel();
    });

    els.noiseRulesList.addEventListener('click', (e) => {
        if (e.target.classList.contains('nr-del')) {
            const idx = parseInt(e.target.closest('.nr-item').dataset.idx, 10);
            noiseRules.splice(idx, 1);
            renderNoiseRulesList();
            applyNoiseRulesFromPanel();
        }
    });

    els.btnNoiseRuleReset.addEventListener('click', () => {
        if (!confirm('确定恢复默认规则？当前未保存的修改将丢失。')) return;
        fetch('api/noise-rules/reset', { method: 'POST' })
            .then((r) => r.json()).then((data) => {
                noiseRules = data || [];
                renderNoiseRulesList();
                if (currentResult) renderFreqList(currentResult.methodFrequency || []);
            });
    });

    els.btnNoiseRuleSave.addEventListener('click', () => {
        const rules = collectNoiseRulesFromPanel();
        fetch('api/noise-rules', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rules),
        }).then((r) => r.json()).then((data) => {
            noiseRules = data || [];
            closeNoiseRulesPanel();
            if (currentResult) renderFreqList(currentResult.methodFrequency || []);
        }).catch(() => alert('保存失败，请检查规则格式'));
    });

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
        method.textContent = node.method.display;

        const cls = document.createElement('span');
        cls.className = 'class';
        cls.textContent = node.method.className;

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
        row.appendChild(cls);
        row.appendChild(badges);

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
            row.addEventListener('click', () => setExpanded(kids.style.display === 'none'));
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
        const perRoot = [];
        currentResult.roots.forEach((root) => {
            const matches = [];
            if (matchNode(root, term, exact)) matches.push({ node: root, ancestors: [] });
            collectMatches(root, term, exact).forEach((m) => matches.push(m));
            if (matches.length > 0) perRoot.push({ root, matches });
        });

        const total = perRoot.reduce((s, p) => s + p.matches.length, 0);
        const entryCount = currentResult.roots.length;

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
            chip.querySelector('.chip-name').textContent =
                p.root.method ? p.root.method.display : '';
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

    // ------------------------------------------------------------------
    // 默认演示值：预填本工具自身，开箱即可点击"开始分析"
    // ------------------------------------------------------------------

    (async function loadDefaults() {
        try {
            const resp = await fetch('/api/defaults');
            if (!resp.ok) return;
            const d = await resp.json();
            if (!els.projectPath.value) els.projectPath.value = d.projectPath || '';
            if (!els.className.value) els.className.value = d.className || '';
            if (!els.methodName.value) els.methodName.value = d.methodName || '';
            if (els.projectPath.value) {
                els.projectInfo.className = 'hint';
                els.projectInfo.textContent = '已预填演示项目（本工具自身），点击"开始分析"即可；可改为你的项目路径';
            }
        } catch (e) { /* 静默失败，用户手填 */ }
    })();

    // 页面加载时拉取样板方法过滤规则
    loadNoiseRules();
})();
