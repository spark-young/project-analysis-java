package com.spark.projectanalysis.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 凭证脱敏工具：把字符串中的 userinfo（user:token@）抹除，避免 token 落入日志或错误响应。
 *
 * <p>覆盖形式：</p>
 * <ul>
 *   <li>{@code https://alice:s3cret@github.com/x.git} → {@code https://***@github.com/x.git}</li>
 *   <li>{@code user:pass@host} → {@code ***@host}</li>
 * </ul>
 */
public final class CredentialRedactor {

    private CredentialRedactor() {
    }

    /**
     * 匹配「可选 scheme:// + user:pass@」形式。
     * - scheme 组可选（http://、https://、ssh:// 等）；
     * - user 不含 :/@/#/?，到首个 : 截止；
     * - pass 不含 @，到 @ 截止（允许 token 自身包含 :）。
     */
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?<scheme>(?i)[a-z][a-z0-9+.-]*://)?(?<user>[^@\\s:/?#]+):(?<pass>[^@\\s]+)@");

    /**
     * 脱敏输入字符串中的凭证信息。null 原样返回；无匹配则原样返回。
     */
    public static String redact(String input) {
        if (input == null) {
            return null;
        }
        Matcher m = CREDENTIAL_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String scheme = m.group("scheme");
            m.appendReplacement(sb, Matcher.quoteReplacement(scheme != null ? scheme + "***@" : "***@"));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
