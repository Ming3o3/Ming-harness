package org.mingharness.evaluation;

import jakarta.validation.Valid;
import org.mingharness.evaluation.api.EvaluationReportView;
import org.mingharness.evaluation.api.EvaluationRequest;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public EvaluationReportView run(@Valid @RequestBody EvaluationRequest request) {
        var identity = HarnessIdentityContext.require();
        return evaluationService.run(identity.tenantId(), identity.userId(), request);
    }

    @GetMapping
    public List<EvaluationReportView> list() {
        return evaluationService.list(HarnessIdentityContext.require().tenantId());
    }
}
