package org.mingharness.feedback;

import jakarta.validation.Valid;
import org.mingharness.feedback.api.RunFeedbackRequest;
import org.mingharness.feedback.api.RunFeedbackView;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs/{runId}/feedback")
public class RunFeedbackController {

    private final RunFeedbackService feedbackService;

    public RunFeedbackController(RunFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public RunFeedbackView save(@PathVariable String runId, @Valid @RequestBody RunFeedbackRequest request) {
        var identity = HarnessIdentityContext.require();
        return feedbackService.save(runId, identity.tenantId(), identity.userId(), request);
    }

    @GetMapping
    public RunFeedbackView get(@PathVariable String runId) {
        var identity = HarnessIdentityContext.require();
        return feedbackService.get(runId, identity.tenantId(), identity.userId());
    }
}
