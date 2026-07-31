package org.mingharness.messaging;

/** Outbox 事件发布状态。 */
public enum OutboxStatus {
    PENDING,
    /** Relay 已取得短期发布租约，其他实例不能重复发送。 */
    PUBLISHING,
    PUBLISHED,
    FAILED
}
