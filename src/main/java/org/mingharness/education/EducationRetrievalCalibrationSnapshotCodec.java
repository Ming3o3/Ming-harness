package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将检索校准结果以脱敏 JSON 冻结在 Run 中。 */
public final class EducationRetrievalCalibrationSnapshotCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EducationRetrievalCalibrationSnapshotCodec() {
    }

    public static String encode(EducationRetrievalCalibrationSnapshot snapshot) {
        if (snapshot == null) return "";
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            return "";
        }
    }

    public static EducationRetrievalCalibrationSnapshot decode(String raw) {
        if (raw == null || raw.isBlank()) return EducationRetrievalCalibrationSnapshot.prior();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) return EducationRetrievalCalibrationSnapshot.prior();
            JsonNode weightNode = root.get("weights");
            EducationRankingWeights weights = weightNode == null || !weightNode.isObject()
                    ? EducationRankingWeights.fixed()
                    : weights(weightNode);
            Map<String, EducationRetrievalCalibrationSlice> slices = slices(root.get("stateSlices"));
            return new EducationRetrievalCalibrationSnapshot(
                    text(root, "version"), integer(root, "sampleCount"),
                    number(root, "targetGroundingMean"),
                    number(root, "prerequisiteUtilityMean"),
                    number(root, "difficultyFitMean"),
                    number(root, "overallUtilityMean"), weights, slices);
        } catch (JacksonException exception) {
            return EducationRetrievalCalibrationSnapshot.prior();
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

    private static long integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong(0L) : 0L;
    }

    private static EducationRankingWeights weights(JsonNode value) {
        return new EducationRankingWeights(
                number(value, "retrievalRelevance"), number(value, "targetConceptMatch"),
                number(value, "prerequisiteGap"), number(value, "graphCoverage"),
                number(value, "difficultyFit"), text(value, "conditioning"));
    }

    private static Map<String, EducationRetrievalCalibrationSlice> slices(JsonNode value) {
        if (value == null || !value.isObject()) return Map.of();
        Map<String, EducationRetrievalCalibrationSlice> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : value.properties()) {
            JsonNode item = entry.getValue();
            if (item == null || !item.isObject()) continue;
            JsonNode weightNode = item.get("weights");
            EducationRankingWeights weights = weightNode == null || !weightNode.isObject()
                    ? EducationRankingWeights.fixed() : weights(weightNode);
            String conditioning = text(item, "conditioning");
            if (conditioning.isBlank()) conditioning = entry.getKey();
            result.put(entry.getKey(), new EducationRetrievalCalibrationSlice(
                    conditioning, integer(item, "sampleCount"),
                    number(item, "targetGroundingMean"),
                    number(item, "prerequisiteUtilityMean"),
                    number(item, "difficultyFitMean"),
                    number(item, "overallUtilityMean"), weights));
        }
        return result;
    }
}
