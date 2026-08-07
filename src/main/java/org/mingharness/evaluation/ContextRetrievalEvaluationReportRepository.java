package org.mingharness.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ContextRetrievalEvaluationReportRepository
        extends JpaRepository<ContextRetrievalEvaluationReport, String> {

    List<ContextRetrievalEvaluationReport> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);

    long deleteByCreatedAtBefore(Instant createdAt);
}
