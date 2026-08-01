package org.mingharness.workspace;

import jakarta.validation.Valid;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.mingharness.workspace.api.LocalWorkspaceView;
import org.mingharness.workspace.api.RegisterLocalWorkspaceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户自己的桌面本地工作区登记接口。 */
@RestController
@RequestMapping("/api/workspaces")
public class LocalWorkspaceController {

    private final WorkspaceDirectoryService workspaceDirectoryService;

    public LocalWorkspaceController(WorkspaceDirectoryService workspaceDirectoryService) {
        this.workspaceDirectoryService = workspaceDirectoryService;
    }

    @GetMapping
    public List<LocalWorkspaceView> list() {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceDirectoryService.list(identity.tenantId(), identity.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocalWorkspaceView register(
            @RequestHeader(value = "X-Harness-Desktop-Bridge", required = false) String bridgeToken,
            @Valid @RequestBody RegisterLocalWorkspaceRequest request) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        return workspaceDirectoryService.register(identity.tenantId(), identity.userId(),
                request.displayName(), request.rootPath(), bridgeToken);
    }
}
