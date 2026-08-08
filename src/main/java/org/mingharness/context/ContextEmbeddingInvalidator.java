package org.mingharness.context;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Embedding 配置切换后清空组织旧向量，要求显式重建索引。 */
@Component
public class ContextEmbeddingInvalidator {

    private final JdbcTemplate jdbcTemplate;

    public ContextEmbeddingInvalidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void invalidateTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return;
        try {
            jdbcTemplate.update("""
                    UPDATE harness_context_chunks
                       SET embedding = NULL,
                           embedding_model = NULL,
                           embedded_at = NULL,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND deleted_at IS NULL
                    """, tenantId);
        } catch (RuntimeException ignored) {
            // H2/local 以及迁移尚未完成时由后续索引状态检查暴露问题，不阻断保存配置。
        }
    }
}
