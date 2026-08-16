package org.mingharness.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModelProviderConfigRepository extends JpaRepository<TenantModelProviderConfig, String> {
}
