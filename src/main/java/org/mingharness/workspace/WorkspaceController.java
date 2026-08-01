package org.mingharness.workspace;

import org.mingharness.workspace.api.WorkspaceStatusView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地代码工作区的只读状态接口。 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceStatusService workspaceStatusService;

    public WorkspaceController(WorkspaceStatusService workspaceStatusService) {
        this.workspaceStatusService = workspaceStatusService;
    }

    /**
     * 告知已授权控制台当前是否连接本地项目；不返回绝对路径或任何目录结构。
     */
    @GetMapping
    public WorkspaceStatusView status() {
        return workspaceStatusService.status();
    }
}
