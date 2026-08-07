package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.Instant;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
    List<KnowledgeDocument> findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

    List<KnowledgeDocument> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String tenantId, Pageable pageable);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
