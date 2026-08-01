package org.mingharness.workspace;

import org.mingharness.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用 AES-GCM 保护数据库中的本机绝对路径，密文被篡改时会被拒绝。 */
@Component
public class WorkspacePathCipher {

    private static final byte[] DERIVATION_LABEL = "ming-harness-workspace-path-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public WorkspacePathCipher(@Value("${harness.workspace.path-encryption-key}") String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("harness.workspace.path-encryption-key 不能为空");
        }
        this.key = new SecretKeySpec(derive(rawKey), "AES");
    }

    public String encrypt(String plaintext) {
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
            throw new IllegalStateException("无法加密本地工作区路径", exception);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(ciphertext);
            if (packed.length <= IV_BYTES) throw new IllegalArgumentException("密文长度不足");
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, IV_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "WORKSPACE_PATH_UNAVAILABLE",
                    "本地工作区路径无法解密，请重新授权该工作区");
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
