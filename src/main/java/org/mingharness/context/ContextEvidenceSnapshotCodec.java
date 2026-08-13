package org.mingharness.context;

import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.EducationRankingBreakdown;
import org.mingharness.context.api.EducationRankingWeights;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Run 步骤中教育检索证据快照的统一编解码器。
 *
 * <p>检索证据既要在运行详情中展示，也要被教育实验聚合器复用；集中在 context
 * 模块可以避免不同消费者各自解释历史 JSON，保证排序拆解字段的语义一致。</p>
 */
public final class ContextEvidenceSnapshotCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ContextEvidenceSnapshotCodec() {
    }

    public static String encode(List<ContextEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) return "[]";
        try {
            return OBJECT_MAPPER.writeValueAsString(evidences.stream()
                    .filter(evidence -> evidence != null)
                    .map(evidence -> new ContextEvidence(
                            safe(evidence.documentId()), safe(evidence.title()),
                            safe(evidence.citation()), safe(evidence.excerpt()),
                            evidence.retrievalScore(), safe(evidence.rankingReason()),
                            evidence.prerequisiteGaps(), evidence.rankingBreakdown()))
                    .toList());
        } catch (JacksonException exception) {
            return "[]";
        }
    }

    public static List<ContextEvidence> decode(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isArray()) return List.of();
            List<ContextEvidence> result = new ArrayList<>();
            for (JsonNode item : root) {
                if (item == null || !item.isObject()) continue;
                result.add(new ContextEvidence(
                        text(item, "documentId"), text(item, "title"),
                        text(item, "citation"), text(item, "excerpt"),
                        number(item, "retrievalScore"), text(item, "rankingReason"),
                        strings(item, "prerequisiteGaps"), rankingBreakdown(item)));
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

    private static double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble(0.0) : 0.0;
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return List.copyOf(result);
    }

    private static EducationRankingBreakdown rankingBreakdown(JsonNode node) {
        JsonNode value = node.get("rankingBreakdown");
        if (value == null || !value.isObject()) return EducationRankingBreakdown.empty();
        return new EducationRankingBreakdown(
                number(value, "retrievalRelevance"), number(value, "targetConceptMatch"),
                number(value, "prerequisiteGap"), number(value, "graphCoverage"),
                number(value, "difficultyFit"), number(value, "marginalCoverageScore"),
                number(value, "redundancyPenalty"), number(value, "finalScore"), weights(value));
    }

    private static EducationRankingWeights weights(JsonNode value) {
        JsonNode weights = value.get("weights");
        if (weights == null || !weights.isObject()) return EducationRankingWeights.fixed();
        return new EducationRankingWeights(
                number(weights, "retrievalRelevance"), number(weights, "targetConceptMatch"),
                number(weights, "prerequisiteGap"), number(weights, "graphCoverage"),
                number(weights, "difficultyFit"), text(weights, "conditioning"));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
