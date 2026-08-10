package org.mingharness.education;

import org.mingharness.education.api.AssessmentEvidenceReference;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.domain.Step;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** 从 Run 的上下文快照提取不含正文的课程来源引用，写入测评事实。 */
public final class EducationRetrievalEvidence {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EducationRetrievalEvidence() {
    }

    public static String snapshot(Run run) {
        if (run == null || run.getSteps() == null || run.getSteps().isEmpty()) return "[]";
        LinkedHashMap<String, AssessmentEvidenceReference> references = new LinkedHashMap<>();
        for (Step step : run.getSteps()) {
            if (step == null || step.getContextEvidenceJson() == null
                    || step.getContextEvidenceJson().isBlank()) continue;
            collect(step.getContextEvidenceJson(), references);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(references.values());
        } catch (JacksonException exception) {
            return "[]";
        }
    }

    public static List<AssessmentEvidenceReference> decode(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isArray()) return List.of();
            List<AssessmentEvidenceReference> result = new ArrayList<>();
            for (JsonNode item : root) {
                if (item == null || !item.isObject()) continue;
                String documentId = text(item, "documentId");
                String title = text(item, "title");
                String citation = text(item, "citation");
                if (documentId.isBlank() && citation.isBlank()) continue;
                result.add(new AssessmentEvidenceReference(documentId, title, citation));
            }
            return List.copyOf(result);
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private static void collect(String raw, LinkedHashMap<String, AssessmentEvidenceReference> references) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isArray()) return;
            for (JsonNode item : root) {
                if (item == null || !item.isObject()) continue;
                String documentId = text(item, "documentId");
                String title = text(item, "title");
                String citation = text(item, "citation");
                if (documentId.isBlank() && citation.isBlank()) continue;
                String key = citation.isBlank() ? documentId : citation;
                references.putIfAbsent(key, new AssessmentEvidenceReference(
                        documentId, title, citation));
            }
        } catch (JacksonException ignored) {
            // 单个步骤的历史快照损坏时，其他步骤的来源仍可用于审计。
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText("");
    }
}
