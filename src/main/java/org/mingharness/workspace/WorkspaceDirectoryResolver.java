package org.mingharness.workspace;

import org.mingharness.tool.ToolExecutionContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** 将 Run 绑定的工作区解析为本次工具调用专属的根目录。 */
@Component
public class WorkspaceDirectoryResolver {

    private final WorkspaceDirectoryService workspaceDirectoryService;

    public WorkspaceDirectoryResolver(WorkspaceDirectoryService workspaceDirectoryService) {
        this.workspaceDirectoryService = workspaceDirectoryService;
    }

    public Path resolve(ToolExecutionContext context) {
        if (context == null) return workspaceDirectoryService.configuredRoot();
        return workspaceDirectoryService.requireRoot(context.workspaceId(), context.tenantId(), context.userId());
    }
}
