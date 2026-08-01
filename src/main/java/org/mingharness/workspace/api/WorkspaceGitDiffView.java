package org.mingharness.workspace.api;

/** 受工作区边界、大小上限和凭证脱敏保护的 Git 差异预览。 */
public record WorkspaceGitDiffView(
        String path,
        boolean staged,
        int contextLines,
        boolean hasChanges,
        boolean outputTruncated,
        int outputBytes,
        String diff
) {
}
