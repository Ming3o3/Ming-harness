package org.mingharness.runtime.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.application.RunService;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService runService;

    public RunController(RunService runService) {
        this.runService = runService;
    }

    @GetMapping
    public List<RunSummary> list() {
        return runService.list(identity().tenantId());
    }

    @GetMapping("/{runId}")
    public RunDetail detail(@PathVariable String runId) {
        return runService.getDetail(runId, identity().tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunSummary create(@Valid @RequestBody CreateRunRequest request,
                             HttpServletRequest httpRequest) {
        HarnessIdentity identity = identity();
        String tenantId = identity.tenantId();
        String idempotencyKey = httpRequest.getHeader("Idempotency-Key");
        String permissions = httpRequest.getHeader("X-Permissions");
        if (identity.isApiKey()) {
            permissions = identity.permissionsCsv();
        }
        if (!tenantId.equals(request.tenantId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "请求租户与当前租户不一致");
        }
        if (!identity.userId().equals(request.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "USER_ACCESS_DENIED", "请求用户与当前认证用户不一致");
        }
        String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? request.idempotencyKey() : idempotencyKey;
        String effectivePermissions = identity.isApiKey()
                ? identity.permissionsCsv()
                : permissions == null || permissions.isBlank() ? request.permissions() : permissions;
        return runService.create(request.withIdempotencyKey(effectiveKey).withPermissions(effectivePermissions));
    }

    @PostMapping("/{runId}/start")
    public RunDetail start(@PathVariable String runId) {
        return runService.start(runId, identity().tenantId());
    }

    @PostMapping("/{runId}/approve")
    public RunDetail approve(@PathVariable String runId) {
        return runService.approve(runId, identity().tenantId(), identity().userId());
    }

    @PostMapping("/{runId}/reject")
    public RunDetail reject(@PathVariable String runId,
                            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return runService.reject(runId, identity().tenantId(), request == null ? null : request.reason(), identity().userId());
    }

    @PostMapping("/{runId}/retry")
    public RunDetail retry(@PathVariable String runId) {
        return runService.retry(runId, identity().tenantId());
    }

    @DeleteMapping("/{runId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String runId) {
        runService.cancel(runId, identity().tenantId());
    }

    private HarnessIdentity identity() {
        return HarnessIdentityContext.require();
    }
}
