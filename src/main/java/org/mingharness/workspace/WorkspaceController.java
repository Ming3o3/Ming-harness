package org.mingharness.workspace;

import jakarta.validation.Valid;
import org.mingharness.workspace.api.WorkspaceStatusView;
import org.mingharness.workspace.api.WorkspaceExplorerView;
import org.mingharness.workspace.api.WorkspaceFileContentView;
import org.mingharness.workspace.api.WorkspaceGitDiffView;
import org.mingharness.workspace.api.WorkspaceGitStatusView;
import org.mingharness.workspace.api.WorkspaceEditorWriteRequest;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 只读项目浏览器：workspaceId 来自当前会话，服务端会再次校验组织和用户归属。 */
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

    /** 编辑器读取未脱敏正文；仅允许拥有 workspace.write 权限的用户使用。 */
    @GetMapping("/files/editor-content")
    public WorkspaceFileContentView editorContent(@RequestParam(required = false) String workspaceId,
                                                  @RequestParam String path) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceExplorerService.readForEditor(workspaceId, identity.tenantId(), identity.userId(), path);
    }

    /** 编辑器保存通过原子写入和 expectedSha256 防止覆盖并发修改。 */
    @PutMapping("/files/editor-content")
    public WorkspaceFileContentView saveEditorContent(@RequestParam(required = false) String workspaceId,
                                                      @Valid @RequestBody WorkspaceEditorWriteRequest request) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceExplorerService.writeForEditor(workspaceId, identity.tenantId(), identity.userId(),
                request.path(), request.content(), request.expectedSha256());
    }

    /** 返回受当前身份和工作区范围限制的 Git 变更列表。 */
    @GetMapping("/git/status")
    public WorkspaceGitStatusView gitStatus(@RequestParam(required = false) String workspaceId) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceExplorerService.gitStatus(workspaceId, identity.tenantId(), identity.userId());
    }

    /** 返回单个相对路径的只读 Diff；不接收任意 Git 参数或绝对路径。 */
    @GetMapping("/git/diff")
    public WorkspaceGitDiffView gitDiff(@RequestParam(required = false) String workspaceId,
                                        @RequestParam(defaultValue = ".") String path,
                                        @RequestParam(defaultValue = "false") boolean staged,
                                        @RequestParam(defaultValue = "3") int contextLines) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceExplorerService.gitDiff(workspaceId, identity.tenantId(), identity.userId(),
                path, staged, contextLines);
    }
}
