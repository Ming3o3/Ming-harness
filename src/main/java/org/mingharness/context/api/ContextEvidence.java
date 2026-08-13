package org.mingharness.context.api;

import java.util.List;

public record ContextEvidence(
        String documentId,
        String title,
        String citation,
        String excerpt,
        double retrievalScore,
        String rankingReason,
        List<String> prerequisiteGaps,
        EducationRankingBreakdown rankingBreakdown
) {

    /** 兼容普通检索和历史快照；教育检索会填充排序理由与前置缺口。 */
    public ContextEvidence(String documentId, String title, String citation, String excerpt) {
        this(documentId, title, citation, excerpt, 0.0, "", List.of(),
                EducationRankingBreakdown.empty());
    }

    /** 兼容已持久化的教育解释字段；旧调用方没有结构化拆解时使用空值。 */
    public ContextEvidence(String documentId, String title, String citation, String excerpt,
                           double retrievalScore, String rankingReason,
                           List<String> prerequisiteGaps) {
        this(documentId, title, citation, excerpt, retrievalScore, rankingReason,
                prerequisiteGaps, EducationRankingBreakdown.empty());
    }

    public ContextEvidence {
        documentId = documentId == null ? "" : documentId;
        title = title == null ? "" : title;
        citation = citation == null ? "" : citation;
        excerpt = excerpt == null ? "" : excerpt;
        retrievalScore = Double.isFinite(retrievalScore) ? retrievalScore : 0.0;
        rankingReason = rankingReason == null ? "" : rankingReason;
        prerequisiteGaps = prerequisiteGaps == null ? List.of() : List.copyOf(prerequisiteGaps);
        rankingBreakdown = rankingBreakdown == null ? EducationRankingBreakdown.empty() : rankingBreakdown;
    }
}
