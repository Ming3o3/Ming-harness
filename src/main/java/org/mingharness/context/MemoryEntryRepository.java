package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, String> {
    List<MemoryEntry> findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, String userId);

    List<MemoryEntry> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String tenantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MemoryEntry m where m.id = :id")
    Optional<MemoryEntry> findByIdForUpdate(@Param("id") String id);

    long deleteByExpiresAtLessThanEqual(Instant expiresAt);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
