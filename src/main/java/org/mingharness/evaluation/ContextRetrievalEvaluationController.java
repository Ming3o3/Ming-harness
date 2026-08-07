package org.mingharness.evaluation;

import jakarta.validation.Valid;
import org.mingharness.evaluation.api.ContextRetrievalEvaluationRequest;
import org.mingharness.evaluation.api.ContextRetrievalEvaluationView;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 检索质量离线评测接口，与生成模型回放评测分开。 */
@RestController
@RequestMapping("/api/evaluations/retrieval")
public class ContextRetrievalEvaluationController {

    private final ContextRetrievalEvaluationService evaluationService;

    public ContextRetrievalEvaluationController(ContextRetrievalEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public ContextRetrievalEvaluationView run(
            @Valid @RequestBody ContextRetrievalEvaluationRequest request) {
        var identity = HarnessIdentityContext.require();
        return evaluationService.run(identity.tenantId(), identity.userId(), request);
    }

    @GetMapping
    public List<ContextRetrievalEvaluationView> list() {
        return evaluationService.list(HarnessIdentityContext.require().tenantId());
    }
}
