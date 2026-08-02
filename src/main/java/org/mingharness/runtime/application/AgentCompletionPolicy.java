package org.mingharness.runtime.application;

import org.mingharness.model.AgentTurnCodec;
import org.mingharness.runtime.domain.Step;
import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.domain.StepType;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Agent 只有在最后一轮模型明确结束且没有未完成 Tool Call 时才能成功。 */
final class AgentCompletionPolicy {

    private AgentCompletionPolicy() {
    }

    static Optional<String> missingFinalModel(List<Step> steps, AgentTurnCodec codec) {
        if (steps == null || steps.isEmpty()) {
            return Optional.of("Agent 没有产生最终模型结果");
        }
        Step latest = steps.stream().max(Comparator.comparingInt(Step::getSequence)).orElse(null);
        if (latest == null || latest.getStatus() != StepStatus.SUCCEEDED
                || latest.getType() != StepType.MODEL) {
            if (latest != null && latest.getStatus() == StepStatus.REJECTED) {
                return Optional.of("Agent 收到人工拒绝后没有生成新的模型轮次，请提高最大轮数后重新提交任务");
            }
            return Optional.of("Agent 在工具步骤后没有生成最终模型结果");
        }
        if (!codec.decode(latest.getOutput()).toolCalls().isEmpty()) {
            return Optional.of("Agent 的工具调用尚未完成，不能结束任务");
        }
        return Optional.empty();
    }
}
