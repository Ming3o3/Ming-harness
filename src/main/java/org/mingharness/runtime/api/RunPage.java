package org.mingharness.runtime.api;

import java.util.List;

/** Run 分页结果，保留总量和下一页信息，方便控制台和外部客户端增量加载。 */
public record RunPage(
        List<RunSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
