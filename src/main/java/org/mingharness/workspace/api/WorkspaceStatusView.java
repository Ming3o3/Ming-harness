package org.mingharness.workspace.api;

/**
 * 返回给控制台的本地工作区摘要。
 *
 * <p>该视图有意不包含绝对路径。绝对路径只保留在本机 Spring Boot 进程中，前端和模型
 * 均通过工作区内的相对路径进行操作。</p>
 */
public record WorkspaceStatusView(
        boolean enabled,
        boolean accessible,
        String displayName,
        boolean gitRepository,
        boolean commandExecutionEnabled,
        int allowedCommandCount,
        boolean writeRequiresApproval,
        boolean absolutePathHidden
) {

    public static WorkspaceStatusView disabled() {
        return new WorkspaceStatusView(false, false, "未连接本地工作区", false,
                false, 0, true, true);
    }
}
