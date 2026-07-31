package org.mingharness.audit;

import org.mingharness.runtime.application.RunService;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/runs/{runId}/audit-events")
public class AuditController {

    private final AuditEventRepository auditEventRepository;
    private final AuditTrailService auditTrailService;
    private final RunService runService;

    public AuditController(AuditEventRepository auditEventRepository,
                           AuditTrailService auditTrailService,
                           RunService runService) {
        this.auditEventRepository = auditEventRepository;
        this.auditTrailService = auditTrailService;
        this.runService = runService;
    }

    @GetMapping
    public List<AuditEvent> list(@PathVariable String runId) {
        runService.assertTenant(runId, HarnessIdentityContext.require().tenantId());
        return auditEventRepository.findTop100ByRunIdOrderByCreatedAtDesc(runId);
    }

    /** 供运维或审计人员主动校验指定 Run 的事件链完整性。 */
    @GetMapping("/verify")
    public AuditIntegrityVerification verify(@PathVariable String runId) {
        runService.assertTenant(runId, HarnessIdentityContext.require().tenantId());
        return auditTrailService.verify(runId);
    }
}
