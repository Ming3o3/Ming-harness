package org.mingharness.education;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将学习者掌握度、时间证据与保持度以脱敏 JSON 冻结在教育 Run 中。 */
public final class LearnerStateSnapshotCodec {

    public static final String VERSION = "STATE_WITH_RETENTION_V1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LearnerStateSnapshotCodec() {
    }

    /** 兼容旧调用方；捕获时点由本次快照生成时确定一次。 */
    public static String encode(List<LearnerMastery> mastery) {
        return encode(mastery, Instant.now());
    }

    /** 使用明确捕获时点编码，供 Run 创建和可复现实验使用。 */
    public static String encode(List<LearnerMastery> mastery, Instant capturedAt) {
        Instant effectiveCapturedAt = capturedAt == null ? Instant.now() : capturedAt;
        List<Map<String, Object>> items = new ArrayList<>();
        if (mastery != null) {
            mastery.stream()
                    .filter(item -> item != null)
                    .limit(100)
                    .forEach(item -> {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("conceptKey", item.getConceptKey());
                        value.put("masteryScore", item.getMasteryScore());
                        value.put("attempts", item.getAttempts());
                        value.put("correctAttempts", item.getCorrectAttempts());
                        value.put("lastAssessedAt", item.getLastAssessedAt() == null
                                ? null : item.getLastAssessedAt().toString());
                        value.put("retentionScore", item.retentionScoreAt(effectiveCapturedAt));
                        value.put("effectiveMastery", item.effectiveMasteryAt(effectiveCapturedAt));
                        items.add(value);
                    });
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("retentionModel", LearnerRetentionModel.VERSION);
        root.put("capturedAt", effectiveCapturedAt.toString());
        root.put("items", items);
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JacksonException exception) {
            return "[]";
        }
    }

    /** 旧 API 继续返回按知识点索引的证据。 */
    public static Map<String, LearnerStateEvidence> decode(String raw) {
        return decodeSnapshot(raw).evidence();
    }

    /** 读取新版对象快照，同时兼容 V78 以前的数组快照。 */
    public static LearnerStateSnapshot decodeSnapshot(String raw) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
            return LearnerStateSnapshot.empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null) return LearnerStateSnapshot.empty();
            boolean legacyArray = root.isArray();
            JsonNode items = legacyArray ? root : root.get("items");
            if (items == null || !items.isArray()) return LearnerStateSnapshot.empty();
            Instant capturedAt = legacyArray ? null : instant(root, "capturedAt");
            String version = legacyArray ? "LEGACY_ARRAY" : text(root, "version");
            Map<String, LearnerStateEvidence> result = new LinkedHashMap<>();
            for (JsonNode item : items) {
                if (item == null || !item.isObject()) continue;
                String concept = text(item, "conceptKey").trim().toLowerCase(java.util.Locale.ROOT);
                if (concept.isBlank()) continue;
                Instant lastAssessedAt = instant(item, "lastAssessedAt");
                double retention = number(item, "retentionScore", Double.NaN);
                if (!Double.isFinite(retention)) {
                    retention = LearnerRetentionModel.retentionAt(lastAssessedAt, capturedAt);
                }
                result.put(concept, new LearnerStateEvidence(
                        number(item, "masteryScore", 0.0),
                        integer(item, "attempts"),
                        integer(item, "correctAttempts"),
                        lastAssessedAt,
                        retention));
            }
            return new LearnerStateSnapshot(version, capturedAt, result);
        } catch (JacksonException exception) {
            return LearnerStateSnapshot.empty();
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

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt(0) : 0;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeException ignored) {
            return null;
        }
    }
}
