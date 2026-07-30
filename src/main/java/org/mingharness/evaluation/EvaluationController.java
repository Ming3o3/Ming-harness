package org.mingharness.evaluation;

import jakarta.validation.Valid;
import org.mingharness.evaluation.api.EvaluationReportView;
import org.mingharness.evaluation.api.EvaluationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public EvaluationReportView run(@RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId,
                                    @RequestHeader(name = "X-User-Id", defaultValue = "operator") String userId,
                                    @Valid @RequestBody EvaluationRequest request) {
        return evaluationService.run(tenantId, userId, request);
    }

    @GetMapping
    public List<EvaluationReportView> list(
            @RequestHeader(name = "X-Tenant-Id", defaultValue = "tenant-demo") String tenantId) {
        return evaluationService.list(tenantId);
    }
}
