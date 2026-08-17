package org.mingharness.education;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 将有界知识依赖图以脱敏 JSON 快照冻结在教育 Run 中。 */
public final class EducationDependencyGraphSnapshotCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EducationDependencyGraphSnapshotCodec() {
    }

    public static String encode(EducationDependencyGraph graph) {
        if (graph == null) return "[]";
        try {
            return OBJECT_MAPPER.writeValueAsString(graph);
        } catch (JacksonException exception) {
            return "[]";
        }
    }

    public static EducationDependencyGraph decode(String raw, String fallbackTarget) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) return null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) return null;
            String target = text(root, "targetConcept");
            if (target.isBlank()) target = fallbackTarget;
            List<EducationDependencyPath> paths = new ArrayList<>();
            JsonNode prerequisites = root.get("prerequisites");
            if (prerequisites != null && prerequisites.isArray()) {
                for (JsonNode item : prerequisites) {
                    if (item == null || !item.isObject()) continue;
                    paths.add(new EducationDependencyPath(
                            text(item, "conceptKey"), integer(item, "depth", 1),
                            number(item, "masteryScore"), number(item, "deficit"),
                            number(item, "uncertainty"), number(item, "forgettingRisk")));
                }
            }
            return new EducationDependencyGraph(target, paths, booleanValue(root, "truncated"));
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText("");
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble(0.0) : 0.0;
    }

    private static int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt(fallback) : fallback;
    }

    private static boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() && value.asBoolean(false);
    }
}
