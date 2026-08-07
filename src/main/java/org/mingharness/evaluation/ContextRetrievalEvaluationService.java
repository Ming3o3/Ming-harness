package org.mingharness.evaluation;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.ContextBuilder;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.evaluation.api.ContextRetrievalEvaluationCaseRequest;
import org.mingharness.evaluation.api.ContextRetrievalEvaluationRequest;
import org.mingharness.evaluation.api.ContextRetrievalEvaluationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在不调用生成模型的情况下评估授权上下文召回质量。
 *
 * <p>相关来源使用父文档标识，子块和父窗口 citation 会在评测时归一化到同一来源。
 * expectedContains 是可选的上下文内容断言；没有断言的用例仍参与 Recall/MRR，但不计入
 * contextHitRate 分母。</p>
 */
@Service
public class ContextRetrievalEvaluationService {

    private final ContextBuilder contextBuilder;
    private final ContextRetrievalEvaluationReportRepository reportRepository;
    private final SensitiveDataSanitizer sanitizer;

    public ContextRetrievalEvaluationService(ContextBuilder contextBuilder,
                                              ContextRetrievalEvaluationReportRepository reportRepository,
                                              SensitiveDataSanitizer sanitizer) {
        this.contextBuilder = contextBuilder;
        this.reportRepository = reportRepository;
        this.sanitizer = sanitizer;
    }

    /** 评测包含外部 embedding 调用，不能持有一个跨所有用例的数据库事务。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ContextRetrievalEvaluationView run(String tenantId, String userId,
                                               ContextRetrievalEvaluationRequest request) {
        int topK = request.effectiveTopK();
        int maxChars = request.effectiveMaxChars();
        int totalCases = request.cases().size();
        int hitCases = 0;
        int contextCases = 0;
        int contextHitCases = 0;
        double recallSum = 0.0;
        double reciprocalRankSum = 0.0;
        List<String> details = new ArrayList<>(totalCases);

        for (ContextRetrievalEvaluationCaseRequest item : request.cases()) {
            ContextResult result = contextBuilder.build(tenantId, userId,
                    sanitizer.sanitize(item.query()), maxChars);
            Set<String> relevantSources = item.relevantSources().stream()
                    .map(this::parentKey)
                    .filter(value -> !value.isBlank())
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            List<String> retrievedSources = result.evidences().stream()
                    .map(ContextEvidence::citation)
                    .map(this::parentKey)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .limit(topK)
                    .toList();
            long relevantRetrieved = retrievedSources.stream()
                    .filter(relevantSources::contains)
                    .count();
            double caseRecall = relevantSources.isEmpty()
                    ? 0.0 : (double) relevantRetrieved / relevantSources.size();
            recallSum += caseRecall;
            int rank = firstRelevantRank(retrievedSources, relevantSources);
            if (rank > 0) {
                hitCases++;
                reciprocalRankSum += 1.0 / rank;
            }

            List<String> expected = item.effectiveExpectedContains().stream()
                    .map(sanitizer::sanitize)
                    .filter(value -> !value.isBlank())
                    .toList();
            String contextText = result.text() == null ? "" : result.text();
            boolean contextHit = !expected.isEmpty()
                    && expected.stream().allMatch(contextText::contains);
            if (!expected.isEmpty()) {
                contextCases++;
                if (contextHit) contextHitCases++;
            }
            details.add(detail(item.name(), caseRecall, rank, contextHit, !expected.isEmpty()));
        }

        BigDecimal hitRateAtK = ratio(hitCases, totalCases);
        BigDecimal recallAtK = ratio(recallSum, totalCases);
        BigDecimal mrr = ratio(reciprocalRankSum, totalCases);
        BigDecimal contextHitRate = ratio(contextHitCases, contextCases);
        ContextRetrievalEvaluationReport report = new ContextRetrievalEvaluationReport(
                tenantId, sanitizer.sanitize(request.name()), topK, totalCases, hitCases,
                hitRateAtK, recallAtK, mrr, contextCases, contextHitCases, contextHitRate,
                String.join("\n", details));
        return ContextRetrievalEvaluationView.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ContextRetrievalEvaluationView> list(String tenantId) {
        return reportRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ContextRetrievalEvaluationView::from)
                .toList();
    }

    private int firstRelevantRank(List<String> retrievedSources, Set<String> relevantSources) {
        for (int index = 0; index < retrievedSources.size(); index++) {
            if (relevantSources.contains(retrievedSources.get(index))) return index + 1;
        }
        return 0;
    }

    private String detail(String name, double recall, int rank, boolean contextHit,
                          boolean contextEvaluated) {
        return sanitizer.sanitize(name) + "|recallAtK=" + format(recall)
                + "|rank=" + rank
                + "|contextHit=" + (contextEvaluated ? Boolean.toString(contextHit) : "NA");
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal ratio(double numerator, int denominator) {
        if (denominator < 1) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(int numerator, int denominator) {
        return ratio((double) numerator, denominator);
    }

    /** 将 document:id#window:n#chunk:m、document:id#chunk:m 归一化为 document:id。 */
    private String parentKey(String citation) {
        if (citation == null) return "";
        String normalized = sanitizer.sanitize(citation).trim();
        int window = normalized.indexOf("#window:");
        int chunk = normalized.indexOf("#chunk:");
        int end = normalized.length();
        if (window >= 0) end = Math.min(end, window);
        if (chunk >= 0) end = Math.min(end, chunk);
        return normalized.substring(0, end);
    }
}
