package org.mingharness.retention;

/** 一次数据保留清理的统计结果，便于测试、指标和运维排查。 */
public record RetentionCleanupResult(
        int runsDeleted,
        int auditEventsDeleted,
        int stepsDeleted,
        int memoriesDeleted,
        int documentsDeleted,
        int chunksDeleted,
        int parentWindowsDeleted,
        int evaluationReportsDeleted,
        int retrievalEvaluationReportsDeleted,
        int outboxEventsDeleted,
        int tenantPolicyAuditsDeleted,
        int apiKeyAuditsDeleted
) {
    public int totalDeleted() {
        return runsDeleted + auditEventsDeleted + stepsDeleted + memoriesDeleted
                + documentsDeleted + chunksDeleted + parentWindowsDeleted + evaluationReportsDeleted
                + retrievalEvaluationReportsDeleted + outboxEventsDeleted + tenantPolicyAuditsDeleted
                + apiKeyAuditsDeleted;
    }
}
