package org.mingharness.runtime.repository;

import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RunRepository extends JpaRepository<Run, String> {

    /** 审计追加时锁定 Run 行，保证同一 Run 的事件序号不会分叉。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from Run run where run.id = :runId")
    Optional<Run> findByIdForAuditUpdate(@Param("runId") String runId);
    List<Run> findTop50ByOrderByCreatedAtDesc();
    List<Run> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);
    Page<Run> findByTenantId(String tenantId, Pageable pageable);
    Page<Run> findByTenantIdAndStatus(String tenantId, RunStatus status, Pageable pageable);
    @Query("select run.status from Run run where run.id = :runId")
    Optional<RunStatus> findStatusById(@Param("runId") String runId);
    /** 取消操作需要等待 Worker 释放行锁后读取最新版本，避免用旧实体覆盖执行结果。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from Run run where run.id = :runId")
    Optional<Run> findByIdForCancelUpdate(@Param("runId") String runId);
    Optional<Run> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    long countByTenantIdAndStatusIn(String tenantId, List<RunStatus> statuses);
    List<Run> findTop100ByStatusAndUpdatedAtBefore(RunStatus status, Instant updatedAt);
    List<Run> findTop100ByStatusAndLeaseUntilBefore(RunStatus status, Instant leaseUntil);
    List<Run> findTop100ByStatusAndLeaseUntilIsNullAndUpdatedAtBefore(RunStatus status, Instant updatedAt);
    List<Run> findByStatusInAndFinishedAtBeforeOrderByFinishedAtAsc(
            List<RunStatus> statuses, Instant finishedAt, Pageable pageable);
    long countByStatus(RunStatus status);
}
