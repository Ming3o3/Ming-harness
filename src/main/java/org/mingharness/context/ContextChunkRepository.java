package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ContextChunkRepository extends JpaRepository<ContextChunk, String> {

    List<ContextChunk> findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(
            String parentType, String parentId);

    List<ContextChunk> findByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(
            String tenantId, String parentType, String parentId);

    List<ContextChunk> findByParentTypeAndParentIdAndDeletedAtIsNull(
            String parentType, String parentId);

    long deleteByParentTypeAndParentId(String parentType, String parentId);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
