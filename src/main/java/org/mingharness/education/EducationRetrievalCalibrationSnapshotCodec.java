package org.mingharness.education;

import org.mingharness.context.api.EducationRankingWeights;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
                    : new EducationRankingWeights(
                    number(weightNode, "retrievalRelevance"),
                    number(weightNode, "targetConceptMatch"),
                    number(weightNode, "prerequisiteGap"),
                    number(weightNode, "graphCoverage"),
                    number(weightNode, "difficultyFit"),
                    text(weightNode, "conditioning"));
            return new EducationRetrievalCalibrationSnapshot(
                    text(root, "version"), integer(root, "sampleCount"),
                    number(root, "targetGroundingMean"),
                    number(root, "prerequisiteUtilityMean"),
                    number(root, "difficultyFitMean"),
                    number(root, "overallUtilityMean"), weights);
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
}
