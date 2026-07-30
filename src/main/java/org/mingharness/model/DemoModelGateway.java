package org.mingharness.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(prefix = "harness.model", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DemoModelGateway implements ModelGateway {

    @Override
    public ModelResponse complete(ModelRequest request) {
        String input = request.input() == null ? "" : request.input().trim();
        String content = input.isBlank()
                ? "演示模型已收到任务，但没有可处理的输入。"
                : "演示模型分析结果：" + input;
        return new ModelResponse(
                content,
                request.model() == null || request.model().isBlank() ? "demo-model" : request.model(),
                request.promptVersion() == null || request.promptVersion().isBlank() ? "prompt-v1" : request.promptVersion(),
                estimateTokens(input),
                estimateTokens(content),
                BigDecimal.valueOf(estimateTokens(input) + estimateTokens(content))
                        .multiply(BigDecimal.valueOf(0.000001))
        );
    }

    private int estimateTokens(String value) {
        return Math.max(1, value.length() / 4);
    }
}
