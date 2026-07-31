package org.mingharness.runtime.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

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

    /** 新增分页接口，不改变原有 GET /api/runs 的数组返回结构。 */
    @GetMapping("/page")
    public RunPage page(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestParam(required = false) String status) {
        return runService.listPage(identity().tenantId(), page, size, parseStatus(status));
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
        if (identity.usesTrustedPermissions()) {
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
        String effectivePermissions = identity.usesTrustedPermissions()
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

    private RunStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return RunStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_RUN_STATUS", "不支持的 Run 状态: " + rawStatus);
        }
    }
}
