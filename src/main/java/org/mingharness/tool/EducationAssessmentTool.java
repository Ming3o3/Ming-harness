package org.mingharness.tool;

import org.mingharness.education.EducationAssessmentService;
import org.mingharness.education.AssessmentAttempt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教育 Agent 的形成性评价工具。
 * <p>只有教育模式会向模型暴露该工具；它把小测结果写入冻结的学习者画像，形成
 * “检索—讲解—测验—状态更新”的闭环。</p>
 */
@Component
public class EducationAssessmentTool implements HarnessTool {

    private final EducationAssessmentService assessmentService;
    private final ObjectMapper objectMapper;

    public EducationAssessmentTool(EducationAssessmentService assessmentService, ObjectMapper objectMapper) {
        this.assessmentService = assessmentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "education.record_assessment",
                "记录学生对一个知识点的形成性评价结果并更新掌握度",
                false,
                "LOW",
                false,
                Map.of(
                        "type", "object",
                        "required", List.of("conceptKey", "correct"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "conceptKey", Map.of("type", "string", "minLength", 1, "maxLength", 255),
                                "correct", Map.of("type", "boolean"),
                                "observedMastery", Map.of("type", "number", "minimum", 0, "maximum", 1),
                                "feedback", Map.of("type", "string", "maxLength", 1000)
                        )
                ),
                Set.of("education.write"),
                30_000,
                1,
                "DENY_EXTERNAL",
                Map.of(
                        "type", "object",
                        "required", List.of("ok", "conceptKey", "masteryScore"),
                        "properties", Map.of(
                                "ok", Map.of("type", "boolean"),
                                "attemptId", Map.of("type", "string"),
                                "conceptKey", Map.of("type", "string"),
                                "masteryBefore", Map.of("type", "number", "minimum", 0, "maximum", 1),
                                "masteryScore", Map.of("type", "number", "minimum", 0, "maximum", 1),
                                "feedback", Map.of("type", "string", "maxLength", 1000)
                        )
                )
        );
    }

    @Override
    public String execute(String input) {
        throw new IllegalStateException("教育评价工具必须在 Run 上下文中执行");
    }

    @Override
    public String execute(String input, ToolExecutionContext context) {
        if (context == null || context.educationLearnerProfileId() == null
                || context.educationLearnerProfileId().isBlank()) {
            throw new IllegalStateException("教育评价工具缺少冻结的学习者画像");
        }
        try {
            JsonNode request = objectMapper.reader().readTree(input);
            String conceptKey = request == null || request.get("conceptKey") == null
                    ? "" : request.get("conceptKey").asText("").trim();
            if (conceptKey.isBlank() || request.get("correct") == null || !request.get("correct").isBoolean()) {
                throw new IllegalArgumentException("形成性评价必须包含 conceptKey 和 boolean correct");
            }
            boolean correct = request.get("correct").asBoolean();
            double observedMastery = request.get("observedMastery") == null
                    ? (correct ? 1.0 : 0.0) : request.get("observedMastery").asDouble();
            AssessmentAttempt attempt = assessmentService.record(
                    context.tenantId(), context.userId(), context.runId(), context.stepId(),
                    context.educationLearnerProfileId(), conceptKey, correct, observedMastery,
                    request.get("feedback") == null ? null : request.get("feedback").asText(""));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("attemptId", attempt.getId());
            result.put("conceptKey", attempt.getConceptKey());
            result.put("masteryBefore", attempt.getMasteryBefore());
            result.put("masteryScore", attempt.getMasteryAfter());
            result.put("feedback", attempt.getFeedback());
            return objectMapper.writeValueAsString(result);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("形成性评价输入不是有效 JSON", exception);
        }
    }
}
