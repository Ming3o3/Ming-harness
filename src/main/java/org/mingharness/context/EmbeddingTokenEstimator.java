package org.mingharness.context;

/**
 * 面向 embedding 输入的保守 token 估算器。
 *
 * <p>项目不把某一家供应商的 tokenizer 固化在核心链路中，因此这里使用可解释的上界近似：
 * CJK、非 ASCII 符号和标点按单 token 计，连续 ASCII 字母数字按每四个字符计一个 token，
 * 空白也单独保留预算。它不是供应商 tokenizer 的精确计数，但用于拒绝或截断请求时偏保守，
 * 不会把字符数误当成所有语言的 token 数量。</p>
 */
public final class EmbeddingTokenEstimator {

    private EmbeddingTokenEstimator() {
    }

    public static int estimate(String value) {
        if (value == null || value.isEmpty()) return 0;
        int tokens = 0;
        int asciiWordLength = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isAsciiWord(codePoint)) {
                asciiWordLength++;
                continue;
            }
            tokens += ceilDiv(asciiWordLength, 4);
            asciiWordLength = 0;
            tokens++;
        }
        return tokens + ceilDiv(asciiWordLength, 4);
    }

    /** 返回不超过预算的前缀，按 Unicode code point 截断，避免切断 surrogate pair。 */
    public static String truncate(String value, int maxTokens) {
        if (value == null || value.isEmpty() || maxTokens <= 0) return "";
        if (estimate(value) <= maxTokens) return value;
        int end = 0;
        int tokens = 0;
        int asciiWordLength = 0;
        while (end < value.length()) {
            int next = end + Character.charCount(value.codePointAt(end));
            int codePoint = value.codePointAt(end);
            int nextTokens;
            if (isAsciiWord(codePoint)) {
                nextTokens = tokens + ceilDiv(asciiWordLength + 1, 4)
                        - ceilDiv(asciiWordLength, 4);
            } else {
                nextTokens = tokens + 1;
            }
            if (nextTokens > maxTokens) break;
            tokens = nextTokens;
            asciiWordLength = isAsciiWord(codePoint) ? asciiWordLength + 1 : 0;
            end = next;
        }
        return value.substring(0, end);
    }

    private static boolean isAsciiWord(int codePoint) {
        return codePoint < 128
                && (Character.isLetterOrDigit(codePoint) || codePoint == '_');
    }

    private static int ceilDiv(int value, int divisor) {
        return value == 0 ? 0 : (value + divisor - 1) / divisor;
    }
}
