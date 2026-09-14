/* Java 方法调用链分析工具 - 前端逻辑（原生 JS，无外部依赖） */
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
        maxDepth: $('#maxDepth'),
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

        // ---- 手动添加入口 Modal ----
        addEntryOverlay: $('#addEntryOverlay'),
        addEntryModal: $('#addEntryModal'),
        addEntryClose: $('#addEntryClose'),
        addEntryCancel: $('#addEntryCancel'),
        addEntryConfirm: $('#addEntryConfirm'),
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
        projectSearchChips: $('#projectSearchChips'),
        loading: $('#loading'),
        loadingText: $('#loadingText'),
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
    let expandFns = [];      // 全部展开/收起用
    let searchTimer = null;
    const nodeRegistry = new Map();  // 数据节点 → { rowEl, setExpanded }
    let hitRows = [];                // 当前搜索高亮的行
    let activeSearch = null;         // 当前打开的行内搜索栏 { bar, node }
    let freqFilter = 'ALL';          // 方法调用次数分析的来源筛选：ALL/PROJECT/DEPENDENCY/EXTERNAL
    let projectFreqMode = false;     // 频率区当前是否显示"项目级聚合频率"（批量全量加载后）
    let batchModel = null;           // 批量全量加载模型：{batch, projectId, entries, done,total,failed, loaded, index, projectFreq}
    let noiseRules = [];             // 样板方法过滤规则（当前层级的，从后端加载）
    let noiseRuleScope = 'global';   // 当前查看/编辑的层级：'global' | 'project'
    let currentProjectId = null;     // 当前选中的项目 id（null = 未选中）

    // ==============================================================
    // 项目列表 / 视图切换 / 持久化项目管理
    // ==============================================================

    function switchView(to) {
        const isProjects = to === 'projects';
        els.viewProjects.hidden = !isProjects;
        els.viewAnalyze.hidden = isProjects;
        els.navToProjects.classList.toggle('active', isProjects);
        els.navToAnalyze.classList.toggle('active', !isProjects);
    }

    async function refreshProjectList() {
        try {
            const resp = await fetch('/api/projects');
            if (!resp.ok) return;
            const list = await resp.json();
            renderProjectList(list);
        } catch (e) { /* ignore */ }
    }

    function renderProjectList(list) {
        list = list || [];
        els.projectsCount.textContent = list.length + ' 个';
        if (list.length === 0) {
            els.projectsEmpty.hidden = false;
            els.projectsList.innerHTML = '';
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
        const listResp = await fetch('/api/projects');
        const list = await listResp.json();
        const p = list.find((x) => x.id === id);
        if (!p) { alert('项目不存在或已被删除'); refreshProjectList(); return; }
        await postJson('/api/projects/' + encodeURIComponent(id) + '/open', {});
        currentProjectId = id;
        currentResult = null;
        currentRequest = null;
        currentBatchSummary = null;
        currentCacheFileName = null;
        if (p.type === 'GIT') {
            gitProjectPath = p.projectPath;
            sourceMode = 'git';
        } else {
            gitProjectPath = null;
            sourceMode = 'local';
            els.projectPath.value = p.projectPath || '';
        }
        setSourceMode(sourceMode);
        els.currentProjectBadge.textContent = p.type || 'LOCAL';
        els.currentProjectBadge.className = 'badge source-' + (p.type === 'GIT' ? 'dependency' : 'project').toLowerCase();
        els.currentProjectName.textContent = p.name || '(未命名)';
        els.currentProjectPath.textContent = p.projectPath || '';
        els.entrySection.hidden = true;
        els.resultSection.hidden = true;
        els.freqSection.hidden = true;
        clearError();
        switchView('analyze');

        // 先加载 Step2 已确认的交易入口清单（主体，先进来就能看到）
        // 其内部会再触发 autoLoadCacheForProject（加载轻量批量索引 / 单入口结果）
        await autoLoadEntryList(id);
    }

    /** 加载项目级已确认的交易入口清单（Step 2） */
    async function autoLoadEntryList(projectId) {
        try {
            const resp = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/entries');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            currentEntryList = await resp.json();
            currentCandidates = [];
            renderEntryList();
            // Step2 清单变了 → 刷新 Step3 提示（让后端重新算 currentEntryCount + dirty）
            await autoLoadCacheForProject(projectId);
        } catch (e) {
            console.warn('[Step2] 入口清单加载失败:', e);
            currentEntryList = { confirmed: [], excluded: [] };
            renderEntryList();
        }
    }

    /** 加载单份缓存 + 更新 Step3 状态提示条 */
    async function autoLoadCacheForProject(projectId) {
        try {
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

    async function fetchJson(url) {
        const resp = await fetch(url);
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
        // 批量分析结果 = 轻量清单索引（kind === 'batch'）→ 渲染入口清单视图
        if (result && result.kind === 'batch') {
            renderBatchSummary(result);
            return;
        }
        // 单个入口的完整结果
        renderSingleEntryResult(result, null);
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
        renderStats(batch.stats || {});
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
                + '<span class="bt-method">' + escapeHtml(entry.className || '')
                + (entry.methodName ? '#' + escapeHtml(entry.methodName) : '') + '</span>'
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
        st.roots = rootsOf(result);
        st.roots.forEach((root) => st.body.appendChild(nodeEl(root, 0)));
        st.rendered = true;
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
            index: null,      // methodKey → { method, source, entries:Set<entryIdx> }
            projectFreq: [],  // 项目级聚合频率
        };
        projectFreqMode = true;
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

    /** 全量加载收尾：建索引/频率，置加载完成态并展示项目级面板 */
    function finalizeBatchLoad() {
        const loaded = batchModel.entries.filter((s) => s.result).length;
        batchModel.loaded = true;
        batchModel.index = buildProjectIndex(batchModel.entries);
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

    /** 每个入口 graph.methods 本身即源自根的可达方法闭集 → 直接遍历建 Map[key]→(method, entries) */
    function buildProjectIndex(entries) {
        const index = new Map();
        entries.forEach((slot, idx) => {
            const g = slot.result && slot.result.graph;
            if (!g || !g.methods) return;
            g.methods.forEach((m) => {
                const key = batchMethodKey(m);
                let rec = index.get(key);
                if (!rec) { rec = { method: m, source: m.source, entries: new Set() }; index.set(key, rec); }
                rec.entries.add(idx);
            });
        });
        return index;
    }

    /** 项目级频率：跨入口按边表入度求和（口径同后端 collectGraphStats，排除根方法） */
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
                    rec = { callCount: 0, source: to.source, display: to.display, callers: new Map() };
                    agg.set(key, rec);
                }
                rec.callCount++;
                const from = methods[e.from];
                if (from && rec.callers.size < 20) {
                    const d = from.display || '?';
                    if (!rec.callers.has(d)) rec.callers.set(d, e.line || 0);
                }
            }
        });
        return Array.from(agg.values())
            .sort((a, b) => b.callCount - a.callCount || (a.display < b.display ? -1 : 1))
            .map((r) => ({
                method: r.display,
                source: r.source,
                callCount: r.callCount,
                callers: Array.from(r.callers.entries()).map(([caller, line]) => ({ caller, line })),
            }));
    }

    /** 频率区数据源：项目级聚合 或 当前单入口的 methodFrequency */
    function currentFreqData() {
        if (projectFreqMode && batchModel && batchModel.projectFreq) return batchModel.projectFreq;
        return (currentResult && currentResult.methodFrequency) || [];
    }

    /** 渲染项目级频率（复用 renderFreqList，来源过滤/样板规则自动生效） */
    function renderProjectFreq() {
        if (!batchModel || !batchModel.projectFreq) return;
        projectFreqMode = true;
        els.freqSection.hidden = false;
        renderFreqList(batchModel.projectFreq);
    }

    // ---------- 项目级方法搜索：搜方法 → 列出调用它的所有交易入口 ----------

    function clearProjectSearch() {
        els.projectSearchResult.textContent = '';
        els.projectSearchResult.className = 'search-result';
        els.projectSearchChips.innerHTML = '';
        clearSearchHighlights();
    }

    /** 清除上次搜索的高亮 */
    function clearSearchHighlights() {
        document.querySelectorAll('.node-row.hit').forEach((el) => el.classList.remove('hit'));
    }

    function runProjectSearch() {
        const term = els.projectSearchInput.value.trim();
        clearProjectSearch();
        if (!term) return;
        if (!batchModel || !batchModel.index || !batchModel.loaded) {
            els.projectSearchResult.className = 'search-result err';
            els.projectSearchResult.textContent = '全量加载尚未完成，请稍后再搜';
            return;
        }
        const exact = els.projectSearchMode.value === 'exact';
        const needle = term.toLowerCase();
        const hits = [];
        batchModel.index.forEach((rec) => {
            const m = rec.method || {};
            let match;
            if (exact) {
                match = m.name === term || m.display === term
                    || ((m.owner || '').replace(/\//g, '.') + '.' + m.name) === term;
            } else {
                match = [m.name, m.display, m.owner].some((v) => v && v.toLowerCase().indexOf(needle) >= 0);
            }
            if (match) hits.push(rec);
        });
        hits.sort((a, b) => a.method.display.localeCompare(b.method.display));

        if (hits.length === 0) {
            els.projectSearchResult.className = 'search-result err';
            els.projectSearchResult.textContent = '未命中 —— 项目内没有方法匹配 "' + term + '"';
            return;
        }

        // 需要展开+高亮的入口（去重，按原始行号）
        const revealEntries = [];
        const seenEntries = new Set();
        hits.forEach((rec) => Array.from(rec.entries).forEach((idx) => {
            if (seenEntries.has(idx)) return;
            seenEntries.add(idx);
            revealEntries.push(idx);
        }));

        els.projectSearchResult.className = 'search-result ok';
        els.projectSearchResult.textContent = '命中 ' + hits.length + ' 个方法 · 已自动展开并高亮 '
            + revealEntries.length + ' 个入口中的命中方法（点行可收起）';

        hits.forEach((rec) => {
            const block = document.createElement('div');
            block.className = 'project-hit';
            const label = document.createElement('div');
            label.className = 'project-hit-method';
            label.appendChild(document.createTextNode(rec.method.display || batchMethodKey(rec.method)));
            label.appendChild(badgeDom('source-' + (rec.source || '').toLowerCase(),
                SOURCE_LABEL[rec.source] || rec.source || '外部'));
            block.appendChild(label);
            const chips = document.createElement('div');
            chips.className = 'search-chips';
            Array.from(rec.entries).forEach((entryIdx) => {
                const e = (batchModel.entries[entryIdx] || {}).entry || {};
                const chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'search-chip';
                chip.innerHTML = '<span class="chip-name"></span>'
                    + '<span class="chip-count">' + (entryIdx + 1) + '</span>';
                chip.querySelector('.chip-name').textContent = (e.className || '')
                    + (e.methodName ? '#' + e.methodName : '');
                chip.title = '展开/收起该入口调用链';
                chip.addEventListener('click', () => toggleEntryBody(entryIdx));
                chips.appendChild(chip);
            });
            block.appendChild(chips);
            els.projectSearchChips.appendChild(block);
        });

        // 自动展开命中入口的调用链并在命中的方法节点上高亮
        let firstHitRow = null;
        revealEntries.forEach((idx) => {
            const r = revealEntryMatches(idx, term, exact);
            if (r && !firstHitRow) firstHitRow = r;
        });
        if (firstHitRow) firstHitRow.scrollIntoView({ block: 'center', behavior: 'smooth' });
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

    /** 展开某入口行内树并高亮命中方法；返回第一个命中节点的 rowEl（便于滚动定位） */
    function revealEntryMatches(entryIdx, term, exact) {
        const st = batchRowStates[entryIdx];
        if (!st) return null;
        // 确保行内树已渲染（全量加载已完成，缓存中应已有结果）
        if (!st.rendered) {
            // 同步建树（结果已在内存）；没有结果则跳过该入口
            if (!buildEntryBodySync(st)) return null;
        }
        st.open = true;
        st.rowEl.classList.add('open');
        st.body.style.display = '';
        updateCaret(st, true);

        const g = st.result && st.result.graph;
        if (!g || !g.methods) return null;

        // 命中方法 id 集合 + 祖先集合（只需展开包含命中的路径，避免整树拉伸）
        const targets = [];
        g.methods.forEach((m, i) => { if (matchRawMethod(m, term, exact)) targets.push(i); });
        if (targets.length === 0) return null;
        const targetSet = new Set(targets);

        const parentMap = new Map();
        (g.edges || []).forEach((e) => {
            const arr = parentMap.get(e.to) || [];
            arr.push(e.from);
            parentMap.set(e.to, arr);
        });
        const revealSet = new Set();
        const stackP = targets.slice();
        stackP.forEach((t) => revealSet.add(t));
        while (stackP.length) {
            const cur = stackP.pop();
            (parentMap.get(cur) || []).forEach((p) => { if (!revealSet.has(p)) { revealSet.add(p); stackP.push(p); } });
        }

        let firstHit = null;
        (function walk(node) {
            if (targetSet.has(node.gid)) {
                const en = nodeRegistry.get(node);
                if (en) {
                    en.rowEl.classList.add('hit');
                    if (!firstHit) firstHit = en.rowEl;
                }
            }
            if (revealSet.has(node.gid)) {
                const en = nodeRegistry.get(node);
                if (en && en.setExpanded) en.setExpanded(true);
                (node.children || []).forEach(walk);
            }
        })(st.roots[0]);

        return firstHit;
    }

    /** 在内存结果已就绪时同步建树（不拉网络），供搜索批量展开使用 */
    function buildEntryBodySync(st) {
        const slot = batchModel && batchModel.entries[st.idx];
        const result = slot && slot.result;
        if (!result) return null;
        st.result = result;
        st.body.innerHTML = '';
        st.roots = rootsOf(result);
        st.roots.forEach((root) => st.body.appendChild(nodeEl(root, 0)));
        st.rendered = true;
        return result;
    }

    els.btnProjectSearch.addEventListener('click', runProjectSearch);
    els.projectSearchInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') runProjectSearch(); });
    els.btnProjectSearchClear.addEventListener('click', clearProjectSearch);

    // ------------------------------------------------------------------
    // 图结构适配层：把 schema=2 的 result.graph 惰性还原为树视图节点。
    // 不整体物化冗余树：每个节点的 children 是 getter，首次访问才构建子节点；
    // 环方法作为叶子返回（子树已在上层路径，避免全局搜索无限递归）。
    // ------------------------------------------------------------------

    // 结果对象 → 其惰性根节点缓存的映射（保证渲染与全局搜索共用同一对象身份，命中 nodeRegistry）
    let _adapterCache = { node: null, roots: [] };

    function rootsOf(result) {
        if (_adapterCache.node === result) return _adapterCache.roots;
        // 兼容退路：无 graph 的旧结果（正常不再发生）
        if (!result || !result.graph) {
            const legacy = (result && result.roots) || [];
            return legacy;
        }
        const g = result.graph;
        const adj = {};
        (g.edges || []).forEach((e) => {
            (adj[e.from] = adj[e.from] || []).push(e);
        });
        function methodView(m) {
            const owner = m.owner || '';
            const cn = owner.replace(/\//g, '.');
            const lastSlash = owner.lastIndexOf('/');
            return {
                name: m.name,
                display: m.display,
                className: cn,
                simpleClassName: lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner,
            };
        }
        function makeNode(id, parentEdge, depth) {
            const m = g.methods[id];
            const view = methodView(m);
            let kidsCache = null;
            const node = {
                gid: id,             // 图内方法 id：批量搜索/高亮定位用
                source: m.source,
                invokeType: parentEdge ? parentEdge.invoke : null,
                line: parentEdge ? (parentEdge.line || 0) : 0,
                cycle: !!m.cycle,
                truncated: false,
                method: view,
                get children() {
                    if (kidsCache) return kidsCache;
                    if (m.cycle) { kidsCache = []; return kidsCache; }
                    kidsCache = (adj[id] || []).map((e) => makeNode(e.to, e, depth + 1));
                    return kidsCache;
                }
            };
            return node;
        }
        const roots = (g.roots || []).map((id) => makeNode(id, null, 0));
        _adapterCache = { node: result, roots: roots };
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
        renderStats(result.stats);
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

    function loadNoiseRules() {
        fetch(noiseRulesApiUrl()).then((r) => r.json()).then((data) => {
            noiseRules = data || [];
            if (currentResult) renderFreqList(currentResult.methodFrequency || []);
        }).catch(() => { noiseRules = []; });
    }

    function openNoiseRulesPanel() {
        // 重置 scope 到全局（更安全）
        noiseRuleScope = 'global';
        document.querySelectorAll('.nr-scope-tab').forEach(t =>
            t.classList.toggle('active', t.dataset.scope === 'global'));
        // 加载
        loadNoiseRules();
        // 更新 hint
        updateNoiseRulesScopeHint();
        renderNoiseRulesList();
        els.noiseRulesPanel.hidden = false;
        els.noiseRulesOverlay.hidden = false;
    }

    function updateNoiseRulesScopeHint() {
        const hint = document.getElementById('nrScopeHint');
        if (!hint) return;
        if (noiseRuleScope === 'global') {
            hint.textContent = '全局规则对所有项目生效';
        } else {
            const pp = currentProjectPath();
            if (pp) {
                hint.textContent = '当前项目: ' + pp;
            } else {
                hint.textContent = '⚠ 未选择项目，无法查看项目级规则';
            }
        }
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
        let url = 'api/noise-rules/export';
        if (noiseRuleScope === 'project') {
            const pp = currentProjectPath();
            if (pp) url += '?projectPath=' + encodeURIComponent(pp);
        }
        window.location.href = url;
    });

    // 导入规则：触发文件选择
    els.btnNoiseRuleImport.addEventListener('click', () => {
        els.noiseRuleImportFile.value = '';
        els.noiseRuleImportFile.click();
    });
    els.noiseRuleImportFile.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;
        if (!confirm('导入将覆盖当前层级的所有规则，确定继续？')) {
            els.noiseRuleImportFile.value = '';
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
                renderNoiseRulesList();
                if (currentResult) renderFreqList(currentResult.methodFrequency || []);
            });
    });

    els.btnNoiseRuleSave.addEventListener('click', () => {
        const rules = collectNoiseRulesFromPanel();
        fetch(noiseRulesApiUrl(), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rules),
        }).then((r) => r.json()).then((data) => {
            noiseRules = data || [];
            closeNoiseRulesPanel();
            if (currentResult) renderFreqList(currentResult.methodFrequency || []);
        }).catch(() => alert('保存失败，请检查规则格式'));
    });

    // Tab 切换（全局 ↔ 项目级）
    document.querySelectorAll('.nr-scope-tab').forEach((tab) => {
        tab.addEventListener('click', () => {
            if (tab.dataset.scope === noiseRuleScope) return; // 已激活
            if (noiseRuleScope === 'project' && !currentProjectPath()) {
                alert('⚠ 未选择项目，无法切换到项目级规则。请先在项目列表中进入一个项目。');
                return;
            }
            noiseRuleScope = tab.dataset.scope;
            document.querySelectorAll('.nr-scope-tab').forEach(t =>
                t.classList.toggle('active', t === tab));
            loadNoiseRules();
            updateNoiseRulesScopeHint();
        });
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

    els.btnBackToList.addEventListener('click', () => {
        backToBatchList();
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
        } catch (e) { /* 静默失败，用户手填 */ }
    })();

    // 页面加载时拉取样板方法过滤规则
    loadNoiseRules();

    // 启动：先加载项目列表，默认停在项目列表视图
    refreshProjectList();

    // 页面加载时恢复最近一个未过期的 Git 任务（在途或刚完成）
    fetch('/api/git/latest').then(r => r.ok ? r.json() : null).then(st => {
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
            els.entryConfirmedList.innerHTML = confirmed.map(item => renderEntryRow(item, 'confirmed')).join('');
        }

        // excluded 折叠区
        if (excluded.length > 0) {
            els.entryExcludedDetails.hidden = false;
            els.entryExcludedCount.textContent = '(' + excluded.length + ')';
            els.entryExcludedList.innerHTML = excluded.map(item => renderEntryRow(item, 'excluded')).join('');
        } else {
            els.entryExcludedDetails.hidden = true;
        }
    }

    function renderEntryRow(item, mode) {
        const fullCls = item.className || '';
        const method = item.methodName || '';
        const desc = item.descriptor || '';
        const groupBadge = item.group ? `<span class="entry-badge group-${item.group}">${item.group}</span>` : '';
        const sourceBadge = item.source === 'MANUAL'
            ? '<span class="entry-badge group-MANUAL">手动</span>'
            : '';

        // 完整签名：全类名#方法名descriptor（descriptor 为空时省略）
        const fullSig = fullCls
            + (method ? '#' + method : '')
            + (desc ? desc : (method ? '()' : ''));

        if (mode === 'excluded') {
            return `<div class="entry-row" data-key="${entryKey(item)}">
                ${sourceBadge}${groupBadge}
                <span class="entry-sig" title="${escapeHtml(fullSig)}">${escapeHtml(fullSig)}</span>
                <button class="entry-restore-btn" onclick="restoreEntry('${entryKey(item).replace(/'/g, "\\'")}')">恢复</button>
            </div>`;
        }
        return `<div class="entry-row" data-key="${entryKey(item)}">
            ${sourceBadge}${groupBadge}
            <span class="entry-sig" title="${escapeHtml(fullSig)}">${escapeHtml(fullSig)}</span>
            <button class="entry-exclude-btn" onclick="excludeEntry('${entryKey(item).replace(/'/g, "\\'")}')">排除</button>
        </div>`;
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"]/g, c =>
            ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]));
    }

    // ---- Step 2 按钮事件 ----

    // 🔍 自动扫描 → diff → 直接合并进清单 → 刷新
    els.btnEntryScan.addEventListener('click', async () => {
        if (!currentProjectId) { showError('请先进入项目'); return; }
        clearError();
        showLoading('扫描入口中...');
        try {
            const resp = await postJson(
                '/api/projects/' + currentProjectId + '/entries/scan', {});
            const candidates = resp.candidates || [];
            if (candidates.length > 0) {
                await postJson(
                    '/api/projects/' + currentProjectId + '/entries/merge', candidates);
            }
            await autoLoadEntryList(currentProjectId);
            hideLoading();
            const total = (currentEntryList && currentEntryList.confirmed) ? currentEntryList.confirmed.length : 0;
            showError('✓ 扫描完成，新增 ' + candidates.length + ' 个，清单共 ' + total + ' 个', true);
        } catch (e) {
            hideLoading();
            showError('扫描失败: ' + e.message);
        }
    });

    // ➕ 手动添加 → Modal
    let addEntryVerified = false;  // 当前是否已通过检测
    function openAddEntryModal() {
        els.addEntryClass.value = '';
        els.addEntryMethod.innerHTML = '<option value="">留空（整个类所有方法都作为入口）</option>';
        els.addEntryMethodText.value = '';
        els.addEntryPaste.value = '';
        setVerifyStatus('未检测', '');
        addEntryVerified = false;
        els.addEntryConfirm.disabled = true;
        els.addEntryOverlay.hidden = false;
        els.addEntryModal.hidden = false;
        els.addEntryClass.focus();
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
    // 输入变动 → 重置检测状态
    function resetVerify() {
        if (addEntryVerified) {
            addEntryVerified = false;
            els.addEntryConfirm.disabled = true;
            setVerifyStatus('已修改，请重新检测', 'warn');
        }
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
        //       com.demo.OrderController#createOrder(LOrder;)V
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
            return { className: cls, methodName: method, descriptor: afterHash.substring(parenIdx, closeParen + 1) };
        }
        return { className: cls, methodName: method, descriptor: '' };
    }

    // 🔎 检测按钮
    els.addEntryVerify.addEventListener('click', async () => {
        if (!currentProjectId) { showError('请先进入项目'); return; }
        let className = '', methodName = '', descriptor = '';
        const pasteRaw = els.addEntryPaste.value.trim();
        if (pasteRaw) {
            const p = parseEntryString(pasteRaw);
            className = p.className; methodName = p.methodName || ''; descriptor = p.descriptor || '';
        } else {
            className = els.addEntryClass.value.trim();
            methodName = els.addEntryMethodText.value.trim() || els.addEntryMethod.value.trim();
            if (els.addEntryMethod.value) {
                const opt = els.addEntryMethod.querySelector(`option[value="${els.addEntryMethod.value}"]`);
                if (opt && opt.dataset.desc) descriptor = opt.dataset.desc;
            }
        }
        if (!className) { setVerifyStatus('请先填类名', 'err'); return; }
        const path = currentProjectPath();
        if (!path) { setVerifyStatus('项目路径无效', 'err'); return; }

        els.addEntryVerify.disabled = true;
        setVerifyStatus('检测中...', '');
        try {
            const url = '/api/classes/verify?path=' + encodeURIComponent(path)
                + '&class=' + encodeURIComponent(className)
                + (methodName ? '&method=' + encodeURIComponent(methodName) : '')
                + (descriptor ? '&descriptor=' + encodeURIComponent(descriptor) : '');
            const resp = await fetch(url);
            const data = await resp.json();
            if (data.ok) {
                setVerifyStatus(data.reason || '✓ 存在', data.multipleOverloads ? 'warn' : 'ok');
                addEntryVerified = true;
                els.addEntryConfirm.disabled = false;
                // 如果后端帮补了唯一 descriptor → 自动填回去
                if (data.descriptor && !descriptor) {
                    // 让用户知道 descriptor 被自动补了
                }
            } else {
                setVerifyStatus('✗ ' + (data.reason || '不存在'), 'err');
                addEntryVerified = false;
                els.addEntryConfirm.disabled = true;
            }
        } catch (e) {
            setVerifyStatus('检测失败: ' + e.message, 'err');
        } finally {
            els.addEntryVerify.disabled = false;
        }
    });

    // 输入变动 → 重置检测状态
    els.addEntryClass.addEventListener('input', resetVerify);
    els.addEntryMethodText.addEventListener('input', resetVerify);
    els.addEntryMethod.addEventListener('change', resetVerify);
    els.addEntryPaste.addEventListener('input', resetVerify);

    // 加入清单 —— 必须已检测通过
    els.addEntryConfirm.addEventListener('click', async () => {
        if (!currentProjectId) { showError('请先进入项目'); closeAddEntryModal(); return; }
        let className = '';
        let methodName = '';
        let descriptor = '';

        // 优先从快捷粘贴解析
        const pasteRaw = els.addEntryPaste.value.trim();
        if (pasteRaw) {
            const p = parseEntryString(pasteRaw);
            className = p.className;
            methodName = p.methodName || '';
            descriptor = p.descriptor || '';
        } else {
            className = els.addEntryClass.value.trim();
            // 方法名：input 优先，下拉其次
            methodName = els.addEntryMethodText.value.trim()
                || els.addEntryMethod.value.trim();
            if (els.addEntryMethod.value) {
                const opt = els.addEntryMethod.querySelector(`option[value="${els.addEntryMethod.value}"]`);
                if (opt && opt.dataset.desc) descriptor = opt.dataset.desc;
            }
        }

        if (!className) {
            showError('请先填类名');
            els.addEntryClass.focus();
            return;
        }

        try {
            const resp = await postJson('/api/projects/' + currentProjectId + '/entries/add', {
                className, methodName, descriptor
            });
            closeAddEntryModal();
            await autoLoadEntryList(currentProjectId);
            const label = className.split('.').pop() + (methodName ? '#' + methodName : '');
            showError(resp.added ? '✓ 已加入清单: ' + label : label + ' 已存在于清单', true);
        } catch (e) {
            showError('添加失败: ' + e.message);
        }
    });

    // 排除（confirmed → excluded）
    window.excludeEntry = function (key) {
        if (!currentProjectId) return;
        const reason = prompt('排除原因（可选，默认"用户排除"）:', '用户排除');
        if (reason === null) return;
        postJson('/api/projects/' + currentProjectId + '/entries/exclude', {
            key: key, reason: reason
        }).then(() => autoLoadEntryList(currentProjectId))
          .catch(e => showError('排除失败: ' + e.message));
    };

    // 恢复（excluded → confirmed）
    window.restoreEntry = function (key) {
        if (!currentProjectId) return;
        postJson('/api/projects/' + currentProjectId + '/entries/restore', { key: key })
            .then(() => autoLoadEntryList(currentProjectId))
            .catch(e => showError('恢复失败: ' + e.message));
    };

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

    // 页面初始化：切到项目列表视图 + 自动刷新
    switchView('projects');
    refreshProjectList();

})();
