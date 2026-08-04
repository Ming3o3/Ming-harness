package org.mingharness.runtime.repository;

import org.mingharness.runtime.domain.TenantPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/** 组织运行策略持久化接口。 */
public interface TenantPolicyRepository extends JpaRepository<TenantPolicy, String> {
}
