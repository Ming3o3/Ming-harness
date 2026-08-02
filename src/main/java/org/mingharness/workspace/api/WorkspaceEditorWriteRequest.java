package org.mingharness.workspace.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 编辑器保存请求；已有文件必须携带最近一次读取到的 SHA-256。 */
public record WorkspaceEditorWriteRequest(
        String path,
        @NotNull String content,
        @Size(min = 64, max = 64) String expectedSha256
) {
}
