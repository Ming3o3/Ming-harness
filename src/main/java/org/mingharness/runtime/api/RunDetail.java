package org.mingharness.runtime.api;

import java.util.List;

public record RunDetail(RunSummary run, List<StepView> steps) {
}
