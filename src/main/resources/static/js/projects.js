/*
 * projects.js —— 项目列表 / 进入 / 删除 + Git 拉取轮询与分支切换（OPT-27 C3 簇）
 * ================================================================================
 * 纯搬运自 app.js 原 :94-402（projects 段）与 :406-802（Git 段），函数体逐行照搬，
 * 仅三类机械适配（零行为变化）：
 *   1. 闭包共享状态 → App.state.*（字段清单与写入方/读取方/清零归属见 js/state.js 头注释）
 *   2. 调用未搬簇函数（loadNoiseRules/loadScanStrategy/renderEntryList/renderResult/
 *      guideRefresh）→ init(hooks) 晚绑定注入（方案 §4.3）
 *   3. 事件绑定收进 init()，保持原 IIFE 内注册的相对顺序（监听注册无相互依赖，时序无行为影响）
 * 模块内部私有状态（有意不进 App.state，理由见 state.js 头注释）：
 *   projectIndex / gitSwitchTimer（句柄）/ gitRefsCache
 */
(function (root, factory) {
    'use strict';
    const Projects = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Projects;            // Node（契约测试可 require）
    }
    if (root) {
        root.Projects = Projects;             // 浏览器（state.js / ui.js 之后、app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // 基础 UI 薄引用（与 app.js 顶部同模式；els 已由 ui.js 在加载时构建一次）
    const els = Ui.els;
    const switchView = Ui.switchView;
    const showError = Ui.showError;
    const clearError = Ui.clearError;
    const showToast = Ui.showToast;
    const showConfirm = Ui.showConfirm;
    const showLoading = Ui.showLoading;
    const loadingSetStep = Ui.loadingSetStep;
    const hideLoading = Ui.hideLoading;
    const escapeHtml = Ui.escapeHtml;
    const postJson = Api.postJson;
    const fetchJson = Api.fetchJson;

    // 晚绑定钩子：app.js 在 init 时注入（函数声明提升，注入点必有定义）
    const hooks = {
        guideRefresh: null,        // 新手引导刷新（app.js 单一来源）
        loadNoiseRules: null,      // C7 噪声簇（未搬）
        loadScanStrategy: null,    // C8 策略簇（未搬）
        renderEntryList: null,     // C4 清单簇（未搬）
        renderResult: null         // C5 结果簇（未搬）
    };

    /** init：注入跨簇依赖 + 注册事件绑定（顺序 = 原 IIFE 行号顺序 349/363/364/372/373/418/419/559/794/799） */
    function init(h) {
        hooks.guideRefresh = h.guideRefresh;
        hooks.loadNoiseRules = h.loadNoiseRules;
        hooks.loadScanStrategy = h.loadScanStrategy;
        hooks.renderEntryList = h.renderEntryList;
        hooks.renderResult = h.renderResult;
        bindEvents();
    }

    // ==============================================================
    // 项目列表 / 视图切换 / 持久化项目管理
    // ==============================================================

    /** 最近一次项目列表索引：进入项目时直接取用，避免重复的全量 /api/projects 请求 */
    let projectIndex = {};
    let gitSwitchTimer = null;       // 分支/Tag 切换任务轮询定时器（句柄，模块私有，见头注释）
    let gitRefsCache = null;         // 最近一次分支/Tag 下拉数据 {branches, tags, defaultBranch}

    async function refreshProjectList() {
        try {
            const resp = await fetch('/api/projects');
            if (!resp.ok) {
                if (App.state.guideProjectCount == null) App.state.guideProjectCount = 0;
                hooks.guideRefresh();
                return;
            }
            const list = await resp.json();
            renderProjectList(list);
        } catch (e) {
            if (App.state.guideProjectCount == null) App.state.guideProjectCount = 0;
            hooks.guideRefresh();
        }
    }

    function renderProjectList(list) {
        list = list || [];
        projectIndex = {};
        list.forEach((p) => { projectIndex[p.id] = p; });
        App.state.guideProjectCount = list.length;
        els.projectsCount.textContent = list.length + ' 个';
        if (list.length === 0) {
            els.projectsEmpty.hidden = false;
            els.projectsList.innerHTML = '';
            hooks.guideRefresh();
            return;
        }
        els.projectsEmpty.hidden = true;
        els.projectsList.innerHTML = list.map((p) => projectCardHtml(p)).join('');
        els.projectsList.querySelectorAll('.pc-enter').forEach((btn) => {
            btn.addEventListener('click', () => enterProject(btn.dataset.id));
        });
        els.projectsList.querySelectorAll('.pc-delete').forEach((btn) => {
            btn.addEventListener('click', async () => {
                const name = btn.dataset.name;
                const ok = await showConfirm(
                    '确定移除项目「' + name + '」？\n（只移除记录，不删除磁盘文件）',
                    '移除项目');
                if (ok) deleteProject(btn.dataset.id);
            });
        });
        hooks.guideRefresh();
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
        if (!p) { showToast('项目不存在或已被删除', 'error', 5000); refreshProjectList(); return; }

        const isGit = p.type === 'GIT';
        const steps = ['读取项目信息', '同步过滤规则与扫描策略'];
        if (isGit) steps.push('加载 Git 分支信息');
        steps.push('加载交易入口清单');
        const iGit = isGit ? 2 : -1;
        const iEntries = isGit ? 3 : 2;
        showLoading('正在进入项目…（工程较大时首次加载会慢一些）', { steps: steps });
        try {
            loadingSetStep(0, 'active', '打开项目');
            const opened = await postJson('/api/projects/' + encodeURIComponent(id) + '/open', {});
            // 后端会顺带补齐 currentRef 等字段，用最新记录覆盖本地缓存（否则信息栏读到旧值）
            if (opened && opened.id) {
                Object.assign(p, opened);
                projectIndex[id] = p;
            }
            App.state.currentProjectId = id;
            App.state.currentResult = null;
            App.state.currentRequest = null;
            App.state.currentBatchSummary = null;
            App.state.currentCacheFileName = null;
            if (isGit) {
                App.state.gitProjectPath = p.projectPath;
                App.state.sourceMode = 'git';
            } else {
                App.state.gitProjectPath = null;
                App.state.sourceMode = 'local';
                els.projectPath.value = p.projectPath || '';
            }
            setSourceMode(App.state.sourceMode);
            els.currentProjectBadge.textContent = p.type || 'LOCAL';
            els.currentProjectBadge.className = 'badge source-' + (isGit ? 'dependency' : 'project').toLowerCase();
            els.currentProjectName.textContent = p.name || '(未命名)';
            els.currentProjectPath.textContent = p.projectPath || '';

            // 项目切换后先同步两层过滤规则（项目路径变化，项目层规则需重新拉取），再加载清单
            loadingSetStep(1, 'active');
            await hooks.loadNoiseRules();
            await hooks.loadScanStrategy();

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
            App.state.currentEntryList = await resp.json();
            App.state.currentCandidates = [];
            hooks.renderEntryList();
            // Step2 清单变了 → 刷新 Step3 提示（让后端重新算 currentEntryCount + dirty）
            await autoLoadCacheForProject(projectId, onNote);
        } catch (e) {
            console.warn('[Step2] 入口清单加载失败:', e);
            App.state.currentEntryList = { confirmed: [], excluded: [] };
            hooks.renderEntryList();
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
                App.state.currentResult = data.result;
                hooks.renderResult(data.result);
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
        App.state.guideHasResult = !!cacheInfo.hasCache;
        App.state.guideResultStale = !!cacheInfo.dirty;
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
        hooks.guideRefresh();
    }

    async function deleteProject(id) {
        try {
            const resp = await fetch('/api/projects/' + encodeURIComponent(id), { method: 'DELETE' });
            if (!resp.ok) { const d = await resp.json(); throw new Error(d.error || '删除失败'); }
            if (App.state.currentProjectId === id) {
                App.state.currentProjectId = null;
                switchView('projects');
            }
            refreshProjectList();
        } catch (e) { showToast('✕ 删除失败: ' + e.message, 'error', 5000); }
    }

    // ------------------------------------------------------------------
    // 项目来源切换：本地路径 / Git 仓库
    // ------------------------------------------------------------------

    function setSourceMode(mode) {
        App.state.sourceMode = mode;
        els.tabLocal.classList.toggle('active', mode === 'local');
        els.tabGit.classList.toggle('active', mode === 'git');
        els.paneLocal.hidden = mode !== 'local';
        els.paneGit.hidden = mode !== 'git';
    }

    /** 当前生效的项目路径：Git 模式取拉取编译产物，本地模式取输入框 */
    function currentProjectPath() {
        if (App.state.sourceMode === 'git') return App.state.gitProjectPath;
        return els.projectPath.value.trim();
    }

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

    /** 停止 Git 拉取轮询。gitPollTimer 句柄存于 App.state（clear 责任见 state.js 头注释）：
     *  先判空再 clear 并置 null，可安全重复调用；btnGitPrepare 重入前也先调本函数。 */
    function stopGitPoll() {
        if (App.state.gitPollTimer) { clearInterval(App.state.gitPollTimer); App.state.gitPollTimer = null; }
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
                App.state.gitProjectPath = st.projectPath;
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

    /** 停止 Git 分支切换轮询。gitSwitchTimer 为本模块私有句柄（不进 App.state，
     *  理由见 state.js 头注释）：先判空再 clear 并置 null，可安全重复调用。 */
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

    // ------------------------------------------------------------------
    // 事件绑定（init 时注册；顺序 = 原 app.js IIFE 内行号顺序）
    // ------------------------------------------------------------------

    function bindEvents() {
        // 原 :349
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

        // 原 :363
        els.btnRefreshProjects.addEventListener('click', refreshProjectList);

        // 原 :364
        els.btnBackToProjects.addEventListener('click', () => {
            App.state.currentProjectId = null;
            App.state.noiseRuleMode = 'panel';
            els.entrySection.hidden = true;
            els.resultSection.hidden = true;
            els.freqSection.hidden = true;
            switchView('projects');
        });

        // 原 :372
        els.navToProjects.addEventListener('click', () => { switchView('projects'); refreshProjectList(); });

        // 原 :373
        els.navToAnalyze.addEventListener('click', () => {
            if (!App.state.currentProjectId) { showToast('请先在项目列表中选择一个项目', 'warn'); switchView('projects'); return; }
            switchView('analyze');
        });

        // 原 :418
        els.tabLocal.addEventListener('click', () => setSourceMode('local'));

        // 原 :419
        els.tabGit.addEventListener('click', () => setSourceMode('git'));

        // 原 :559
        els.btnGitPrepare.addEventListener('click', async () => {
            clearError();
            const repoUrl = els.repoUrl.value.trim();
            if (!repoUrl) { showError('请填写仓库地址'); return; }
            stopGitPoll();
            App.state.gitProjectPath = null;
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
                App.state.gitPollTimer = setInterval(() => pollGitStatus(st.jobId), 2000);
            } catch (e) {
                stopGitPoll();
                renderGitStatus({ status: 'FAILED', message: e.message });
            }
        });

        // 原 :794
        els.btnGitSwitch.addEventListener('click', () => {
            if (!App.state.currentProjectId) return;
            startGitSwitch(App.state.currentProjectId);
        });

        // 原 :799
        els.btnGitCheckUpdate.addEventListener('click', async () => {
            if (!App.state.currentProjectId) return;
            await checkGitRemoteStatus(App.state.currentProjectId, false);
        });
    }

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------
    return {
        init: init,
        refreshProjectList: refreshProjectList,
        renderProjectList: renderProjectList,
        projectCardHtml: projectCardHtml,
        enterProject: enterProject,
        autoLoadEntryList: autoLoadEntryList,
        autoLoadCacheForProject: autoLoadCacheForProject,
        updateStep3Hint: updateStep3Hint,
        deleteProject: deleteProject,
        setSourceMode: setSourceMode,
        currentProjectPath: currentProjectPath,
        renderJobLog: renderJobLog,
        renderGitStatus: renderGitStatus,
        stopGitPoll: stopGitPoll,
        pollGitStatus: pollGitStatus,
        formatCheckTime: formatCheckTime,
        showGitInfoBar: showGitInfoBar,
        hideGitInfoBar: hideGitInfoBar,
        renderGitRemoteBadge: renderGitRemoteBadge,
        loadGitRefs: loadGitRefs,
        checkGitRemoteStatus: checkGitRemoteStatus,
        renderGitSwitchProgress: renderGitSwitchProgress,
        setGitSwitchBusy: setGitSwitchBusy,
        stopGitSwitchPoll: stopGitSwitchPoll,
        onGitSwitchDone: onGitSwitchDone,
        pollGitSwitchStatus: pollGitSwitchStatus,
        startGitSwitch: startGitSwitch
    };
});
