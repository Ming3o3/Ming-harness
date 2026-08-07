package org.mingharness.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 保存一次上下文检索离线评测的聚合指标和脱敏用例结果。 */
@Entity
@Table(name = "harness_context_retrieval_evaluation_reports")
public class ContextRetrievalEvaluationReport {

    @Id
    private String id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "top_k", nullable = false)
    private int topK;

    @Column(nullable = false)
    private int totalCases;

    @Column(nullable = false)
    private int hitCases;

    @Column(name = "hit_rate_at_k", nullable = false, precision = 10, scale = 4)
    private BigDecimal hitRateAtK;

    @Column(name = "recall_at_k", nullable = false, precision = 10, scale = 4)
    private BigDecimal recallAtK;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal mrr;

    @Column(nullable = false)
    private int contextCases;

    @Column(nullable = false)
    private int contextHitCases;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal contextHitRate;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    protected ContextRetrievalEvaluationReport() {
    }

    public ContextRetrievalEvaluationReport(String tenantId, String name, int topK, int totalCases,
                                             int hitCases, BigDecimal hitRateAtK, BigDecimal recallAtK,
                                             BigDecimal mrr, int contextCases, int contextHitCases,
                                             BigDecimal contextHitRate, String details) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.name = name;
        this.topK = topK;
        this.totalCases = totalCases;
        this.hitCases = hitCases;
        this.hitRateAtK = hitRateAtK;
        this.recallAtK = recallAtK;
        this.mrr = mrr;
        this.contextCases = contextCases;
        this.contextHitCases = contextHitCases;
        this.contextHitRate = contextHitRate;
        this.details = details == null ? "" : details;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public int getTopK() { return topK; }
    public int getTotalCases() { return totalCases; }
    public int getHitCases() { return hitCases; }
    public BigDecimal getHitRateAtK() { return hitRateAtK; }
    public BigDecimal getRecallAtK() { return recallAtK; }
    public BigDecimal getMrr() { return mrr; }
    public int getContextCases() { return contextCases; }
    public int getContextHitCases() { return contextHitCases; }
    public BigDecimal getContextHitRate() { return contextHitRate; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
