package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.Instant;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, String> {
    List<MemoryEntry> findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, String userId);

    List<MemoryEntry> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String tenantId, Pageable pageable);

    long deleteByExpiresAtLessThanEqual(Instant expiresAt);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
