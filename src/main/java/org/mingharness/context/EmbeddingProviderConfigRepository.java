package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmbeddingProviderConfigRepository extends JpaRepository<EmbeddingProviderConfig, String> {

    Optional<EmbeddingProviderConfig> findByTenantId(String tenantId);
}
