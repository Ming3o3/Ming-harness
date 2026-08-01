package org.mingharness.workspace.api;

import java.util.List;

/** 当前目录的受控浏览结果，所有 path 字段均相对于已授权工作区根目录。 */
public record WorkspaceExplorerView(
        String path,
        String parentPath,
        List<WorkspaceExplorerEntryView> entries,
        WorkspaceGitOverviewView git
) {
}
