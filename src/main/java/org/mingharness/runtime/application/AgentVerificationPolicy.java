package org.mingharness.runtime.application;

import org.mingharness.runtime.domain.StepStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 保证 Agent 使用工作区修改工具后留下可追溯的实际变更核验。 */
final class AgentVerificationPolicy {

    private static final Set<String> MUTATING_TOOLS = Set.of("workspace.edit", "workspace.write");
    private static final Set<String> VERIFICATION_TOOLS = Set.of("workspace.read", "workspace.git.diff");

    private AgentVerificationPolicy() {
    }

    static Optional<String> missingVerification(List<StepEvidence> steps) {
        if (steps == null || steps.isEmpty()) return Optional.empty();
        int latestMutation = steps.stream()
                .filter(step -> step.status() == StepStatus.SUCCEEDED)
                .filter(step -> MUTATING_TOOLS.contains(step.name()))
                .mapToInt(StepEvidence::sequence)
                .max()
                .orElse(-1);
        if (latestMutation < 0) return Optional.empty();
        boolean verified = steps.stream()
                .filter(step -> step.status() == StepStatus.SUCCEEDED)
                .anyMatch(step -> step.sequence() > latestMutation
                        && VERIFICATION_TOOLS.contains(step.name()));
        return verified ? Optional.empty()
                : Optional.of("Agent 修改工作区后必须重新读取文件或使用 workspace.git.diff 核对实际变更");
    }

    record StepEvidence(int sequence, String name, StepStatus status) {
    }
}
