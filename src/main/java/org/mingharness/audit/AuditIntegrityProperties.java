package org.mingharness.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 审计 HMAC 完整性密钥配置，正式环境必须由密钥系统注入。 */
@ConfigurationProperties(prefix = "harness.audit")
public record AuditIntegrityProperties(String integrityKey) {

    public AuditIntegrityProperties {
        if (integrityKey == null || integrityKey.isBlank()) {
            throw new IllegalArgumentException("harness.audit.integrity-key 不能为空");
        }
    }
}
