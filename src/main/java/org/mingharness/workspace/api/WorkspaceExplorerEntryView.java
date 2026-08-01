package org.mingharness.workspace.api;

/** 工作区文件浏览器的单个相对路径条目，绝不包含主机绝对路径。 */
public record WorkspaceExplorerEntryView(
        String path,
        String name,
        boolean directory,
        Long size
) {
}
