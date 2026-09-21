#!/usr/bin/env node
/*
 * OPT-24 前端契约测试（Node 运行，本仓无 JS 测试框架）
 * ====================================================
 * 加载共享夹具 + 前端 Sig 模块（src/main/resources/static/js/sig.js），
 * 断言前端产出与夹具期望（= 后端权威格式）一致。
 *
 * 运行：  node src/test/js/contract.test.js
 * 退出码：0 = 全通过；1 = 有失败（并打印逐条差异）
 */
'use strict';

const fs = require('fs');
const path = require('path');

const Sig = require('../../main/resources/static/js/sig.js');

const SIGNATURES = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'resources', 'contract', 'signatures.json'), 'utf8'));
const NOISE = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', 'resources', 'contract', 'noise.json'), 'utf8'));

let passed = 0;
const failures = [];

function eq(label, actual, expected) {
    const a = JSON.stringify(actual);
    const e = JSON.stringify(expected);
    if (a === e) {
        passed++;
    } else {
        failures.push(label + '\n     expected: ' + e + '\n     actual:   ' + a);
    }
}

// --------------------------- 签名契约 ---------------------------
SIGNATURES.cases.forEach((c) => {
    const tag = '[sig] ' + c.name + ' (' + c.identifier + ')';
    // 1) 描述符解析 → 参数短名列表
    eq(tag + ' · paramNamesFromDescriptor', Sig.paramNamesFromDescriptor(c.descriptor), c.paramTypes);
    // 2) 由(类名,方法名,描述符)生成权威签名
    eq(tag + ' · fullSignature(descriptor)', Sig.fullSignature(c.className, c.methodName, c.descriptor), c.identifier);
    // 3) 描述符参数个数 == 参数列表长度
    eq(tag + ' · paramCountFromDescriptor', Sig.paramCountFromDescriptor(c.descriptor), c.paramTypes.length);
});

// --------------------------- 噪声判定契约 ---------------------------
NOISE.cases.forEach((c) => {
    const tag = '[noise] ' + c.name;
    // 前端真实路径：先解析签名 → (className, methodName, paramCount)，再判定
    const split = Sig.splitSignature(c.identifier);
    eq(tag + ' · splitSignature.className', split.className, c.className);
    eq(tag + ' · splitSignature.methodName', split.methodName, c.methodName);
    eq(tag + ' · splitSignature.paramCount', split.paramCount, c.paramCount);
    const compiled = Sig.compileRules(c.rules);
    eq(tag + ' · matchCompiledRules',
        Sig.matchCompiledRules(compiled, c.source, c.className, c.methodName, c.paramCount),
        c.expected);
});

// --------------------------- 汇总 ---------------------------
const total = passed + failures.length;
if (failures.length === 0) {
    console.log('OK  contract.test.js: ' + passed + '/' + total + ' 断言通过');
    process.exit(0);
}
console.error('FAIL contract.test.js: ' + failures.length + '/' + total + ' 断言失败\n');
failures.forEach((f) => console.error('  ✗ ' + f + '\n'));
process.exit(1);
