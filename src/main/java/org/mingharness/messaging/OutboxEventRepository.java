package org.mingharness.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * 使用悲观行锁抢占可发布事件；发布动作在事务外执行，避免等待 RabbitMQ 确认时长期占用数据库锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from OutboxEvent event
            where (event.status = :pending and (event.nextAttemptAt is null or event.nextAttemptAt <= :now))
               or (event.status = :publishing and (event.claimUntil is null or event.claimUntil <= :now))
            order by event.createdAt asc
            """)
    List<OutboxEvent> findClaimableForUpdate(@Param("pending") OutboxStatus pending,
                                              @Param("publishing") OutboxStatus publishing,
                                              @Param("now") Instant now,
                                              Pageable pageable);

    /** 完成回写同样锁定行，避免过期 Relay 覆盖被接管事件的最终状态。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :eventId")
    java.util.Optional<OutboxEvent> findByIdForPublishUpdate(@Param("eventId") String eventId);

    long deleteByRunId(String runId);

    long deleteByStatusInAndCreatedAtBefore(List<OutboxStatus> statuses, Instant createdAt);
}
