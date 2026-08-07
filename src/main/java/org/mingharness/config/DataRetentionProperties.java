package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务数据保留配置。审计事件与 Run 一起清理，避免仅删除链中事件而破坏完整性校验。
 */
@ConfigurationProperties(prefix = "harness.data-retention")
public record DataRetentionProperties(
        boolean enabled,
        int runDays,
        int auditDays,
        int memoryDays,
        int documentDays,
        int evaluationDays,
        int outboxDays,
        int tenantPolicyAuditDays,
        int apiKeyAuditDays,
        int embeddingCacheDays,
        long cleanupIntervalMs,
        long cleanupInitialDelayMs,
        int batchSize
) {

    public DataRetentionProperties {
        runDays = positiveOrDefault(runDays, 90);
        auditDays = positiveOrDefault(auditDays, 365);
        memoryDays = positiveOrDefault(memoryDays, 30);
        documentDays = positiveOrDefault(documentDays, 30);
        evaluationDays = positiveOrDefault(evaluationDays, 90);
        outboxDays = positiveOrDefault(outboxDays, 14);
        tenantPolicyAuditDays = positiveOrDefault(tenantPolicyAuditDays, 365);
        apiKeyAuditDays = positiveOrDefault(apiKeyAuditDays, 365);
        embeddingCacheDays = positiveOrDefault(embeddingCacheDays, 30);
        cleanupIntervalMs = cleanupIntervalMs < 60_000 ? 3_600_000 : cleanupIntervalMs;
        cleanupInitialDelayMs = cleanupInitialDelayMs < 1_000 ? 60_000 : cleanupInitialDelayMs;
        batchSize = Math.min(1_000, Math.max(1, batchSize));
    }

    /** 运行记录必须至少保留到审计链保留期结束，防止截断仍可查询 Run 的审计链。 */
    public int effectiveRunRetentionDays() {
        return Math.max(runDays, auditDays);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value < 1 ? fallback : value;
    }
}
