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

    List<KnowledgeDocument> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String tenantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from KnowledgeDocument d where d.id = :id")
    Optional<KnowledgeDocument> findByIdForUpdate(@Param("id") String id);

    long deleteByDeletedAtBefore(Instant deletedAt);
}
