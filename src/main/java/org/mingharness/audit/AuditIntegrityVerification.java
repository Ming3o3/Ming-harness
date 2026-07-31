package org.mingharness.audit;

/** 审计链完整性校验结果，legacyEventCount 表示迁移前未签名的历史记录数量。 */
public record AuditIntegrityVerification(
        String runId,
        boolean valid,
        int checkedEventCount,
        int legacyEventCount,
        String failureCode,
        String message
) {
}
