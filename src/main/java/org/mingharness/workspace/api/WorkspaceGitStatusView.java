package org.mingharness.workspace.api;

import java.util.List;

/** 供控制台审阅的工作区 Git 变更摘要，仅包含固定只读 Git 命令的结果。 */
public record WorkspaceGitStatusView(
        String branch,
        boolean clean,
        List<WorkspaceGitChangeView> entries,
        int changeCount,
        int protectedChangeCount,
        boolean outputTruncated
) {
}
