package org.mingharness.runtime.application;

import jakarta.annotation.PreDestroy;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.api.RunDetail;
import org.mingharness.runtime.domain.RunStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/**
 * 向已授权客户端推送 Run 的最新持久化快照。
 *
 * <p>Worker 可能位于其他应用实例，不能只依赖 JVM 内存事件；订阅存在时本服务按短周期读取
 * 数据库并仅在状态、步骤或结果发生变化时发送 SSE。前端保留 HTTP 轮询作为连接中断兜底。</p>
 */
@Service
public class RunEventStreamService {

    private final RunService runService;
    private final long heartbeatMs;
    private final int maxSubscribers;
    private final ConcurrentMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final Semaphore subscriberPermits;

    public RunEventStreamService(RunService runService,
                                 @Value("${harness.runtime.event-stream-heartbeat-ms:15000}") long heartbeatMs,
                                 @Value("${harness.runtime.event-stream-max-subscribers:200}") int maxSubscribers) {
        this.runService = runService;
        this.heartbeatMs = Math.max(1_000, heartbeatMs);
        this.maxSubscribers = Math.max(1, maxSubscribers);
        this.subscriberPermits = new Semaphore(this.maxSubscribers);
    }

    /** 创建订阅前先执行既有租户校验，跨租户请求不会得到连接或任务存在性信息。 */
    public SseEmitter subscribe(String runId, String tenantId) {
        RunDetail initial = runService.getDetail(runId, tenantId);
        // size() 不是并发配额原语；用信号量确保同时建立连接时也不会突破实例上限。
        if (!subscriberPermits.tryAcquire()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RUN_EVENT_STREAM_LIMIT_REACHED",
                    "实时执行连接已达到当前实例上限，请稍后重试");
        }
        SseEmitter emitter = new SseEmitter(0L);
        String subscriptionId = UUID.randomUUID().toString();
        Subscription subscription = new Subscription(subscriptionId, runId, tenantId, emitter,
                fingerprint(initial), Instant.now());
        subscriptions.put(subscriptionId, subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscriptionId));
        emitter.onTimeout(() -> subscriptions.remove(subscriptionId));
        emitter.onError(error -> subscriptions.remove(subscriptionId));
        if (!send(subscription, "snapshot", initial)) {
            return emitter;
        }
        if (terminal(initial)) {
            close(subscription);
        }
        return emitter;
    }

    /** 仅在有订阅时轮询数据库，保证多实例 Worker 完成步骤后也能被当前窗口看到。 */
    @Scheduled(fixedDelayString = "${harness.runtime.event-stream-poll-ms:750}")
    public void publishChanges() {
        for (Subscription subscription : subscriptions.values()) {
            try {
                RunDetail latest = runService.getDetail(subscription.runId, subscription.tenantId);
                String latestFingerprint = fingerprint(latest);
                if (!Objects.equals(subscription.fingerprint, latestFingerprint)) {
                    subscription.fingerprint = latestFingerprint;
                    subscription.lastKeepaliveAt = Instant.now();
                    if (!send(subscription, "run", latest)) continue;
                    if (terminal(latest)) {
                        close(subscription);
                    }
                } else if (Instant.now().minusMillis(heartbeatMs).isAfter(subscription.lastKeepaliveAt)) {
                    subscription.lastKeepaliveAt = Instant.now();
                    sendKeepalive(subscription);
                }
            } catch (BusinessException exception) {
                // 任务被删除或权限事实改变时主动关闭连接，不将内部异常详情写入 SSE。
                close(subscription);
            } catch (RuntimeException exception) {
                // 短暂数据库故障由下一轮重试；持续失败时客户端仍会走自身 HTTP 轮询兜底。
            }
        }
    }

    int activeSubscriberCount() {
        return subscriptions.size();
    }

    /**
     * SSE 连接通常是长连接；应用进入关闭流程时必须主动结束它们，
     * 否则 Tomcat 的优雅停机会一直把连接视为活动请求。
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        shutdown();
    }

    @PreDestroy
    public void shutdown() {
        subscriptions.values().forEach(this::close);
    }

    private boolean send(Subscription subscription, String eventName, RunDetail detail) {
        try {
            subscription.emitter.send(SseEmitter.event().name(eventName).data(detail));
            return true;
        } catch (IOException | IllegalStateException exception) {
            close(subscription);
            return false;
        }
    }

    private void sendKeepalive(Subscription subscription) {
        try {
            subscription.emitter.send(SseEmitter.event().comment("keepalive"));
        } catch (IOException | IllegalStateException exception) {
            close(subscription);
        }
    }

    private void close(Subscription subscription) {
        // 只有实际从映射删除的订阅才归还配额，onCompletion/onError 的重复回调不会多次释放。
        if (subscriptions.remove(subscription.id, subscription)) {
            subscriberPermits.release();
        }
        try {
            subscription.emitter.complete();
        } catch (RuntimeException ignored) {
            // 客户端断开后 complete 可能抛出异常，订阅已被移除即可。
        }
    }

    private boolean terminal(RunDetail detail) {
        RunStatus status = detail.run().status();
        return status == RunStatus.SUCCEEDED || status == RunStatus.FAILED
                || status == RunStatus.CANCELLED || status == RunStatus.TIMED_OUT;
    }

    /** 指纹只用于比较，不会在日志或 SSE 中暴露输入、输出、错误与工具参数正文。 */
    private String fingerprint(RunDetail detail) {
        StringBuilder value = new StringBuilder(String.valueOf(detail.run().updatedAt()))
                .append('|').append(detail.run().status())
                .append('|').append(detail.run().stepCount())
                .append('|').append(Objects.hashCode(detail.run().output()))
                .append('|').append(Objects.hashCode(detail.run().error()));
        detail.steps().forEach(step -> value.append('|').append(step.id())
                .append(':').append(step.status())
                .append(':').append(step.attempt())
                .append(':').append(Objects.hashCode(step.output()))
                .append(':').append(Objects.hashCode(step.error())));
        return Integer.toHexString(value.toString().hashCode());
    }

    private static final class Subscription {
        private final String id;
        private final String runId;
        private final String tenantId;
        private final SseEmitter emitter;
        private volatile String fingerprint;
        private volatile Instant lastKeepaliveAt;

        private Subscription(String id, String runId, String tenantId, SseEmitter emitter,
                             String fingerprint, Instant lastKeepaliveAt) {
            this.id = id;
            this.runId = runId;
            this.tenantId = tenantId;
            this.emitter = emitter;
            this.fingerprint = fingerprint;
            this.lastKeepaliveAt = lastKeepaliveAt;
        }
    }
}
