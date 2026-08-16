package org.mingharness.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelProviderConfigSnapshotRepository extends JpaRepository<ModelProviderConfigSnapshot, String> {

    Optional<ModelProviderConfigSnapshot> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);

    Optional<ModelProviderConfigSnapshot> findByIdAndTenantId(String id, String tenantId);
}
