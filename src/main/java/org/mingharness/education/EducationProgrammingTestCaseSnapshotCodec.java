package org.mingharness.education;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 将教师测试用例编码为可审计的 Run 快照。 */
public final class EducationProgrammingTestCaseSnapshotCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EducationProgrammingTestCaseSnapshotCodec() {
    }

    public static String encode(List<LearningAssignmentTestCase> source) {
        List<EducationProgrammingTestCase> cases = source == null ? List.of() : source.stream()
                .filter(item -> item != null && item.isEnabled())
                .map(item -> new EducationProgrammingTestCase(item.getCaseKey(), item.getInputData(),
                        item.getExpectedOutput(), item.getWeight(), item.getConceptKey()))
                .toList();
        try {
            return OBJECT_MAPPER.writeValueAsString(new EducationProgrammingTestCaseSnapshot(
                    Instant.now().toString(), cases));
        } catch (JacksonException exception) {
            return "[]";
        }
    }

    public static EducationProgrammingTestCaseSnapshot decode(String raw) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
            return EducationProgrammingTestCaseSnapshot.empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) return EducationProgrammingTestCaseSnapshot.empty();
            String version = text(root, "version");
            List<EducationProgrammingTestCase> cases = new ArrayList<>();
            JsonNode values = root.get("cases");
            if (values != null && values.isArray()) {
                for (JsonNode item : values) {
                    if (item == null || !item.isObject()) continue;
                    String caseKey = text(item, "caseKey");
                    String expectedOutput = text(item, "expectedOutput");
                    if (caseKey.isBlank() || expectedOutput.isBlank()) continue;
                    cases.add(new EducationProgrammingTestCase(caseKey, text(item, "input"),
                            expectedOutput, number(item, "weight", 1.0), textOrNull(item, "conceptKey")));
                }
            }
            return new EducationProgrammingTestCaseSnapshot(version, cases);
        } catch (JacksonException exception) {
            return EducationProgrammingTestCaseSnapshot.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText("");
    }

    private static double number(JsonNode node, String field, double fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble(fallback) : fallback;
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = text(node, field).trim();
        return value.isBlank() ? null : value;
    }
}
