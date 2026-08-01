package org.mingharness.workspace.api;

/** 文件浏览器的只读文本预览；疑似凭证会脱敏，文件哈希仍反映磁盘上的原始内容。 */
public record WorkspaceFileContentView(
        String path,
        String content,
        String sha256,
        int totalLines,
        boolean truncated,
        boolean redacted
) {
}
