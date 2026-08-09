package org.mingharness.evaluation;

import jakarta.validation.Valid;
import org.mingharness.evaluation.api.EvaluationCaseView;
import org.mingharness.evaluation.api.SaveEvaluationCaseRequest;
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
@RequestMapping("/api/evaluations/cases")
public class EvaluationCaseController {

    private final EvaluationCaseService caseService;

    public EvaluationCaseController(EvaluationCaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    public List<EvaluationCaseView> list() {
        return caseService.list(HarnessIdentityContext.require().tenantId());
    }

    @PostMapping("/from-run")
    public EvaluationCaseView saveFromRun(@Valid @RequestBody SaveEvaluationCaseRequest request) {
        var identity = HarnessIdentityContext.require();
        return caseService.saveFromRun(identity.tenantId(), identity.userId(), request);
    }

    @DeleteMapping("/{caseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String caseId) {
        var identity = HarnessIdentityContext.require();
        caseService.delete(identity.tenantId(), identity.userId(), caseId);
    }
}
