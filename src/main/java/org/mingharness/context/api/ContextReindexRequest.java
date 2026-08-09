package org.mingharness.context.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 有界的租户级上下文索引重建请求。
 *
 * <p>重建按父对象和 chunk 分别限流，避免一次管理请求把整个租户的数据全部送入外部
 * embedding 服务。scope 使用固定枚举字符串，避免把任意值带入重建查询分支。</p>
 */
public record ContextReindexRequest(
        @Pattern(regexp = "(?i)ALL|DOCUMENT|MEMORY", message = "重建范围只能是 ALL、DOCUMENT 或 MEMORY")
        String scope,
        @Min(value = 1, message = "父对象数量上限必须至少为 1")
        @Max(value = 500, message = "父对象数量上限不能超过 500")
        Integer parentLimit,
        @Min(value = 1, message = "chunk 数量上限必须至少为 1")
        @Max(value = 5000, message = "chunk 数量上限不能超过 5000")
        Integer chunkLimit,
        Boolean rechunk
) {

    public ContextReindexRequest(String scope, Integer parentLimit, Integer chunkLimit) {
        this(scope, parentLimit, chunkLimit, false);
    }

    public String effectiveScope() {
        return scope == null || scope.isBlank() ? "ALL" : scope.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public int effectiveParentLimit() {
        return parentLimit == null ? 100 : Math.min(500, Math.max(1, parentLimit));
    }

    public int effectiveChunkLimit() {
        return chunkLimit == null ? 1000 : Math.min(5000, Math.max(1, chunkLimit));
    }

    public boolean shouldRechunk() {
        return Boolean.TRUE.equals(rechunk);
    }
}
