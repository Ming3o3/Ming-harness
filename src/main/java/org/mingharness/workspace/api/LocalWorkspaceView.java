package org.mingharness.workspace.api;

import java.time.Instant;

/** 前端可见的工作区摘要；不包含本机根目录或路径密文。 */
public record LocalWorkspaceView(
        String id,
        String displayName,
        boolean accessible,
        boolean gitRepository,
        Instant createdAt,
        Instant updatedAt
) {
}
