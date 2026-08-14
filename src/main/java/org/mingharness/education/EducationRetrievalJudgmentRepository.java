package org.mingharness.education;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationRetrievalJudgmentRepository
        extends JpaRepository<EducationRetrievalJudgment, String> {

    List<EducationRetrievalJudgment> findByTenantIdAndRunIdOrderByCreatedAtAsc(
            String tenantId, String runId);

    List<EducationRetrievalJudgment> findByTenantIdOrderByCreatedAtAsc(String tenantId);

    List<EducationRetrievalJudgment> findByTenantIdAndEvaluatorUserIdOrderByCreatedAtAsc(
            String tenantId, String evaluatorUserId);
}
