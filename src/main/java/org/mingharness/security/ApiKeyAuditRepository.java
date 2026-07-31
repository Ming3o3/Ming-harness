package org.mingharness.security;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;

/** API Key 生命周期审计查询接口。 */
public interface ApiKeyAuditRepository extends JpaRepository<ApiKeyAudit, String> {

    List<ApiKeyAudit> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    long deleteByCreatedAtBefore(Instant cutoff);
}
