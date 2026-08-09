package org.mingharness.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RunFeedbackRepository extends JpaRepository<RunFeedback, String> {
    Optional<RunFeedback> findByRunIdAndUserId(String runId, String userId);
    long deleteByRunId(String runId);
}
