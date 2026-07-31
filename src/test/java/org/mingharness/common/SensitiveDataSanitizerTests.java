package org.mingharness.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证常见凭证格式会被统一替换，普通业务文本保持原样。 */
class SensitiveDataSanitizerTests {

    private final SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer();

    @Test
    void shouldRedactStructuredCredentialsTokensAndConnectionPasswords() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature-value-123456";
        String input = "{\"api_key\":\"super-secret-key\","
                + "\"authorization\":\"Bearer bearer-secret-123\","
                + "\"database\":\"postgresql://operator:db-password@localhost:5432/harness\","
                + "\"jwt\":\"" + jwt + "\"}";

        String sanitized = sanitizer.sanitize(input);

        assertFalse(sanitized.contains("super-secret-key"));
        assertFalse(sanitized.contains("bearer-secret-123"));
        assertFalse(sanitized.contains("db-password"));
        assertFalse(sanitized.contains(jwt));
        assertTrue(sanitized.contains("\"api_key\":\"[REDACTED]\""));
        assertTrue(sanitizer.containsSensitiveData(input));
    }

    @Test
    void shouldRedactPemPrivateKeyAndVendorTokens() {
        String input = "-----BEGIN PRIVATE KEY-----\nprivate-key-content\n-----END PRIVATE KEY----- "
                + "sk-12345678901234567890 ghp_123456789012345678901234";

        String sanitized = sanitizer.sanitize(input);

        assertFalse(sanitized.contains("private-key-content"));
        assertFalse(sanitized.contains("sk-12345678901234567890"));
        assertFalse(sanitized.contains("ghp_123456789012345678901234"));
    }

    @Test
    void shouldKeepOrdinaryBusinessTextUnchanged() {
        String input = "订单状态是已审核，token 数量为 10。";

        assertEquals(input, sanitizer.sanitize(input));
        assertFalse(sanitizer.containsSensitiveData(input));
    }

    @Test
    void shouldSanitizeBusinessExceptionMessageBeforeReturningIt() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(sanitizer);

        String responseMessage = handler.handleBusinessException(new BusinessException(
                HttpStatus.BAD_REQUEST, "TEST", "请求失败: api_key=do-not-return"))
                .getBody().message();

        assertFalse(responseMessage.contains("do-not-return"));
        assertTrue(responseMessage.contains(SensitiveDataSanitizer.REDACTION_MARKER));
    }
}
