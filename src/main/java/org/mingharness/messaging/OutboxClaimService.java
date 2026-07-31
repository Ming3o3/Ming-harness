package org.mingharness.messaging;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.MessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 负责 Outbox 发布租约的抢占和最终回写。
 *
 * 抢占事务只持有数据库行锁到状态提交，真正的 RabbitMQ 网络调用在事务外执行；
 * Relay 崩溃后，其他实例可在租约过期时接管。发布确认之前崩溃仍允许重复投递，
 * 由 Run 执行锁和数据库状态保证业务副作用不会重复执行。
 */
@Service
@ConditionalOnProperty(prefix = "harness.messaging", name = "enabled", havingValue = "true")
public class OutboxClaimService {

    private static final int MAX_CLAIM_BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final MessagingProperties properties;
    private final SensitiveDataSanitizer sanitizer;
    private final String relayId = "relay-" + UUID.randomUUID();

    public OutboxClaimService(OutboxEventRepository repository,
                              MessagingProperties properties,
                              SensitiveDataSanitizer sanitizer) {
        this.repository = repository;
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    /** 在独立事务内批量抢占待发布事件。 */
    @Transactional
    public List<OutboxClaim> claimPending() {
        return claimPending(MAX_CLAIM_BATCH_SIZE);
    }

    /**
     * 在剩余队列容量内抢占待发布事件。
     *
     * <p>即使调用方错误传入过大值，也不会让单个 Relay 一次抢占超过固定批次，
     * 防止网络确认较慢时长期占用过多 Outbox 租约。</p>
     */
    @Transactional
    public List<OutboxClaim> claimPending(int requestedLimit) {
        if (requestedLimit < 1) {
            return List.of();
        }
        Instant now = Instant.now();
        Instant leaseUntil = now.plusMillis(properties.outboxClaimLeaseMs());
        int limit = Math.min(MAX_CLAIM_BATCH_SIZE, requestedLimit);
        return repository.findClaimableForUpdate(OutboxStatus.PENDING, OutboxStatus.PUBLISHING, now,
                        PageRequest.of(0, limit)).stream()
                .filter(event -> event.claim(relayId, now, leaseUntil))
                .map(event -> new OutboxClaim(event.getId(), event.getPayload()))
                .toList();
    }

    /** 只有原 Relay 仍持有租约时才允许标记发布成功。 */
    @Transactional
    public boolean markPublished(String eventId) {
        return repository.findByIdForPublishUpdate(eventId)
                .map(event -> event.markPublished(relayId))
                .orElse(false);
    }

    /** 只有原 Relay 仍持有租约时才允许记录失败，错误内容统一脱敏。 */
    @Transactional
    public boolean markFailed(String eventId, String error) {
        String safeError = sanitizer.sanitize(error == null || error.isBlank() ? "Outbox 发布失败" : error);
        return repository.findByIdForPublishUpdate(eventId)
                .map(event -> event.markFailed(relayId, safeError, properties.maxAttempts()))
                .orElse(false);
    }

    /**
     * 读取已经耗尽投递次数的事件，用于把对应 Run 及时落为终态。
     * 查询与终态回写分为两个短事务：Relay 绝不在 RabbitMQ 网络调用期间持有 Run 行锁。
     */
    @Transactional(readOnly = true)
    public Optional<FailedOutbox> findTerminalFailure(String eventId) {
        return repository.findById(eventId)
                .filter(event -> event.getStatus() == OutboxStatus.FAILED)
                .map(event -> new FailedOutbox(event.getRunId(), event.getTenantId(), event.getLastError()));
    }

    public String relayId() {
        return relayId;
    }

    /** 已耗尽发布重试预算的 Outbox 最小快照，不暴露完整消息正文。 */
    public record FailedOutbox(String runId, String tenantId, String error) {
    }
}
