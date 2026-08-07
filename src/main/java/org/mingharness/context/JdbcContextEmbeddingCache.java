package org.mingharness.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PostgreSQL/pgvector 持久化 embedding 缓存。
 *
 * <p>缓存故障会退化为正常的 embedding API 调用，不影响正文保存或向量索引正确性。</p>
 */
@Component
public class JdbcContextEmbeddingCache implements ContextEmbeddingCache {

    private static final Logger log = LoggerFactory.getLogger(JdbcContextEmbeddingCache.class);

    private static final String FIND_SQL = """
            SELECT embedding_model, embedding::text
              FROM harness_context_embedding_cache
             WHERE tenant_id = ?
               AND content_hash = ?
               AND request_model = ?
               AND model_version = ?
               AND embedding_dimension = ?
            """;

    private static final String SAVE_SQL = """
            INSERT INTO harness_context_embedding_cache
                (tenant_id, content_hash, request_model, model_version, embedding_model, embedding_dimension,
                 embedding, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?)
            ON CONFLICT (tenant_id, content_hash, request_model, model_version, embedding_dimension)
            DO UPDATE SET embedding_model = EXCLUDED.embedding_model,
                          embedding = EXCLUDED.embedding,
                          updated_at = EXCLUDED.updated_at
            """;

    private static final String DELETE_SQL = """
            DELETE FROM harness_context_embedding_cache
             WHERE updated_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final boolean supported;

    public JdbcContextEmbeddingCache(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.supported = isPostgreSql(dataSource);
    }

    @Override
    public Optional<EmbeddingVector> find(String tenantId, String contentHash,
                                          String requestModel, String modelVersion, int dimension) {
        if (!supported || blank(tenantId) || blank(contentHash) || blank(requestModel)
                || blank(modelVersion) || dimension <= 0) {
            return Optional.empty();
        }
        try {
            List<EmbeddingVector> values = jdbcTemplate.query(FIND_SQL,
                    (resultSet, rowNumber) -> new EmbeddingVector(
                            resultSet.getString(1), parseVector(resultSet.getString(2), dimension)),
                    tenantId, contentHash, requestModel, modelVersion, dimension);
            return values.stream().findFirst();
        } catch (DataAccessException | IllegalArgumentException exception) {
            log.warn("读取 embedding 缓存失败，继续调用供应商，message={}", exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(String tenantId, String contentHash, String requestModel,
                     String modelVersion, EmbeddingVector vector) {
        if (!supported || blank(tenantId) || blank(contentHash) || blank(requestModel)
                || blank(modelVersion) || vector == null || vector.values().isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            OffsetDateTime databaseNow = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
            jdbcTemplate.update(SAVE_SQL, tenantId, contentHash, requestModel, modelVersion,
                    blank(vector.model()) ? requestModel : vector.model(), vector.dimension(),
                    vectorLiteral(vector), databaseNow, databaseNow);
        } catch (DataAccessException | IllegalArgumentException exception) {
            log.warn("写入 embedding 缓存失败，继续使用已写入的 chunk 向量，message={}",
                    exception.getMessage());
        }
    }

    @Override
    public int deleteUpdatedBefore(Instant cutoff) {
        if (!supported || cutoff == null) return 0;
        try {
            return jdbcTemplate.update(DELETE_SQL,
                    OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
        } catch (DataAccessException exception) {
            log.warn("清理 embedding 缓存失败，message={}", exception.getMessage());
            return 0;
        }
    }

    private List<Double> parseVector(String literal, int expectedDimension) {
        if (literal == null) throw new IllegalArgumentException("embedding 缓存向量为空");
        String value = literal.trim();
        if (value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException("embedding 缓存向量格式非法");
        }
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) throw new IllegalArgumentException("embedding 缓存向量为空");
        String[] parts = body.split(",", -1);
        if (parts.length != expectedDimension) {
            throw new IllegalArgumentException("embedding 缓存向量维度不匹配");
        }
        List<Double> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            double number = Double.parseDouble(part.trim());
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("embedding 缓存向量包含非有限数字");
            }
            result.add(number);
        }
        return List.copyOf(result);
    }

    private String vectorLiteral(EmbeddingVector vector) {
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
