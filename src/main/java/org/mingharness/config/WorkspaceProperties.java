package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.ArrayList;
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
        boolean allowHiddenFiles,
        boolean execEnabled,
        List<String> allowedCommands,
        int maxCommandTimeoutMs,
        int maxCommandOutputBytes,
        int maxCommandArgs,
        int maxSearchMatchChars,
        int maxToolOutputChars
) {

    /** 兼容工作区文件工具阶段的旧构造方式。 */
    public WorkspaceProperties(boolean enabled, String root, int maxReadBytes, int maxWriteBytes,
                               int maxListEntries, int maxSearchFiles, int maxSearchResults,
                               int maxReadLines, boolean allowHiddenFiles) {
        this(enabled, root, maxReadBytes, maxWriteBytes, maxListEntries, maxSearchFiles,
                maxSearchResults, maxReadLines, allowHiddenFiles, false, List.of(),
                120_000, 200_000, 32, 512, 4_000);
    }

    /** 兼容执行工具阶段的旧构造方式。 */
    public WorkspaceProperties(boolean enabled, String root, int maxReadBytes, int maxWriteBytes,
                               int maxListEntries, int maxSearchFiles, int maxSearchResults,
                               int maxReadLines, boolean allowHiddenFiles, boolean execEnabled,
                               List<String> allowedCommands, int maxCommandTimeoutMs,
                               int maxCommandOutputBytes, int maxCommandArgs) {
        this(enabled, root, maxReadBytes, maxWriteBytes, maxListEntries, maxSearchFiles,
                maxSearchResults, maxReadLines, allowHiddenFiles, execEnabled, allowedCommands,
                maxCommandTimeoutMs, maxCommandOutputBytes, maxCommandArgs, 512, 4_000);
    }

    @ConstructorBinding
    public WorkspaceProperties {
        root = root == null || root.isBlank() ? "./workspace" : root;
        maxReadBytes = positiveOrDefault(maxReadBytes, 1_000_000);
        maxWriteBytes = positiveOrDefault(maxWriteBytes, 1_000_000);
        maxListEntries = positiveOrDefault(maxListEntries, 200);
        maxSearchFiles = positiveOrDefault(maxSearchFiles, 2_000);
        maxSearchResults = positiveOrDefault(maxSearchResults, 200);
        maxReadLines = positiveOrDefault(maxReadLines, 2_000);
        List<String> normalizedCommands = new ArrayList<>();
        if (allowedCommands != null) {
            allowedCommands.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .forEach(normalizedCommands::add);
        }
        allowedCommands = List.copyOf(normalizedCommands);
        maxCommandTimeoutMs = boundedOrDefault(maxCommandTimeoutMs, 120_000, 1_000, 600_000);
        maxCommandOutputBytes = boundedOrDefault(maxCommandOutputBytes, 200_000, 1_024, 5_000_000);
        maxCommandArgs = boundedOrDefault(maxCommandArgs, 32, 1, 128);
        maxSearchMatchChars = positiveOrDefault(maxSearchMatchChars, 512);
        maxToolOutputChars = boundedOrDefault(maxToolOutputChars, 4_000, 256, 1_000_000);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value < 1 ? fallback : value;
    }

    private static int boundedOrDefault(int value, int fallback, int min, int max) {
        if (value < min) return fallback;
        return Math.min(value, max);
    }
}
