package org.mingharness.security;

import org.mingharness.common.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 从现有业务事实汇总租户用户。当前系统没有单独的用户表，目录因此不创建新的用户主数据，
 * 而是从已经出现过用户标识的认证、运行、会话和教育表中去重读取。
 */
@Service
public class HarnessUserDirectoryService {

    private static final int MAX_LIST_SIZE = 500;
    private static final String USER_DIRECTORY_SQL = """
            SELECT user_id FROM (
                SELECT user_id FROM harness_api_keys WHERE tenant_id = ?
                UNION ALL SELECT user_id FROM harness_user_permissions WHERE tenant_id = ?
                UNION ALL SELECT user_id FROM harness_runs WHERE tenant_id = ?
                UNION ALL SELECT owner_user_id AS user_id FROM harness_context_documents WHERE tenant_id = ?
                UNION ALL SELECT user_id FROM harness_context_memories WHERE tenant_id = ?
                UNION ALL SELECT user_id FROM harness_conversations WHERE tenant_id = ?
                UNION ALL SELECT user_id FROM harness_local_workspaces WHERE tenant_id = ?
                UNION ALL SELECT user_id FROM harness_model_provider_configs WHERE tenant_id = ?
                UNION ALL SELECT user_id FROM harness_learner_profiles WHERE tenant_id = ?
                UNION ALL SELECT owner_user_id AS user_id FROM harness_education_courses WHERE tenant_id = ?
                UNION ALL SELECT learner_user_id AS user_id FROM harness_education_enrollments WHERE tenant_id = ?
                UNION ALL SELECT teacher_user_id AS user_id FROM harness_learning_assignments WHERE tenant_id = ?
                UNION ALL SELECT learner_user_id AS user_id FROM harness_learning_assignments WHERE tenant_id = ?
            ) candidates
            WHERE user_id IS NOT NULL AND TRIM(user_id) <> ''
            GROUP BY user_id
            ORDER BY user_id
            LIMIT %d
            """.formatted(MAX_LIST_SIZE);

    private final JdbcTemplate jdbcTemplate;

    public HarnessUserDirectoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<UserDirectoryView> list(String tenantId, String currentUserId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "组织不能为空");
        }
        String normalizedTenant = tenantId.trim();
        Set<String> userIds = new TreeSet<>();
        try {
            List<Object> parameters = new ArrayList<>();
            for (int index = 0; index < 13; index++) {
                parameters.add(normalizedTenant);
            }
            userIds.addAll(jdbcTemplate.query(
                    USER_DIRECTORY_SQL,
                    (resultSet, rowNumber) -> resultSet.getString("user_id"),
                    parameters.toArray()));
        } catch (DataAccessException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STORE_UNAVAILABLE",
                    "用户目录暂时不可用，请稍后重试");
        }
        if (currentUserId != null && !currentUserId.isBlank()) {
            userIds.add(currentUserId.trim());
        }
        return userIds.stream()
                .limit(MAX_LIST_SIZE)
                .map(userId -> new UserDirectoryView(normalizedTenant, userId))
                .toList();
    }
}
