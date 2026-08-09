package org.mingharness.runtime.application;

import org.mingharness.context.api.ContextEvidence;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 将模型步骤实际使用的授权来源以脱敏 JSON 快照持久化，供 Run 详情追溯。 */
final class ContextEvidenceCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ContextEvidenceCodec() {
    }

    static String encode(List<ContextEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) return "[]";
        try {
            return OBJECT_MAPPER.writeValueAsString(evidences.stream()
                    .filter(evidence -> evidence != null)
                    .map(evidence -> new ContextEvidence(
                            safe(evidence.documentId()), safe(evidence.title()),
                            safe(evidence.citation()), safe(evidence.excerpt())))
                    .toList());
        } catch (JacksonException exception) {
            return "[]";
        }
    }

    static List<ContextEvidence> decode(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isArray()) return List.of();
            List<ContextEvidence> result = new ArrayList<>();
            for (JsonNode item : root) {
                if (item == null || !item.isObject()) continue;
                result.add(new ContextEvidence(
                        text(item, "documentId"), text(item, "title"),
                        text(item, "citation"), text(item, "excerpt")));
            }
            return List.copyOf(result);
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText("");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
