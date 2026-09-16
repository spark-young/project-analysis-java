/* 新手引导（三层轻引导）
 *
 *  1) 常驻「下一步」条 —— 永远只显示当前该做的那一件事，走通全流程后自动退场
 *  2) 首次上手清单   —— 四项自动打勾，可整体收起，全部完成自动隐藏
 *  3) 一次性就地微提示 —— 每个 key 只出现一次，带 ✕
 *
 * 设计原则：状态驱动、完成即退场、绝不阻塞操作、可永久关闭，随时可从 header 的 ? 重看。
 * 本文件只读取 app.js 传入的状态，不反向修改任何业务逻辑。
 */
(function () {
    'use strict';

    const LS_PREFIX = 'cg.guide.';
    const KEY_BAR_CLOSED = 'barClosed';
    const KEY_CHECKLIST_CLOSED = 'checklistClosed';
    const KEY_NOISE_CONFIGURED = 'noiseConfigured';

    const store = {
        get(k) { try { return localStorage.getItem(LS_PREFIX + k); } catch (e) { return null; } },
        set(k, v) { try { localStorage.setItem(LS_PREFIX + k, v); } catch (e) { /* 隐私模式忽略 */ } },
        del(k) { try { localStorage.removeItem(LS_PREFIX + k); } catch (e) { /* ignore */ } },
    };

    let cfg = null;      // { getState, onAction, onReplay }
    let el = {};         // 元素引用
    let ready = false;

    // ------------------------------------------------------------------
    // 状态 → 「下一步」
    // ------------------------------------------------------------------

    /** 全流程走通后返回 null —— 引导条自动隐藏，不再打扰 */
    function nextStep(s) {
        if (s.projectCount == null) return null;   // 项目列表还没拉到，先不打扰
        if (!s.projectCount) {
            return { ico: '📁', text: '还没有项目 —— 先导入一个（本地目录，或 Git 仓库）', action: 'import', label: '去导入' };
        }
        if (!s.currentProjectId) {
            return { ico: '👉', text: '从下面的项目列表点一个进入，就可以开始分析了', action: 'openProject', label: '选项目' };
        }
        if (!s.entryCount) {
            return { ico: '📝', text: '交易入口清单还是空的 —— 分析是按这份清单跑的，先补入口', action: 'fixEntries', label: '补入口' };
        }
        if (!s.hasResult) {
            return {
                ico: '▶️',
                text: '清单已就绪（' + s.entryCount + ' 个入口），可以开始分析了'
                    + (s.noiseConfigured ? '' : '；建议先看一眼「⚙ 过滤规则配置」，把样板方法噪声去掉'),
                action: 'analyze',
                label: '去分析',
            };
        }
        if (s.resultStale) {
            return { ico: '⚠️', text: '入口清单调整过，当前分析结果可能已过期', action: 'reanalyze', label: '重新分析' };
        }
        return null;
    }

    function renderBar(s) {
        if (!el.bar) return;
        const step = nextStep(s);
        if (!step) { el.bar.hidden = true; return; }   // 全流程走通 → 退场
        // ✕ 只静默「当前这一条」：状态推进到下一步（action 变了）时仍会提示，不会漏掉关键引导
        if (store.get(KEY_BAR_CLOSED) === step.action) { el.bar.hidden = true; return; }
        el.ico.textContent = step.ico;
        el.text.textContent = step.text;
        el.action.textContent = step.label;
        el.action.dataset.action = step.action;
        el.bar.hidden = false;
    }

    // ------------------------------------------------------------------
    // 上手清单
    // ------------------------------------------------------------------

    function checklistItems(s) {
        return [
            { done: s.projectCount > 0, text: '导入一个项目（本地目录或 Git 仓库）', action: 'import' },
            { done: !!s.currentProjectId && s.entryCount > 0, text: '进入项目，确认交易入口清单', action: 'fixEntries' },
            { done: s.noiseConfigured, opt: true, text: '配置过滤规则，去掉样板方法的噪声', action: 'noiseRules' },
            { done: s.hasResult, text: '开始分析，查看调用链 / 导出 Excel', action: 'analyze' },
        ];
    }

    function renderChecklist(s) {
        const box = el.checklist;
        if (!box) return;
        if (s.projectCount == null) {   // 项目列表还没拉到，先不渲染
            box.hidden = true;
            return;
        }
        const items = checklistItems(s);
        // 必做项全部完成即收起：可选项不再长期挂着打扰
        const allRequiredDone = items.filter((i) => !i.opt).every((i) => i.done);
        if (allRequiredDone || store.get(KEY_CHECKLIST_CLOSED) === '1') {
            box.hidden = true;
            box.innerHTML = '';
            return;
        }
        // 「当前该做」跳过可选项：可选步骤不该把后面的必做步骤卡住
        const currentIdx = items.findIndex((it) => !it.done && !it.opt);
        const rows = items.map((it, i) => {
            const current = i === currentIdx;
            const cls = it.done ? 'done' : (current ? 'current' : 'todo');
            const dot = it.done ? '✓' : (current ? '▶' : '○');
            return '<li class="gc-item ' + cls + '">'
                + '<span class="gc-dot">' + dot + '</span>'
                + '<span class="gc-text">' + it.text + (it.opt ? '（可选）' : '') + '</span>'
                + '</li>';
        }).join('');
        box.innerHTML =
            '<div class="gc-head">'
            + '<span class="gc-title">🚀 上手四步</span>'
            + '<span class="gc-sub">按顺序走完即可拿到分析结果</span>'
            + '<button type="button" class="gc-close" title="收起引导（右上角 ? 可重新打开）">✕</button>'
            + '</div>'
            + '<ul class="gc-list">' + rows + '</ul>';
        box.hidden = false;
        box.querySelector('.gc-close').addEventListener('click', () => {
            store.set(KEY_CHECKLIST_CLOSED, '1');
            renderChecklist(s);
        });
    }

    // ------------------------------------------------------------------
    // 对外能力
    // ------------------------------------------------------------------

    function refresh() {
        if (!ready || !cfg || typeof cfg.getState !== 'function') return;
        let s;
        try {
            s = cfg.getState() || {};
        } catch (e) {
            return;
        }
        renderBar(s);
        // 清单只在「项目列表」页展示，避免在分析页叠加干扰
        if (s.view === 'projects') renderChecklist(s);
        else if (el.checklist) { el.checklist.hidden = true; }
    }

    /** 一次性就地微提示：同一 key 全局只出现一次 */
    function tipOnce(key, anchor, text) {
        if (!ready || !anchor || !anchor.parentNode) return;
        if (store.get('tip.' + key) === '1') return;
        if (document.querySelector('.guide-tip[data-key="' + key + '"]')) return;
        store.set('tip.' + key, '1');
        const tip = document.createElement('div');
        tip.className = 'guide-tip';
        tip.dataset.key = key;
        const span = document.createElement('span');
        span.className = 'guide-tip-text';
        span.textContent = text;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'guide-tip-close';
        btn.title = '知道了';
        btn.textContent = '✕';
        btn.addEventListener('click', () => tip.remove());
        tip.appendChild(span);
        tip.appendChild(btn);
        anchor.parentNode.insertBefore(tip, anchor.nextSibling);
    }

    /** 重放引导：清掉所有「已看过/已关闭」标记 */
    function replay() {
        [KEY_BAR_CLOSED, KEY_CHECKLIST_CLOSED].forEach(store.del);
        try {
            const keys = [];
            for (let i = 0; i < localStorage.length; i++) {
                const k = localStorage.key(i);
                if (k && (k.indexOf(LS_PREFIX + 'tip.') === 0)) keys.push(k);
            }
            keys.forEach((k) => localStorage.removeItem(k));
        } catch (e) { /* ignore */ }
        if (cfg && typeof cfg.onReplay === 'function') cfg.onReplay();
        refresh();
    }

    function bindEvents() {
        if (el.action) {
            el.action.addEventListener('click', () => {
                const action = el.action.dataset.action;
                if (action && cfg && typeof cfg.onAction === 'function') cfg.onAction(action);
                refresh();
            });
        }
        if (el.close) {
            el.close.addEventListener('click', () => {
                store.set(KEY_BAR_CLOSED, el.action.dataset.action || '1');
                if (el.bar) el.bar.hidden = true;
            });
        }
        const help = document.getElementById('btnGuideHelp');
        if (help) help.addEventListener('click', replay);
    }

    function init(config) {
        cfg = config || {};
        el.bar = document.getElementById('guideBar');
        el.ico = document.getElementById('guideIco');
        el.text = document.getElementById('guideText');
        el.action = document.getElementById('guideAction');
        el.close = document.getElementById('guideClose');
        el.checklist = document.getElementById('guideChecklist');
        bindEvents();
        ready = true;
        refresh();
    }

    window.Guide = {
        init: init,
        refresh: refresh,
        tipOnce: tipOnce,
        replay: replay,
        isSeen: (k) => store.get(k) === '1',
        markSeen: (k) => store.set(k, '1'),
        NOISE_CONFIGURED: KEY_NOISE_CONFIGURED,
    };
})();
