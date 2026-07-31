package org.mingharness.messaging;

/** Outbox 事件发布状态。 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
