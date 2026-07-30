package org.mingharness.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationReportRepository extends JpaRepository<EvaluationReport, String> {
    List<EvaluationReport> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
