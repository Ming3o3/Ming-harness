package org.mingharness.context;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * PostgreSQL pgvector JDBC 写入器。
 *
 * <p>embedding 列不映射为 JPA 属性，而是使用参数绑定后 CAST 为 vector，避免向量类型
 * 驱动和 Hibernate 版本耦合。H2/local 模式会被识别为不支持，完全跳过该 SQL。</p>
 */
@Component
public class JdbcContextEmbeddingStore implements ContextEmbeddingStore {

    private static final String UPDATE_SQL = """
            UPDATE harness_context_chunks
               SET embedding = CAST(? AS vector),
                   embedding_model = ?,
                   embedded_at = ?,
                   updated_at = ?
             WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final boolean supported;

    public JdbcContextEmbeddingStore(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.supported = isPostgreSql(dataSource);
    }

    @Override
    public boolean supported() {
        return supported;
    }

    @Override
    public void save(List<ContextEmbeddingUpdate> updates) {
        if (!supported || updates == null || updates.isEmpty()) return;
        Instant now = Instant.now();
        OffsetDateTime databaseNow = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        int[][] counts;
        try {
            counts = jdbcTemplate.batchUpdate(UPDATE_SQL, updates, updates.size(), (statement, update) -> {
                statement.setString(1, vectorLiteral(update.vector()));
                statement.setString(2, update.vector().model());
                // PostgreSQL 无法从 Instant 直接推断 timestamptz 参数类型；显式传 UTC 偏移。
                statement.setObject(3, databaseNow);
                statement.setObject(4, databaseNow);
                statement.setString(5, update.chunk().getId());
                statement.setString(6, update.chunk().getTenantId());
            });
        } catch (RuntimeException exception) {
            throw new ContextEmbeddingStoreException("pgvector 写入失败", exception);
        }
        for (int[] batch : counts) {
            for (int count : batch) {
                if (count != 1) {
                    throw new ContextEmbeddingStoreException("pgvector 写入的 chunk 已不存在或已被删除", null);
                }
            }
        }
    }

    private String vectorLiteral(EmbeddingVector vector) {
        if (vector == null || vector.values().isEmpty()) {
            throw new IllegalArgumentException("embedding 向量不能为空");
        }
        return "[" + vector.values().stream()
                .map(value -> {
                    if (value == null || !Double.isFinite(value)) {
                        throw new IllegalArgumentException("embedding 向量包含非有限数字");
                    }
                    return Double.toString(value);
                })
                .collect(Collectors.joining(",")) + "]";
    }

    private boolean isPostgreSql(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException exception) {
            return false;
        }
    }
}
