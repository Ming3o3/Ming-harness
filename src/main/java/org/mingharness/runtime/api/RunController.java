package org.mingharness.runtime.api;

import jakarta.validation.Valid;
import org.mingharness.runtime.application.RunService;
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
        return runService.list();
    }

    @GetMapping("/{runId}")
    public RunDetail detail(@PathVariable String runId) {
        return runService.getDetail(runId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunSummary create(@Valid @RequestBody CreateRunRequest request) {
        return runService.create(request);
    }

    @PostMapping("/{runId}/start")
    public RunDetail start(@PathVariable String runId) {
        return runService.start(runId);
    }

    @DeleteMapping("/{runId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String runId) {
        runService.cancel(runId);
    }
}
