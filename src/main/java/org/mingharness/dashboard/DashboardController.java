package org.mingharness.dashboard;

import org.mingharness.runtime.application.RunService;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final RunService runService;

    public DashboardController(RunService runService) {
        this.runService = runService;
    }

    @GetMapping("/summary")
    public RunDashboardSummary summary() {
        return runService.summary(HarnessIdentityContext.require().tenantId());
    }
}
