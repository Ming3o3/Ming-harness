package org.mingharness.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EvaluationCaseRepository extends JpaRepository<EvaluationCase, String> {
    List<EvaluationCase> findTop200ByTenantIdOrderByCreatedAtDesc(String tenantId);
    long deleteByCreatedAtBefore(Instant createdAt);
}
