/*
 * api.js —— 后端 REST 请求层（前端唯一来源）
 * ==================================================
 *
 * OPT-27（app.js 拆分）从 app.js:795-828 **逐行搬运**而来，函数体零修改：
 *   postJson(url, body)  → POST JSON，非 2xx 抛 Error(data.error || HTTP status)
 *   fetchJson(url)       → GET  JSON，同上
 *   putJson(url, body)   → PUT  JSON，同上
 *
 * UMD 双导出（与 sig.js 同约定）：浏览器挂 window.Api（index.html 在 app.js 之前加载），
 * Node 走 module.exports（供后续可能的契约/单测复用）。
 */
(function (root, factory) {
    'use strict';
    var Api = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Api;                 // Node
    }
    if (root) {
        root.Api = Api;                       // 浏览器（app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

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

    async function putJson(url, body) {
        const resp = await fetch(url, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok) {
            throw new Error(data.error || ('请求失败: HTTP ' + resp.status));
        }
        return data;
    }

    return {
        postJson: postJson,
        fetchJson: fetchJson,
        putJson: putJson
    };
});
