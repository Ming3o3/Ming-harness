package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Agent 工作区的资源边界。
 *
 * <p>工作区是代码 Agent 接触用户文件系统的唯一根目录。默认只允许读取较小的文本文件，
 * 写入和执行命令仍需要工具策略与人工审批。</p>
 */
@ConfigurationProperties(prefix = "harness.workspace")
public record WorkspaceProperties(
        boolean enabled,
        String root,
        int maxReadBytes,
        int maxWriteBytes,
        int maxListEntries,
        int maxSearchFiles,
        int maxSearchResults,
        int maxReadLines,
        boolean allowHiddenFiles
) {

    public WorkspaceProperties {
        root = root == null || root.isBlank() ? "./workspace" : root;
        maxReadBytes = positiveOrDefault(maxReadBytes, 1_000_000);
        maxWriteBytes = positiveOrDefault(maxWriteBytes, 1_000_000);
        maxListEntries = positiveOrDefault(maxListEntries, 200);
        maxSearchFiles = positiveOrDefault(maxSearchFiles, 2_000);
        maxSearchResults = positiveOrDefault(maxSearchResults, 200);
        maxReadLines = positiveOrDefault(maxReadLines, 2_000);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value < 1 ? fallback : value;
    }
}
