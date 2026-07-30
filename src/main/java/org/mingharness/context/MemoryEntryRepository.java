package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, String> {
    List<MemoryEntry> findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, String userId);
}
