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
    /** Worker 每次短事务状态变更都锁定最新 Run，避免取消或恢复覆盖执行结果。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from Run run where run.id = :runId")
    Optional<Run> findByIdForExecutionUpdate(@Param("runId") String runId);
    Optional<Run> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    long countByTenantIdAndUserIdAndEducationLearningAssignmentId(
            String tenantId, String userId, String educationLearningAssignmentId);
    Optional<Run> findTopByTenantIdAndUserIdAndEducationLearningAssignmentIdOrderByCreatedAtDesc(
            String tenantId, String userId, String educationLearningAssignmentId);
    long countByTenantIdAndStatusIn(String tenantId, List<RunStatus> statuses);
    /** 恢复器必须锁住候选 Run，等待并发 Worker 提交后再重新判断状态，避免覆盖最新结果。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select run from Run run
            where run.status = :status
              and run.leaseUntil is not null
              and run.leaseUntil <= :now
            order by run.leaseUntil asc
            """)
    List<Run> findStaleByLeaseForUpdate(@Param("status") RunStatus status,
                                        @Param("now") Instant now,
                                        org.springframework.data.domain.Pageable pageable);
    /** 没有租约的遗留任务按更新时间恢复，同样使用行锁避免多实例重复处理。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select run from Run run
            where run.status = :status
              and run.leaseUntil is null
              and run.updatedAt <= :threshold
            order by run.updatedAt asc
            """)
    List<Run> findStaleWithoutLeaseForUpdate(@Param("status") RunStatus status,
                                             @Param("threshold") Instant threshold,
                                             org.springframework.data.domain.Pageable pageable);
    List<Run> findTop100ByStatusAndUpdatedAtBefore(RunStatus status, Instant updatedAt);
    List<Run> findTop100ByStatusAndLeaseUntilBefore(RunStatus status, Instant leaseUntil);
    List<Run> findTop100ByStatusAndLeaseUntilIsNullAndUpdatedAtBefore(RunStatus status, Instant updatedAt);
    List<Run> findByStatusInAndFinishedAtBeforeOrderByFinishedAtAsc(
            List<RunStatus> statuses, Instant finishedAt, Pageable pageable);
    long countByStatus(RunStatus status);
}
