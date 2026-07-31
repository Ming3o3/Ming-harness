package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, String> {
    List<MemoryEntry> findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, String userId);

    long deleteByExpiresAtLessThanEqual(Instant expiresAt);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
