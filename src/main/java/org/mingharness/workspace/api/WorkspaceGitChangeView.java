package org.mingharness.workspace.api;

/** Git 状态中的单个变更；路径相对于已授权工作区，绝不携带本机绝对路径。 */
public record WorkspaceGitChangeView(
        String index,
        String worktree,
        String path
) {
}
