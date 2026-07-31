package org.mingharness.runtime.repository;

import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface RunRepository extends JpaRepository<Run, String> {
    List<Run> findTop50ByOrderByCreatedAtDesc();
    List<Run> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);
    Optional<Run> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    long countByTenantIdAndStatusIn(String tenantId, List<RunStatus> statuses);
    List<Run> findTop100ByStatusAndUpdatedAtBefore(RunStatus status, Instant updatedAt);
    long countByStatus(RunStatus status);
}
