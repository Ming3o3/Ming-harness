package org.mingharness.workspace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 仅由 Electron/Tauri 等原生目录选择器提交，不为普通浏览器提供路径发现能力。 */
public record RegisterLocalWorkspaceRequest(
        @Size(max = 255, message = "工作区名称长度不能超过 255 个字符") String displayName,
        @NotBlank(message = "工作区目录不能为空")
        @Size(max = 2048, message = "工作区目录长度不能超过 2048 个字符") String rootPath
) {
}
