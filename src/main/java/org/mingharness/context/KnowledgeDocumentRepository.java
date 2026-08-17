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

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
    List<KnowledgeDocument> findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

    /**
     * 只读取课程资料的非正文元数据，供管理员治理视图补齐资料标题和所有者。
     * 正文仍不会通过教育接口暴露。
     */
    List<KnowledgeDocument> findByTenantIdAndIdInAndDeletedAtIsNull(String tenantId, List<String> documentIds);

    /**
     * 教育检索先由课程元数据限定文档集合，再进行关键词召回。
     * 不使用全库“最新 100 篇”的快捷查询，避免有效但较早上传的课程资料被截断。
     */
    List<KnowledgeDocument> findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, List<String> documentIds);

    List<KnowledgeDocument> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String tenantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from KnowledgeDocument d where d.id = :id")
    Optional<KnowledgeDocument> findByIdForUpdate(@Param("id") String id);

    List<KnowledgeDocument> findByImportStatusAndImportSourcePathIsNotNull(DocumentImportStatus status);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
