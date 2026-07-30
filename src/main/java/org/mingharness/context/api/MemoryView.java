package org.mingharness.context.api;

import org.mingharness.context.MemoryEntry;

import java.time.Instant;

public record MemoryView(
        String id,
        String tenantId,
        String userId,
        String memoryType,
        String content,
        String sourceRunId,
        Instant expiresAt,
        Instant createdAt
) {

    public static MemoryView from(MemoryEntry memory) {
        return new MemoryView(memory.getId(), memory.getTenantId(), memory.getUserId(),
                memory.getMemoryType(), memory.getContent(), memory.getSourceRunId(),
                memory.getExpiresAt(), memory.getCreatedAt());
    }
}
