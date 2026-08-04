package org.mingharness.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 工作区按组织和用户双重隔离，不能通过 ID 跨账户读取本机路径。 */
public interface LocalWorkspaceRepository extends JpaRepository<LocalWorkspace, String> {

    Optional<LocalWorkspace> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);

    List<LocalWorkspace> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);
}
