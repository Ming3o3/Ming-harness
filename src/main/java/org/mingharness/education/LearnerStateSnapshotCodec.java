package org.mingharness.education;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将学习者掌握度与证据量以脱敏 JSON 冻结在教育 Run 中。 */
public final class LearnerStateSnapshotCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LearnerStateSnapshotCodec() {
    }

    public static String encode(List<LearnerMastery> mastery) {
        if (mastery == null || mastery.isEmpty()) return "[]";
        try {
            return OBJECT_MAPPER.writeValueAsString(mastery.stream()
                    .filter(item -> item != null)
                    .limit(100)
                    .map(item -> Map.of(
                            "conceptKey", item.getConceptKey(),
                            "masteryScore", item.getMasteryScore(),
                            "attempts", item.getAttempts(),
                            "correctAttempts", item.getCorrectAttempts()))
                    .toList());
        } catch (JacksonException exception) {
            return "[]";
        }
    }

    public static Map<String, LearnerStateEvidence> decode(String raw) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) return Map.of();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isArray()) return Map.of();
            Map<String, LearnerStateEvidence> result = new LinkedHashMap<>();
            for (JsonNode item : root) {
                if (item == null || !item.isObject()) continue;
                String concept = text(item, "conceptKey").trim().toLowerCase(java.util.Locale.ROOT);
                if (concept.isBlank()) continue;
                result.put(concept, new LearnerStateEvidence(
                        number(item, "masteryScore"),
                        integer(item, "attempts"),
                        integer(item, "correctAttempts")));
            }
            return result.isEmpty() ? Map.of() : Map.copyOf(result);
        } catch (JacksonException exception) {
            return Map.of();
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

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt(0) : 0;
    }
}
