package org.mingharness.messaging;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.MessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
        Instant now = Instant.now();
        Instant leaseUntil = now.plusMillis(properties.outboxClaimLeaseMs());
        return repository.findClaimableForUpdate(OutboxStatus.PENDING, OutboxStatus.PUBLISHING, now,
                        PageRequest.of(0, 50)).stream()
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

    public String relayId() {
        return relayId;
    }
}
