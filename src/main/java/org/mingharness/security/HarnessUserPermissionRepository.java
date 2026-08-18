package org.mingharness.security;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HarnessUserPermissionRepository extends JpaRepository<HarnessUserPermission, String> {

    Optional<HarnessUserPermission> findByTenantIdAndUserId(String tenantId, String userId);

    List<HarnessUserPermission> findByTenantIdOrderByUpdatedAtDesc(String tenantId, PageRequest pageRequest);
}
