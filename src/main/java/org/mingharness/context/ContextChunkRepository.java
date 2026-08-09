package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ContextChunkRepository extends JpaRepository<ContextChunk, String> {

    List<ContextChunk> findByParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(
            String parentType, String parentId);

    List<ContextChunk> findByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNullOrderByChunkIndexAsc(
            String tenantId, String parentType, String parentId);

    long countByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNull(
            String tenantId, String parentType, String parentId);

    long countByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNull(String tenantId);

    List<ContextChunk> findByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNullOrderByCreatedAtAsc(
            String tenantId, Pageable pageable);

    List<ContextChunk> findByParentTypeAndParentIdAndDeletedAtIsNull(
            String parentType, String parentId);

    long deleteByTenantIdAndParentTypeAndParentId(
            String tenantId, String parentType, String parentId);

    long deleteByDeletedAtBefore(Instant deletedAt);

    /** 清理父记录已被硬删除的 chunk；软删除父记录仍由保留时间策略单独处理。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM harness_context_chunks c
             WHERE NOT EXISTS (
                       SELECT 1 FROM harness_context_documents d
                        WHERE c.parent_type = 'DOCUMENT' AND d.id = c.parent_id
                   )
               AND NOT EXISTS (
                       SELECT 1 FROM harness_context_memories m
                        WHERE c.parent_type = 'MEMORY' AND m.id = c.parent_id
                   )
            """, nativeQuery = true)
    int deleteOrphanedChunks();
}
