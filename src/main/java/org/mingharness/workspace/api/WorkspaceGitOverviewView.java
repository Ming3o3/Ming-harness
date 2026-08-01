package org.mingharness.workspace.api;

/** 文件浏览器展示的只读 Git 摘要；Git 不可用时不阻断普通目录浏览。 */
public record WorkspaceGitOverviewView(
        boolean available,
        String branch,
        boolean clean,
        int changeCount,
        boolean outputTruncated
) {

    public static WorkspaceGitOverviewView unavailable() {
        return new WorkspaceGitOverviewView(false, "—", true, 0, false);
    }
}
