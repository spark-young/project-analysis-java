/*
 * sig.js —— 方法签名解析 / 规范化的单一实现（前端）
 * ==================================================
 *
 * 权威格式（与后端 MethodKey.getIdentifier() 一致）：
 *     全限定类名#方法名(参数类型短名列表)
 *     例：com.foo.Bar#write(Object, int)
 *     构造器：com.foo.Bar#<init>(String)
 *     无参方法参数列表仍保留空括号：com.foo.Bar#ping()
 *
 * 本模块把原先散落在 app.js 各处的 8 份签名解析/格式化实现收敛为**唯一来源**，
 * 同时被浏览器（window.Sig）与 Node（module.exports）加载，供前后端契约测试共用。
 *
 * OPT-24 演进：COMMIT 1 本文件镜像 app.js 现有行为（不改界面输出）；COMMIT 2 app.js 各处
 * 改为调用本模块、删除重复实现；D3 起噪声判定严格对齐后端权威
 * NoiseRuleService.isNoiseOnRules（source 大小写不敏感；仅 null/空串 pattern 才视为"不限"）。
 */
(function (root, factory) {
    'use strict';
    var Sig = factory();
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Sig;                 // Node（契约测试）
    }
    if (root) {
        root.Sig = Sig;                       // 浏览器（app.js 之前加载）
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
    'use strict';

    // ------------------------------------------------------------------
    // JVM 描述符 ↔ 简短类型名
    // ------------------------------------------------------------------

    /** 原始类型/JVM 单字符 → 简短类型名（含 void） */
    var SIG_PRIM = {
        Z: 'boolean', B: 'byte', C: 'char', S: 'short',
        I: 'int', J: 'long', F: 'float', D: 'double', V: 'void'
    };

    /** 简短类型名 → JVM 单字符（反向映射，用于"可读参数 → 描述符"） */
    var PRIM_TO_DESC = {
        boolean: 'Z', byte: 'B', char: 'C', short: 'S',
        int: 'I', long: 'J', float: 'F', double: 'D', void: 'V'
    };

    /** 取内部名（com/foo/Bar$Inner）的最后一段简短类名（Bar 的嵌套类 Inner） */
    function shortInternalName(internal) {
        var slash = internal.lastIndexOf('/');
        var dollar = internal.lastIndexOf('$');
        var cut = Math.max(slash, dollar);
        return cut < 0 ? internal : internal.substring(cut + 1);
    }

    /**
     * JVM 描述符参数 → 简短类型名列表。
     * 例：'(Ljava/lang/String;I)' → ['String', 'int']；'([[I)' → ['int[][]']
     */
    function paramNamesFromDescriptor(descriptor) {
        var out = [];
        if (!descriptor) return out;
        var i = descriptor.indexOf('(');
        var j = descriptor.lastIndexOf(')');
        if (i < 0 || j <= i) return out;
        var body = descriptor.substring(i + 1, j);
        var k = 0, n = body.length;
        while (k < n) {
            var arr = '';
            while (body[k] === '[') { arr += '[]'; k++; }
            var c = body[k];
            if (c === 'L') {
                var semi = body.indexOf(';', k);
                var internal = body.substring(k + 1, semi < 0 ? n : semi);
                out.push(shortInternalName(internal) + arr);
                k = semi < 0 ? n : semi + 1;
            } else {
                out.push((SIG_PRIM[c] || c) + arr);
                k++;
            }
        }
        return out;
    }

    /** 从 JVM 描述符里取参数个数（不依赖方法名后缀） */
    function paramCountFromDescriptor(descriptor) {
        if (!descriptor) return 0;
        var i = descriptor.indexOf('(');
        var j = descriptor.lastIndexOf(')');
        if (i < 0 || j <= i) return 0;
        var body = descriptor.substring(i + 1, j);
        var count = 0, k = 0, n = body.length;
        while (k < n) {
            while (body[k] === '[') k++;
            var c = body[k];
            if (c === 'L') {
                var semi = body.indexOf(';', k);
                k = semi < 0 ? n : semi + 1;
            } else if (c !== 'V') {
                k++;
            } else {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * 从"方法名(参数...)"字符串解析参数个数。
     * 正确处理泛型中的逗号，如 'query(Map<String, Integer>)' → 1。
     */
    function paramCountFromSignature(methodWithArgs) {
        if (!methodWithArgs) return 0;
        var paren = methodWithArgs.indexOf('(');
        if (paren < 0) return 0;
        var closeParen = methodWithArgs.indexOf(')', paren);
        if (closeParen < 0) closeParen = methodWithArgs.length;
        var params = methodWithArgs.slice(paren + 1, closeParen).trim();
        if (!params) return 0;
        var depth = 0;
        var count = 1;
        for (var i = 0; i < params.length; i++) {
            var c = params.charAt(i);
            if (c === '<' || c === '(') depth++;
            else if (c === '>' || c === ')') depth--;
            else if (c === ',' && depth === 0) count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 规范化：生成权威签名字符串
    // ------------------------------------------------------------------

    /**
     * 统一可读签名：全限定类名#方法名(参数...)。
     * 无方法名（整类入口）只显示类名；有方法名但无描述符时不再补括号（保持既有展示行为）。
     */
    function fullSignature(className, methodName, descriptor) {
        var cls = className || '';
        if (!methodName) return cls;
        if (descriptor) {
            var params = paramNamesFromDescriptor(descriptor);
            return params.length > 0
                ? cls + '#' + methodName + '(' + params.join(', ') + ')'
                : cls + '#' + methodName + '()';
        }
        return cls + '#' + methodName;
    }

    /**
     * 拆解权威签名 → { className, simpleClass, methodName, paramCount }。
     * 无 '#' 时 className 视为空（与后端 parseMethodSignature 口径一致，均为空串而非 null）。
     */
    function splitSignature(method) {
        var s = method || '';
        var hashIdx = s.indexOf('#');
        var className = hashIdx >= 0 ? s.slice(0, hashIdx) : '';
        var rest = hashIdx >= 0 ? s.slice(hashIdx + 1) : s;
        var parenIdx = rest.indexOf('(');
        var methodName = parenIdx >= 0 ? rest.slice(0, parenIdx) : rest;
        var dot = className.lastIndexOf('.');
        return {
            className: className,
            simpleClass: dot >= 0 ? className.slice(dot + 1) : className,
            methodName: methodName,
            paramCount: paramCountFromSignature(rest)
        };
    }

    // ------------------------------------------------------------------
    // 反向：可读参数 → JVM 描述符（精确重载检测用）
    // ------------------------------------------------------------------

    /** 判断一段参数是否已经是 JVM descriptor（形如 '' / J / I / [Ljava/lang/String;） */
    function looksLikeDescriptor(body) {
        var i = 0, n = body.length;
        while (i < n) {
            while (i < n && body[i] === '[') i++;
            if (i >= n) return false;
            var c = body[i];
            if (c === 'L') {
                var s = body.indexOf(';', i);
                if (s < 0) return false;
                i = s + 1;
            } else if ('ZBCSIFDV'.indexOf(c) >= 0) {
                i++;
            } else {
                return false;
            }
        }
        return true;   // 空 body 也是合法 descriptor（无参方法）
    }

    /** 可读参数类型名 → 单个 JVM 类型描述符（尽力转换，供"精确重载"检测，检测端会自动校正） */
    function typeToDescriptor(name) {
        var arr = '', t = name;
        while (t.endsWith('[]')) { arr = '[' + arr; t = t.slice(0, -2); }
        if (PRIM_TO_DESC[t]) return arr + PRIM_TO_DESC[t];
        if (t === 'Object') return arr + 'Ljava/lang/Object;';
        if (t === 'String') return arr + 'Ljava/lang/String;';
        if (t === 'Class') return arr + 'Ljava/lang/Class;';
        return arr + 'L' + t.replace(/\./g, '/').replace(/\./g, '/') + ';';
    }

    /** 可读参数列表 → JVM 描述符，如 'String, int' → '(Ljava/lang/String;I)' */
    function paramsToDescriptor(body) {
        var parts = body.split(',').map(function (s) { return s.trim(); });
        return '(' + parts.filter(Boolean).map(typeToDescriptor).join('') + ')';
    }

    // ------------------------------------------------------------------
    // 单个入口字符串 → { className, methodName, descriptor }
    // ------------------------------------------------------------------

    /**
     * 解析用户输入的入口串。支持：
     *      com.demo.OrderController
     *      com.demo.OrderController#createOrder
     *      com.demo.OrderController#createOrder(Order)             ← 可读精确重载
     *      com.demo.OrderController#createOrder(Lcom/demo/Order;)V ← 旧 JVM 描述符
     */
    function parseEntryString(raw) {
        var s = raw == null ? '' : String(raw);
        var hashIdx = s.indexOf('#');
        if (hashIdx < 0) {
            return { className: s, methodName: null, descriptor: '' };
        }
        var cls = s.substring(0, hashIdx);
        var afterHash = s.substring(hashIdx + 1);
        var parenIdx = afterHash.indexOf('(');
        if (parenIdx < 0) {
            return { className: cls, methodName: afterHash, descriptor: '' };
        }
        var method = afterHash.substring(0, parenIdx);
        var closeParen = afterHash.lastIndexOf(')');
        if (closeParen >= 0) {
            var body = afterHash.substring(parenIdx + 1, closeParen);
            // 已经是 JVM descriptor 原样保留，否则把可读参数列表转成 descriptor
            var descriptor = looksLikeDescriptor(body) ? '(' + body + ')' : paramsToDescriptor(body);
            return { className: cls, methodName: method, descriptor: descriptor };
        }
        return { className: cls, methodName: method, descriptor: '' };
    }

    // ------------------------------------------------------------------
    // 样板（噪声）规则匹配：与后端 NoiseRuleService.isNoiseOnRules 同口径
    // ------------------------------------------------------------------

    /**
     * 编译正则——口径与后端 NoiseRuleService.matches 完全一致：
     *   - pattern 为 null 或空串（真正未填）→ null（视为"不限"，匹配全部）；
     *   - 否则（**含仅空白**，如 " "）→ 按字面编译，find 语义；非法正则 → false（永不匹配）。
     * 注意：**不以 trim() 判空** —— 后端仅在 isEmpty()（长度 0）时才视作不限。
     */
    function compileRegex(pattern) {
        if (pattern == null || pattern === '') return null;
        try {
            return new RegExp(pattern);
        } catch (e) {
            return false;
        }
    }

    /**
     * 预编译规则列表 → 判定用结构。
     * 仅保留 enabled 规则；正则此处的编译一次，判定热路径只 test()。
     * source 保持原值（含 ""、null、"all" 等），判定时再按后端 equalsIgnoreCase 口径处理。
     */
    function compileRules(rules) {
        var enabled = (rules || []).filter(function (r) { return r && r.enabled; });
        return enabled.map(function (r) {
            return {
                source: r.source != null ? r.source : null,
                paramCount: r.paramCount != null ? r.paramCount : null,
                methodRe: compileRegex(r.methodPattern),
                classRe: compileRegex(r.classPattern)
            };
        });
    }

    /** 大小写不敏感相等（对齐后端 String.equalsIgnoreCase） */
    function equalsIgnoreCase(a, b) {
        return String(a).toLowerCase() === String(b).toLowerCase();
    }

    /**
     * 按预编译规则判定是否命中（任一命中即噪声）。语义严格对齐后端 NoiseRuleService.isNoiseOnRules：
     *   - 来源：null/空串 或 任意大小写 "ALL" → 不限来源；否则大小写不敏感比较；
     *   - 方法名正则：null/空 → 不限；非法 → 永不命中；否则 test；
     *   - 类名正则：null/空 → 不限类；否则 test；
     *   - paramCount：null → 不限；否则严格相等。
     */
    function matchCompiledRules(compiled, src, className, methodName, paramCount) {
        compiled = compiled || [];
        for (var i = 0; i < compiled.length; i++) {
            var cr = compiled[i];
            if (cr.source != null && cr.source !== '' && !equalsIgnoreCase(cr.source, 'ALL')) {
                if (!equalsIgnoreCase(cr.source, src)) continue;
            }
            if (cr.methodRe === false) continue;                     // 非法正则 → 永不匹配
            if (cr.methodRe && !cr.methodRe.test(methodName)) continue;
            if (cr.classRe === false) continue;
            if (cr.classRe && !cr.classRe.test(className)) continue;
            if (cr.paramCount != null && cr.paramCount !== paramCount) continue;
            return true;
        }
        return false;
    }

    return {
        SIG_PRIM: SIG_PRIM,
        PRIM_TO_DESC: PRIM_TO_DESC,
        shortInternalName: shortInternalName,
        paramNamesFromDescriptor: paramNamesFromDescriptor,
        paramCountFromDescriptor: paramCountFromDescriptor,
        paramCountFromSignature: paramCountFromSignature,
        fullSignature: fullSignature,
        splitSignature: splitSignature,
        looksLikeDescriptor: looksLikeDescriptor,
        typeToDescriptor: typeToDescriptor,
        paramsToDescriptor: paramsToDescriptor,
        parseEntryString: parseEntryString,
        compileRegex: compileRegex,
        compileRules: compileRules,
        matchCompiledRules: matchCompiledRules,
        parseParamCount: paramCountFromSignature
    };
});
