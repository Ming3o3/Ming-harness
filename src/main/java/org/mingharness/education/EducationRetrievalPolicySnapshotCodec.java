package org.mingharness.education;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 将自适应策略选择结果冻结为可回放 JSON。 */
public final class EducationRetrievalPolicySnapshotCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EducationRetrievalPolicySnapshotCodec() {
    }

    public static String encode(EducationRetrievalPolicySnapshot snapshot) {
        if (snapshot == null) return "";
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            return "";
        }
    }

    public static EducationRetrievalPolicySnapshot decode(String raw) {
        if (raw == null || raw.isBlank()) return EducationRetrievalPolicySnapshot.prior("UNKNOWN");
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) return EducationRetrievalPolicySnapshot.prior("UNKNOWN");
            List<EducationRetrievalPolicyCandidate> candidates = new ArrayList<>();
            JsonNode values = root.get("candidates");
            if (values != null && values.isArray()) {
                for (JsonNode item : values) {
                    if (item == null || !item.isObject()) continue;
                    candidates.add(new EducationRetrievalPolicyCandidate(
                            text(item, "strategy"), integer(item, "runCount"), integer(item, "assessmentCount"),
                            number(item, "masteryGainMean"), number(item, "accuracyRate"),
                            number(item, "targetReachRate"), number(item, "outcomeScore"),
                            number(item, "confidence"), number(item, "adjustedScore"), text(item, "sampleStatus")));
                }
            }
            return new EducationRetrievalPolicySnapshot(
                    text(root, "version"), text(root, "conditioning"), text(root, "selectedStrategy"),
                    integer(root, "eligibleRunCount"), text(root, "selectionReason"), candidates);
        } catch (JacksonException exception) {
            return EducationRetrievalPolicySnapshot.prior("UNKNOWN");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText("");
    }

    private static long integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong(0L) : 0L;
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble(0.0) : 0.0;
    }
}
