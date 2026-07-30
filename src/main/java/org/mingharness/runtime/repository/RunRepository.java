package org.mingharness.runtime.repository;

import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, String> {
    List<Run> findTop50ByOrderByCreatedAtDesc();
    List<Run> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);
    Optional<Run> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    long countByTenantIdAndStatusIn(String tenantId, List<RunStatus> statuses);
    long countByStatus(RunStatus status);
}
