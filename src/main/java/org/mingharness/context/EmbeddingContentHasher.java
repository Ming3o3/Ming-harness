package org.mingharness.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 统一生成 embedding 缓存和上下文内容使用的 SHA-256 哈希。 */
final class EmbeddingContentHasher {

    private EmbeddingContentHasher() {
    }

    static String sha256(String value) {
        if (value == null) throw new IllegalArgumentException("待哈希内容不能为空");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(item & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256 算法", exception);
        }
    }
}
