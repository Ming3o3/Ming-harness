package org.mingharness.workspace;

import org.mingharness.workspace.api.WorkspaceStatusView;
import org.mingharness.workspace.api.WorkspaceExplorerView;
import org.mingharness.workspace.api.WorkspaceFileContentView;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地代码工作区的只读状态接口。 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceStatusService workspaceStatusService;
    private final WorkspaceExplorerService workspaceExplorerService;

    public WorkspaceController(WorkspaceStatusService workspaceStatusService,
                               WorkspaceExplorerService workspaceExplorerService) {
        this.workspaceStatusService = workspaceStatusService;
        this.workspaceExplorerService = workspaceExplorerService;
    }

    /**
     * 告知已授权控制台当前是否连接本地项目；不返回绝对路径或任何目录结构。
     */
    @GetMapping
    public WorkspaceStatusView status() {
        return workspaceStatusService.status();
    }

    /** 只读项目浏览器：workspaceId 来自当前会话，服务端会再次校验租户和用户归属。 */
    @GetMapping("/files")
    public WorkspaceExplorerView files(@RequestParam(required = false) String workspaceId,
                                       @RequestParam(defaultValue = ".") String path) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceExplorerService.browse(workspaceId, identity.tenantId(), identity.userId(), path);
    }

    /** 返回受大小、行数和凭证脱敏限制的 UTF-8 文件预览，不提供任意写入能力。 */
    @GetMapping("/files/content")
    public WorkspaceFileContentView content(@RequestParam(required = false) String workspaceId,
                                            @RequestParam String path) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceExplorerService.read(workspaceId, identity.tenantId(), identity.userId(), path);
    }
}
