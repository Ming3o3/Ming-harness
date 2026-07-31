package org.mingharness.runtime.repository;

import org.mingharness.runtime.domain.TenantPolicyAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;

/** 租户策略变更审计查询。 */
public interface TenantPolicyAuditRepository extends JpaRepository<TenantPolicyAudit, String> {

    List<TenantPolicyAudit> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    long deleteByCreatedAtBefore(Instant cutoff);
}
