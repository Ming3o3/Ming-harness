package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
    List<KnowledgeDocument> findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
