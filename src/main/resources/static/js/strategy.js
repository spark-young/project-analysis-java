/*
 * strategy.js —— 扫描策略管理与弹窗（OPT-27 C8 簇）
 * ================================================================================
 * 纯搬运自 app.js 原「扫描策略配置」「手动/自动添加入口」「批量排除」「新手引导」各段，
 * 函数体逐行照搬（模板字符串原样保留），仅机械适配（零行为变化）：
 *   1. 跨簇共享状态 → App.state.*（currentProjectId/currentEntryList/entrySelKeys 为已迁字段）
 *   2. 调用已搬簇函数 → 命名空间访问（Ui.* / Api.* / Projects.* / Entries.* / Batch.* / Noise.*）
 *   3. 调用 app.js 未搬簇函数 → init(hooks) 晚绑定：guideNoiseConfigured（app.js 引导状态）
 * 模块私有状态（有意不进 App.state，依据见 js/state.js 头注释）：
 *   scanStrategy / ssEditingProfileId / ssEditingRuleId / dsContext / globalScanStrategy /
 *   ssPageEditingProfileId / excludeModalItems / addEntryVerified / currentScanCandidates /
 *   currentScanExisted / addEntryModalMode / addEntrySearchTimer
 *   （grep 确认本簇外零引用）。
 */
(function (root, factory) {
    'use strict';
    const Strategy = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Strategy;
    }
    if (root) {
        root.Strategy = Strategy;
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // 基础 UI
    const els = Ui.els;
    const escapeHtml = Ui.escapeHtml;
    const showToast = Ui.showToast;
    const showError = Ui.showError;
    const showConfirm = Ui.showConfirm;
    const switchView = Ui.switchView;
    const showLoading = Ui.showLoading;
    const hideLoading = Ui.hideLoading;
    const clearError = Ui.clearError;

    // 请求层
    const postJson = Api.postJson;
    const fetchJson = Api.fetchJson;
    const putJson = Api.putJson;

    // C3 项目模块
    const currentProjectPath = Projects.currentProjectPath;
    const autoLoadEntryList = Projects.autoLoadEntryList;

    // C4 入口模块
    const entryKey = Entries.entryKey;
    const updateEntryToolbar = Entries.updateEntryToolbar;

    // C6 批量模块
    const readableFullSig = Batch.readableFullSig;
    const sigHtmlFromString = Batch.sigHtmlFromString;

    // C7 噪声模块（仅 guideAction 引用）
    const openNoiseRulesPanel = Noise.openNoiseRulesPanel;

    // ---- 模块私有状态 ----

    // 批量排除弹窗当前承载的条目
    let excludeModalItems = [];

    // 扫描策略状态
    let scanStrategy = null;
    let ssEditingProfileId = null;
    let ssEditingRuleId = null;
    let dsContext = 'modal';
    let globalScanStrategy = null;
    let ssPageEditingProfileId = null;

    // 添加入口弹窗状态
    let addEntryVerified = false;
    let currentScanCandidates = [];
    let currentScanExisted = 0;
    let currentScanScope = '';         // 本次手动扫描的范围描述（类「x」/ 包「x」本层）
    let currentScanTruncated = false;  // 命中数超过后端上限，只返回了前 N 个
    let addEntryModalMode = 'manual';
    let addEntrySearchTimer = null;

    // 扫描策略配置（自动扫描方案管理）
    // ==============================================================

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

    function renderSsPageContent() {
        if (!globalScanStrategy) {
            showToast('全局策略数据尚未加载', 'warn');
            return;
        }
        renderSsProfileList();
        renderSsEditor();
    }

    function openScanStrategyPanel() {
        if (!scanStrategy) { showToast('策略数据尚未加载', 'warn'); return; }
        dsContext = 'modal';
        ssEditingProfileId = scanStrategy.activeProfileId || 'builtin-standard';
        els.scanStrategyOverlay.hidden = false;
        els.scanStrategyPanel.hidden = false;
        renderSsProfileList();
        renderSsEditor();
    }

    function closeScanStrategyPanel() {
        els.scanStrategyPanel.hidden = true;
        els.scanStrategyOverlay.hidden = true;
        ssEditingProfileId = null;
        loadScanStrategy();
    }

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

    function updateSsProfileActions() {
        const profile = currentEditingProfile();
        const isBuiltin = profile && !!profile.builtin;
        __ss().deleteBtn.disabled = !profile || isBuiltin;
    }

    function currentEditingProfile() {
        const ctx = __ss();
        if (!ctx.strategy || !ctx.strategy.profiles || !ctx.editingId) return null;
        return ctx.strategy.profiles.find(p => p.id === ctx.editingId);
    }

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

    function closeSsRuleEditor() {
        els.ssRuleEditor.hidden = true;
        els.ssRuleEditorOverlay.hidden = true;
        ssEditingRuleId = null;
    }

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
            await refreshAnalyzeViewStrategy();
            showToast('✓ 扫描策略已保存', 'success');
        } catch (e) {
            showToast('保存失败: ' + e.message, 'error');
        }
    }

    /**
     * 系统配置页改了全局策略后，分析视图的方案下拉框仍持有"进入项目时"加载的旧快照
     * （只有 dsContext === 'modal' 的老路径才重绘过），表现为"改了策略要重新进入项目才生效"。
     * 这里在页面上下文保存/重置后补一次项目生效策略的重载 + 重绘。
     */
    async function refreshAnalyzeViewStrategy() {
        if (dsContext !== 'page' || !App.state.currentProjectId) return;
        await loadScanStrategy();
    }

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
            await refreshAnalyzeViewStrategy();
            showToast('已恢复默认扫描策略', 'success');
        } catch (e) {
            showToast('重置失败: ' + e.message, 'error');
        }
    }

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
        await refreshAnalyzeViewStrategy();
        showToast('已删除方案', 'success');
    }

    const SS_SHARE_TYPE = 'callgraph-scan-strategy';
    const SS_SHARE_VERSION = 1;

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
            copy.builtin = false;
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

    function addSsRule() {
        const profile = currentEditingProfile();
        if (!profile) { showToast('请先选择一个方案', 'warn'); return; }
        if (profile.builtin) { showToast('内置方案不能编辑', 'warn'); return; }
        openSsRuleEditor(null, null);
    }

    // ---- 新手引导只读状态与导航动作 ----

    let _guideNoiseConfigured = null;

    function guideState() {
        const confirmed = (App.state.currentEntryList && App.state.currentEntryList.confirmed) || [];
        return {
            view: App.state.currentView,
            projectCount: App.state.guideProjectCount,
            currentProjectId: App.state.currentProjectId,
            entryCount: confirmed.length,
            hasResult: App.state.guideHasResult,
            resultStale: App.state.guideResultStale,
            noiseConfigured: _guideNoiseConfigured ? _guideNoiseConfigured() : false,
        };
    }

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

    // ---- 事件绑定 ----

    // 扫描策略管理
    els.btnScanStrategyManage.addEventListener('click', openScanStrategyPanel);
    els.btnSsClose.addEventListener('click', closeScanStrategyPanel);
    els.scanStrategyOverlay.addEventListener('click', closeScanStrategyPanel);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.ssRuleEditor.hidden) closeSsRuleEditor();
        if (e.key === 'Escape' && !els.scanStrategyPanel.hidden && els.ssRuleEditor.hidden) closeScanStrategyPanel();
    });

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

    els.btnSsSave.addEventListener('click', saveSsStrategy);
    els.btnSsPageSave.addEventListener('click', saveSsStrategy);
    els.btnSsReset.addEventListener('click', resetSsStrategy);
    els.btnSsPageReset.addEventListener('click', resetSsStrategy);
    els.btnSsProfileNew.addEventListener('click', newSsProfile);
    els.btnSsPageProfileNew.addEventListener('click', newSsProfile);
    els.btnSsProfileCopy.addEventListener('click', copySsProfile);
    els.btnSsPageProfileCopy.addEventListener('click', copySsProfile);
    els.btnSsProfileDelete.addEventListener('click', deleteSsProfile);
    els.btnSsPageProfileDelete.addEventListener('click', deleteSsProfile);

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

    els.btnSsRuleAdd.addEventListener('click', addSsRule);
    els.btnSsPageRuleAdd.addEventListener('click', addSsRule);

    els.ssRuleKind.addEventListener('change', () => {
        renderSsRuleDynamicFields(els.ssRuleKind.value, null);
    });

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

    els.ssRuleEditorCancel.addEventListener('click', closeSsRuleEditor);
    els.ssRuleEditorClose.addEventListener('click', closeSsRuleEditor);
    els.ssRuleEditorOverlay.addEventListener('click', closeSsRuleEditor);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.ssRuleEditor.hidden) closeSsRuleEditor();
    });

    // 系统配置 tab 切换
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

    // 自动扫描按钮 → diff → 弹窗展示候选
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
                renderScanResults(candidates, scanExisted, true);   // 自动扫描的候选已按策略筛过，保持默认全选
                setVerifyStatus('扫描完成，请选择要加入的方法', 'ok');
            }
        } catch (e) {
            hideLoading();
            showError('扫描失败: ' + e.message);
        }
    });

    // 添加入口弹窗
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
            els.addEntryMethod.innerHTML = '<option value="">留空（列出该类下所有方法）</option>';
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
        els.addEntryVerifyStatus.textContent = text;
        els.addEntryVerifyStatus.className = 'modal-verify-status' + (type ? ' vs-' + type : '');
    }

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

    function renderScanResults(candidates, existed, defaultChecked) {
        els.addEntryScanWrap.hidden = false;
        const listEl = els.addEntryScanList;
        listEl.innerHTML = '';
        scanShiftAnchorCb = null;   // 列表重建，旧的连选锚点已失效
        currentScanExisted = existed || 0;
        if (candidates.length === 0) {
            listEl.innerHTML = '<div class="scan-result-empty">没有可加入的方法'
                + (currentScanExisted > 0 ? '（' + currentScanExisted + ' 个已在清单中）' : '')
                + '。可改填类名 / 包名，或用方法名缩小范围</div>';
        } else {
            candidates.forEach((item) => {
                const sig = readableFullSig(item.className, item.methodName, item.descriptor) || entryKey(item);
                const key = entryKey(item);
                const row = document.createElement('div');
                row.className = 'scan-result-item';
                row.innerHTML =
                    '<input type="checkbox" class="scan-item-cb" data-key="' + key.replace(/"/g, '&quot;') + '"'
                    + (defaultChecked ? ' checked' : '') + '>'
                    + '<span class="scan-item-sig">' + sigHtmlFromString(sig) + '</span>'
                    + (item.group ? '<span class="scan-item-group">' + escapeHtml(item.group) + '</span>' : '');
                listEl.appendChild(row);
            });
        }
        updateScanStats();
    }

    function updateScanStats() {
        const all = els.addEntryScanList.querySelectorAll('.scan-item-cb');
        const checked = els.addEntryScanList.querySelectorAll('.scan-item-cb:checked').length;
        els.addEntryScanAll.checked = all.length > 0 && all.length === checked;
        els.addEntryConfirm.textContent = '确定加入（' + checked + '）';
        els.addEntryConfirm.disabled = checked === 0;
        let statsText = (currentScanScope ? currentScanScope + '：' : '')
            + '扫描到 ' + all.length + ' 个可加入的方法，勾选 ' + checked + ' 个';
        if (currentScanExisted > 0) statsText += '；' + currentScanExisted + ' 个已在清单中（不会重复加入）';
        if (currentScanTruncated) statsText += '；命中过多，仅显示前 ' + all.length + ' 个';
        els.addEntryScanStats.textContent = statsText;
    }

    /** 本次扫描范围的展示文案；包名明确标注"仅本层"，避免用户误以为包含子包 */
    function scanScopeLabel(resp) {
        if (resp.mode === 'PACKAGE') return '包「' + (resp.resolvedName || '') + '」仅本层（不含子包）';
        if (resp.mode === 'CLASS') return '类「' + (resp.resolvedName || '') + '」';
        return '';
    }

    /** 扫描完成后的状态行文案：区分"找不到"与"范围下没有方法" */
    function scanStatusText(resp, hitCount) {
        if (resp.mode === 'NONE') return '未找到类或包：' + (resp.resolvedName || '');
        if (hitCount === 0) {
            return resp.mode === 'PACKAGE'
                ? '该包本层没有类（方法都在子包里，本工具不递归子包）'
                : '该类下没有可加入的方法（已排除构造器与合成方法）';
        }
        return '扫描完成，请选择要加入的方法';
    }

    function readAddEntryInput() {
        let className = '', methodName = '';
        const pasteRaw = els.addEntryPaste.value.trim();
        if (pasteRaw) {
            const p = Sig.parseEntryString(pasteRaw);
            className = p.className;
            methodName = p.methodName || '';
        } else {
            className = els.addEntryClass.value.trim();
            methodName = els.addEntryMethodText.value.trim() || els.addEntryMethod.value.trim();
        }
        return { className, methodName };
    }

    // 添加入口弹窗绑定
    els.btnEntryAdd.addEventListener('click', openAddEntryModal);
    els.addEntryClose.addEventListener('click', closeAddEntryModal);
    els.addEntryCancel.addEventListener('click', closeAddEntryModal);
    els.addEntryOverlay.addEventListener('click', closeAddEntryModal);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.addEntryModal.hidden) closeAddEntryModal();
    });

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
            const methods = resp.ok ? await resp.json() : [];
            // 下拉框只对"类"有意义；填的是包名（或类不存在）时重置，避免残留上一个类的选项
            const base = '<option value="">留空（列出该类下所有方法）</option>';
            if (!Array.isArray(methods) || methods.length === 0) {
                els.addEntryMethod.innerHTML = base;
                return;
            }
            els.addEntryMethod.innerHTML = base
                + methods.map(m => `<option value="${m.name}" data-desc="${m.descriptor || ''}">${m.name}()</option>`).join('');
        } catch (e) { /* 忽略 */ }
    }

    els.addEntryPaste.addEventListener('input', () => {
        const raw = els.addEntryPaste.value.trim();
        if (!raw) return;
        const parsed = Sig.parseEntryString(raw);
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
        currentScanScope = '';
        currentScanTruncated = false;
        try {
            const resp = await postJson('/api/projects/' + App.state.currentProjectId + '/entries/scan-manual', {
                className: input.className,
                methodName: input.methodName || ''
            });
            const candidates = resp.candidates || [];
            const existed = resp.existed || 0;
            currentScanCandidates = candidates;
            currentScanScope = scanScopeLabel(resp);
            currentScanTruncated = !!resp.truncated;
            // 手动扫描枚举的是"该范围下全部方法"，动辄上百个，默认不勾选由用户挑选；
            // 自动扫描的候选已按策略筛过，沿用原来的默认全选。
            renderScanResults(candidates, existed, false);
            setVerifyStatus(scanStatusText(resp, candidates.length),
                resp.mode === 'NONE' ? 'err' : (candidates.length > 0 ? 'ok' : 'warn'));
            addEntryVerified = true;
        } catch (e) {
            els.addEntryScanWrap.hidden = true;
            setVerifyStatus('扫描失败: ' + e.message, 'err');
            addEntryVerified = false;
        } finally {
            els.addEntryVerify.disabled = false;
        }
    });

    els.addEntryScanAll.addEventListener('change', () => {
        const checked = els.addEntryScanAll.checked;
        els.addEntryScanList.querySelectorAll('.scan-item-cb').forEach(cb => cb.checked = checked);
        updateScanStats();
    });

    els.addEntryScanList.addEventListener('change', (e) => {
        if (!e.target.classList.contains('scan-item-cb')) return;
        // Shift 连选：整段设为与本行相同的勾选态
        applyScanRangeSelect(scanShiftAnchorCb, e.target);
        scanShiftAnchorCb = e.target;
        updateScanStats();
    });

    /** 扫描候选弹窗的 Shift 连选锚点（列表每次扫描都会重建，锚点失效时自动退化为普通单选） */
    let scanShiftAnchorCb = null;

    function applyScanRangeSelect(anchorCb, targetCb) {
        if (!anchorCb || !targetCb) return false;
        const cbs = Array.from(els.addEntryScanList.querySelectorAll('.scan-item-cb'));
        const a = cbs.indexOf(anchorCb);
        const b = cbs.indexOf(targetCb);
        if (a < 0 || b < 0) return false;
        for (let i = Math.min(a, b); i <= Math.max(a, b); i++) cbs[i].checked = targetCb.checked;
        return true;
    }

    els.addEntryClass.addEventListener('input', resetVerify);
    els.addEntryMethodText.addEventListener('input', resetVerify);
    els.addEntryMethod.addEventListener('change', resetVerify);
    els.addEntryPaste.addEventListener('input', resetVerify);

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

    // 入口行内排除/恢复与勾选事件委托
    const entryActions = {
        exclude(key) {
            if (!App.state.currentProjectId) return;
            const items = ((App.state.currentEntryList && App.state.currentEntryList.confirmed) || [])
                .filter(it => entryKey(it) === key);
            if (items.length === 0) return;
            openExcludeModal(items);
        },
        restore(key) {
            if (!App.state.currentProjectId) return;
            postJson('/api/projects/' + App.state.currentProjectId + '/entries/restore', { key: key })
                .then(() => autoLoadEntryList(App.state.currentProjectId))
                .catch(e => showError('恢复失败: ' + e.message));
        },
    };

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

    // ---- 批量排除 ----

    const EXCLUDE_REASON_PRESETS = ['非业务入口', '已废弃 / 不再使用', '测试 / 演示代码', '重复入口'];

    els.entryConfirmedList.addEventListener('change', (e) => {
        if (!e.target.classList.contains('entry-cb')) return;
        const row = e.target.closest('.entry-row');
        if (!row) return;
        // Shift 连选：把「上次点击的行 → 本行」整段设为同一勾选态（按当前可见顺序，被过滤隐藏的行不参与）
        if (e.shiftKey && applyEntryRangeSelect(entryShiftAnchorRow, row)) {
            syncEntrySelFromRows();
        } else {
            const key = row.dataset.key;
            if (e.target.checked) App.state.entrySelKeys.add(key); else App.state.entrySelKeys.delete(key);
            row.classList.toggle('selected', e.target.checked);
        }
        entryShiftAnchorRow = row;
        updateEntryToolbar();
    });

    /** 清单行 Shift 连选锚点（元素引用而非下标：过滤/重排后下标会漂移） */
    let entryShiftAnchorRow = null;

    /** 当前可见的清单行（清单过滤把行 display 设成 none） */
    function visibleEntryRows() {
        return Array.from(els.entryConfirmedList.querySelectorAll('.entry-row'))
            .filter((row) => row.style.display !== 'none');
    }

    /** 把 anchor 与 target 之间的可见行整段设为 target 的勾选态；anchor 已不可见时返回 false */
    function applyEntryRangeSelect(anchorRow, targetRow) {
        if (!anchorRow || !targetRow) return false;
        const rows = visibleEntryRows();
        const a = rows.indexOf(anchorRow);
        const b = rows.indexOf(targetRow);
        if (a < 0 || b < 0) return false;
        const checked = targetRow.querySelector('.entry-cb').checked;
        for (let i = Math.min(a, b); i <= Math.max(a, b); i++) {
            const cb = rows[i].querySelector('.entry-cb');
            if (cb) cb.checked = checked;
        }
        return true;
    }

    /** 以 DOM 勾选态为准重建 entrySelKeys（连选会批量改动多次勾选，逐行加减容易漏） */
    function syncEntrySelFromRows() {
        App.state.entrySelKeys.clear();
        els.entryConfirmedList.querySelectorAll('.entry-row').forEach((row) => {
            const cb = row.querySelector('.entry-cb');
            if (!cb) return;
            row.classList.toggle('selected', cb.checked);
            if (cb.checked && row.dataset.key) App.state.entrySelKeys.add(row.dataset.key);
        });
    }

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

    els.excludeReasonPresets.addEventListener('click', (e) => {
        const btn = e.target.closest('.ex-preset');
        if (!btn) return;
        els.excludeReasonPresets.querySelectorAll('.ex-preset')
            .forEach(b => b.classList.toggle('active', b === btn));
        els.excludeReasonText.value = btn.dataset.reason;
    });

    els.excludeReasonText.addEventListener('input', () => {
        els.excludeReasonPresets.querySelectorAll('.ex-preset')
            .forEach(b => b.classList.remove('active'));
    });

    els.excludeReasonUnified.addEventListener('change', () => {
        els.excludePerItemReasons.hidden = els.excludeReasonUnified.checked;
    });

    els.excludeModalClose.addEventListener('click', closeExcludeModal);
    els.excludeModalCancel.addEventListener('click', closeExcludeModal);
    els.excludeModalOverlay.addEventListener('click', closeExcludeModal);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !els.excludeModal.hidden) closeExcludeModal();
    });

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

    // ---- hooks 注入 ----

    function init(opts) {
        if (opts && opts.guideNoiseConfigured) _guideNoiseConfigured = opts.guideNoiseConfigured;
    }

    // ================================================================
    // 公共 API
    // ================================================================

    return {
        init: init,
        loadScanStrategy: loadScanStrategy,
        loadGlobalScanStrategy: loadGlobalScanStrategy,
        openScanStrategyPanel: openScanStrategyPanel,
        closeScanStrategyPanel: closeScanStrategyPanel,
        renderSsPageContent: renderSsPageContent,
        saveSsStrategy: saveSsStrategy,
        resetSsStrategy: resetSsStrategy,
        newSsProfile: newSsProfile,
        copySsProfile: copySsProfile,
        deleteSsProfile: deleteSsProfile,
        exportSsProfiles: exportSsProfiles,
        importSsProfiles: importSsProfiles,
        addSsRule: addSsRule,
        openAddEntryModal: openAddEntryModal,
        closeAddEntryModal: closeAddEntryModal,
        loadMethodsForClass: loadMethodsForClass,
        entryActions: entryActions,
        openExcludeModal: openExcludeModal,
        closeExcludeModal: closeExcludeModal,
        guideState: guideState,
        guideAction: guideAction,
    };
});