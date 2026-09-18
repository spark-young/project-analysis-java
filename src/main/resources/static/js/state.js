/*
 * state.js —— app.js 拆分的共享状态通道（OPT-27 C2 首批字段）
 * ==================================================
 *
 * 背景：app.js 是单 IIFE，约 50 个顶层 let 为各簇闭包共享。逐簇外搬时，
 * 被多簇读写的字段必须先迁入本模块，避免闭包状态被截断成两份。
 *
 * 原则（QA 补充）：**只搬当前簇实际需要的字段**，后续簇外搬时按此模式追加；
 * 每个字段必须写明「写入方 / 读取方 / 清零（重置）职责归属」，防止"半重置"
 * （一簇重置了另一簇不知道）。
 *
 * 当前字段（均为 C2 迁入）：
 *
 *   currentView   初始 'projects'
 *     写入：ui.js switchView（唯一写入方，视图切换即整体覆盖）
 *     读取：app.js guideState（新手引导只读状态）
 *     清零职责：无独立重置场景——switchView 每次调用都整体覆盖（原 app.js:341）。
 *
 *   noiseRuleMode 初始 'panel'   // 'panel'（弹窗）| 'page'（独立页面）
 *     写入：ui.js switchView（离开 noiseRules 视图时重置 'panel'，原 app.js:334）；
 *           app.js 噪声规则簇（navToNoiseRules='page'、btnBackToProjects/btnNrPageBack='panel'、
 *           openNoiseRulesPanel='panel'，原行号 611/623/637/2910）
 *     读取：app.js 噪声规则簇（renderNoiseRulesList / renderGlobalOverridesSection /
 *           renderCustomRulesSection / applyNoiseRulesFromPanel / resetNoiseRules /
 *           saveNoiseRules 等，按 mode 选弹窗或页面容器）
 *     清零职责：**重置 'panel' 的唯一权威是 switchView**；页面→弹窗的回退由
 *           btnBackToProjects / btnNrPageBack 显式写 'panel'，与原行为逐字一致。
 *
 * 后续簇预期（占位说明，迁入时再真正添加字段）：
 *   C3 projects+Git：sourceMode / gitProjectPath / gitPollTimer / gitSwitchTimer / gitRefsCache
 *   C4 entries：     entryItems / entrySelKeys / excludeModalItems / currentEntryList / currentCandidates
 *   C5 result：      currentResult / nodeRegistry / hitRows / activeSearch / expandFns
 *   C6 batch+freq：  batchModel / batchRowStates / freqFilter / freqViewMode / projSearch*
 *   C7 noise：       noiseRules / globalRulesCache / projectRulesCache / globalOverrides /
 *                    activeNoiseRules / compiledActiveRules / activeNoiseHash / noiseRuleScope
 *   C8 strategy：    scanStrategy / globalScanStrategy / ss* / dsContext
 */
(function (root, factory) {
    'use strict';
    var App = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = App;                 // Node
    }
    if (root) {
        root.App = App;                       // 浏览器（ui.js / app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    return {
        state: {
            currentView: 'projects',          // 原 app.js:277 逐字迁入（含初始值）
            noiseRuleMode: 'panel'            // 原 app.js:314 逐字迁入（含初始值）
        }
    };
});
