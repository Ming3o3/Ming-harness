package org.mingharness.security;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 数据库 API Key 查询接口。 */
public interface ApiKeyCredentialRepository extends JpaRepository<ApiKeyCredential, String> {

    Optional<ApiKeyCredential> findByKeyHash(String keyHash);

    List<ApiKeyCredential> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
}
