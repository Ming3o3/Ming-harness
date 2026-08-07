package org.mingharness.evaluation;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.context.ContextBuilder;
import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.mingharness.evaluation.api.ContextRetrievalEvaluationCaseRequest;
import org.mingharness.evaluation.api.ContextRetrievalEvaluationRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextRetrievalEvaluationServiceTests {

    @Test
    void shouldCalculateRecallMrrAndContextHitRateAcrossCases() {
        ContextBuilder contextBuilder = mock(ContextBuilder.class);
        ContextRetrievalEvaluationReportRepository repository = mock(ContextRetrievalEvaluationReportRepository.class);
        when(repository.save(any(ContextRetrievalEvaluationReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        String relevant = "document:doc-relevant";
        when(contextBuilder.build("tenant-a", "operator", "如何回滚", 4_000))
                .thenReturn(new ContextResult("[doc-1] 其他\n[doc-relevant] 回滚答案片段", List.of(
                        new ContextEvidence("doc-other", "其他", "document:doc-other#window:0#chunk:0", "其他"),
                        new ContextEvidence("doc-relevant", "回滚", relevant + "#window:1#chunk:2", "回滚答案片段"))));
        when(contextBuilder.build("tenant-a", "operator", "如何发布", 4_000))
                .thenReturn(new ContextResult("只有发布说明", List.of(
                        new ContextEvidence("doc-other", "其他", "document:doc-other#chunk:0", "其他"))));

        ContextRetrievalEvaluationService service = new ContextRetrievalEvaluationService(
                contextBuilder, repository, new SensitiveDataSanitizer());
        ContextRetrievalEvaluationRequest request = new ContextRetrievalEvaluationRequest("检索基线", List.of(
                new ContextRetrievalEvaluationCaseRequest("回滚", "如何回滚", List.of(relevant),
                        List.of("回滚答案片段")),
                new ContextRetrievalEvaluationCaseRequest("发布", "如何发布", List.of("document:doc-missing"),
                        List.of("缺失片段"))), 2, 4_000);

        var result = service.run("tenant-a", "operator", request);

        assertEquals(2, result.totalCases());
        assertEquals(1, result.hitCases());
        assertEquals(new BigDecimal("0.5000"), result.hitRateAtK());
        assertEquals(new BigDecimal("0.5000"), result.recallAtK());
        assertEquals(new BigDecimal("0.2500"), result.mrr());
        assertEquals(2, result.contextCases());
        assertEquals(1, result.contextHitCases());
        assertEquals(new BigDecimal("0.5000"), result.contextHitRate());
        assertTrue(result.details().contains("回滚|recallAtK=1.0000|rank=2|contextHit=true"));
        assertTrue(result.details().contains("发布|recallAtK=0.0000|rank=0|contextHit=false"));
    }
}
