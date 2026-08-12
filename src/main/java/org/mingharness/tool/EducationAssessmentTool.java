package org.mingharness.tool;

import org.mingharness.education.EducationAssessmentService;
import org.mingharness.education.AssessmentAttempt;
import org.mingharness.common.BusinessException;
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
                        // learnerEvidenceQuote 缺失也要作为可恢复结果交回模型，不能在
                        // schema 层直接终止整个教育 Run。
                        "required", List.of("conceptKey", "correct"),
                        "additionalProperties", false,
                        "properties", Map.of(
                                "conceptKey", Map.of("type", "string", "minLength", 1, "maxLength", 255),
                                "correct", Map.of("type", "boolean"),
                                "observedMastery", Map.of("type", "number", "minimum", 0, "maximum", 1),
                                "evidenceText", Map.of("type", "string", "maxLength", 4000),
                                "learnerEvidenceQuote", Map.of("type", "string", "maxLength", 2000),
                                "feedback", Map.of("type", "string", "maxLength", 1000)
                        )
                ),
                Set.of("education.write"),
                30_000,
                1,
                "DENY_EXTERNAL",
                Map.of(
                        "type", "object",
                        // 形成性证据校验失败是可恢复的业务结果：先把错误交回模型，
                        // 允许它修正原话引用，而不是让整个教育 Run 直接失败。
                        "required", List.of("ok"),
                        "properties", Map.of(
                                "ok", Map.of("type", "boolean"),
                                "recoverable", Map.of("type", "boolean"),
                                "code", Map.of("type", "string"),
                                "message", Map.of("type", "string"),
                                "attemptId", Map.of("type", "string"),
                                "conceptKey", Map.of("type", "string"),
                                "masteryBefore", Map.of("type", "number", "minimum", 0, "maximum", 1),
                                "masteryScore", Map.of("type", "number", "minimum", 0, "maximum", 1),
                                "evidenceSource", Map.of("type", "string"),
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
        String conceptKey = "";
        try {
            JsonNode request = objectMapper.reader().readTree(input);
            conceptKey = request == null || request.get("conceptKey") == null
                    ? "" : request.get("conceptKey").asText("").trim();
            if (conceptKey.isBlank() || request.get("correct") == null || !request.get("correct").isBoolean()) {
                throw new IllegalArgumentException("形成性评价必须包含 conceptKey 和 boolean correct");
            }
            String evidenceText = request.get("evidenceText") == null
                    ? "" : request.get("evidenceText").asText("").trim();
            if (evidenceText.isBlank()) {
                return recoverableEvidenceResult(
                        "ASSESSMENT_EVIDENCE_REQUIRED",
                        "形成性评价必须包含学生作答或推理依据 evidenceText", conceptKey);
            }
            String learnerEvidenceQuote = request.get("learnerEvidenceQuote") == null
                    ? "" : request.get("learnerEvidenceQuote").asText("").trim();
            if (learnerEvidenceQuote.isBlank()) {
                return recoverableEvidenceResult(
                        "ASSESSMENT_LEARNER_EVIDENCE_QUOTE_REQUIRED",
                        "模型评价必须逐字引用本轮学习者的作答或推理原话", conceptKey);
            }
            boolean correct = request.get("correct").asBoolean();
            double observedMastery = request.get("observedMastery") == null
                    ? (correct ? 1.0 : 0.0) : request.get("observedMastery").asDouble();
            AssessmentAttempt attempt = assessmentService.record(
                    context.tenantId(), context.userId(), context.runId(), context.stepId(),
                    context.educationLearnerProfileId(), conceptKey, correct, observedMastery,
                    "MODEL_TOOL", evidenceText, learnerEvidenceQuote,
                    request.get("feedback") == null ? null : request.get("feedback").asText(""));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("attemptId", attempt.getId());
            result.put("conceptKey", attempt.getConceptKey());
            result.put("masteryBefore", attempt.getMasteryBefore());
            result.put("masteryScore", attempt.getMasteryAfter());
            result.put("evidenceSource", attempt.getEvidenceSource());
            result.put("feedback", attempt.getFeedback());
            return objectMapper.writeValueAsString(result);
        } catch (BusinessException exception) {
            if (!isRecoverableEvidenceError(exception)) {
                throw exception;
            }
            // 这是模型参数与本轮输入不一致，而不是基础设施故障或权限故障。
            // 以结构化工具结果返回，下一轮模型可以修正 learnerEvidenceQuote；
            // 若仍无法补齐，Run 也能正常返回教学回答，后续由人工复核补证据。
            return recoverableEvidenceResult(exception.getCode(), exception.getMessage(), conceptKey);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("形成性评价输入不是有效 JSON", exception);
        }
    }

    private boolean isRecoverableEvidenceError(BusinessException exception) {
        return Set.of(
                "ASSESSMENT_LEARNER_EVIDENCE_QUOTE_MISMATCH",
                "ASSESSMENT_LEARNER_EVIDENCE_QUOTE_REQUIRED",
                "ASSESSMENT_EVIDENCE_REQUIRED"
        ).contains(exception.getCode());
    }

    private String recoverableEvidenceResult(String code, String message, String conceptKey) {
        try {
            Map<String, Object> recoverable = new LinkedHashMap<>();
            recoverable.put("ok", false);
            recoverable.put("recoverable", true);
            recoverable.put("code", code);
            recoverable.put("message", message);
            recoverable.put("conceptKey", conceptKey);
            return objectMapper.writeValueAsString(recoverable);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化可恢复的形成性评价结果", exception);
        }
    }
}
