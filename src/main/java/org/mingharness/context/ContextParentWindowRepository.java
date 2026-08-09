package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ContextParentWindowRepository extends JpaRepository<ContextParentWindow, String> {

    List<ContextParentWindow> findByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNullOrderByWindowIndexAsc(
            String tenantId, String parentType, String parentId);

    ContextParentWindow findByIdAndTenantIdAndParentTypeAndParentIdAndDeletedAtIsNull(
            String id, String tenantId, String parentType, String parentId);

    long deleteByDeletedAtBefore(Instant deletedAt);

    long deleteByTenantIdAndParentTypeAndParentId(
            String tenantId, String parentType, String parentId);

    /** 清理父文档已被硬删除且不再被任何子块引用的窗口。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM harness_context_parent_windows w
             WHERE NOT EXISTS (
                       SELECT 1 FROM harness_context_documents d
                        WHERE w.parent_type = 'DOCUMENT' AND d.id = w.parent_id
                   )
               AND NOT EXISTS (
                       SELECT 1 FROM harness_context_memories m
                        WHERE w.parent_type = 'MEMORY' AND m.id = w.parent_id
                   )
               AND NOT EXISTS (
                       SELECT 1 FROM harness_context_chunks c
                        WHERE c.parent_window_id = w.id
                   )
            """, nativeQuery = true)
    int deleteOrphanedParentWindows();
}
