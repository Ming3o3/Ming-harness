package org.mingharness.runtime.api;

import jakarta.validation.Valid;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.application.RunService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public List<RunSummary> list(@RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId) {
        return runService.list(tenantId);
    }

    @GetMapping("/{runId}")
    public RunDetail detail(@PathVariable String runId,
                            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId) {
        return runService.getDetail(runId, tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunSummary create(@Valid @RequestBody CreateRunRequest request,
                             @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
                             @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                             @RequestHeader(name = "X-Permissions", required = false) String permissions) {
        if (!tenantId.equals(request.tenantId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "请求租户与当前租户不一致");
        }
        String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? request.idempotencyKey() : idempotencyKey;
        String effectivePermissions = permissions == null || permissions.isBlank()
                ? request.permissions() : permissions;
        return runService.create(request.withIdempotencyKey(effectiveKey).withPermissions(effectivePermissions));
    }

    @PostMapping("/{runId}/start")
    public RunDetail start(@PathVariable String runId,
                           @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId) {
        return runService.start(runId, tenantId);
    }

    @PostMapping("/{runId}/approve")
    public RunDetail approve(@PathVariable String runId,
                             @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId) {
        return runService.approve(runId, tenantId);
    }

    @PostMapping("/{runId}/reject")
    public RunDetail reject(@PathVariable String runId,
                            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
                            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return runService.reject(runId, tenantId, request == null ? null : request.reason());
    }

    @PostMapping("/{runId}/retry")
    public RunDetail retry(@PathVariable String runId,
                           @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId) {
        return runService.retry(runId, tenantId);
    }

    @DeleteMapping("/{runId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String runId,
                       @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId) {
        runService.cancel(runId, tenantId);
    }
}
