package org.mingharness.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    List<AuditEvent> findTop100ByRunIdOrderByCreatedAtDesc(String runId);

    List<AuditEvent> findByRunIdOrderByIntegritySequenceAsc(String runId);
}
