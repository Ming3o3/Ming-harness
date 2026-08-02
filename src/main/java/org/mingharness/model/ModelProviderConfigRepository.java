package org.mingharness.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelProviderConfigRepository extends JpaRepository<ModelProviderConfig, String> {

    Optional<ModelProviderConfig> findByTenantIdAndUserId(String tenantId, String userId);
}
