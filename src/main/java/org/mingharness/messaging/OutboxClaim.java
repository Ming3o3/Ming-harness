package org.mingharness.messaging;

/** Relay 已经取得的 Outbox 发布租约，正文在数据库中保存，避免消息体进入 Redis。 */
public record OutboxClaim(String eventId, String payload) {
}
