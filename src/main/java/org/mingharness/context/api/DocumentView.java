package org.mingharness.context.api;

import org.mingharness.context.KnowledgeDocument;

import java.time.Instant;

public record DocumentView(
        String id,
        String tenantId,
        String ownerUserId,
        String title,
        String content,
        String sensitivity,
        String allowedUsers,
        Instant createdAt,
        Instant updatedAt
) {

    public static DocumentView from(KnowledgeDocument document) {
        return new DocumentView(document.getId(), document.getTenantId(), document.getOwnerUserId(),
                document.getTitle(), document.getContent(), document.getSensitivity(),
                document.getAllowedUsers(), document.getCreatedAt(), document.getUpdatedAt());
    }
}
