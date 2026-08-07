package org.mingharness.context;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContextParentWindowRepository extends JpaRepository<ContextParentWindow, String> {

    List<ContextParentWindow> findByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNullOrderByWindowIndexAsc(
            String tenantId, String parentType, String parentId);

    ContextParentWindow findByIdAndTenantIdAndParentTypeAndParentIdAndDeletedAtIsNull(
            String id, String tenantId, String parentType, String parentId);

    long deleteByParentTypeAndParentId(String parentType, String parentId);
}
