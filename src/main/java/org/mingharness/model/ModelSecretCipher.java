package org.mingharness.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 使用独立 AES-GCM 标签保护数据库中的模型 API Key。 */
@Component
public class ModelSecretCipher {

    private static final byte[] DERIVATION_LABEL = "ming-harness-model-secret-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ModelSecretCipher(@Value("${harness.model.secret-encryption-key:${harness.workspace.path-encryption-key}}") String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("模型密钥加密配置不能为空");
        }
        this.key = new SecretKeySpec(derive(rawKey), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密模型 API Key", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            byte[] packed = Base64.getUrlDecoder().decode(ciphertext);
            if (packed.length <= IV_BYTES) throw new IllegalArgumentException("密文长度不足");
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(packed, IV_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("模型 API Key 无法解密，请重新配置模型供应商", exception);
        }
    }

    private byte[] derive(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DERIVATION_LABEL);
            return digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
        }
    }
}
