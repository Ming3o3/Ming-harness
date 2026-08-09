-- 保存上下文向量/关键词混合召回的离线评测聚合指标。
CREATE TABLE harness_context_retrieval_evaluation_reports (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(200) NOT NULL,
    top_k INTEGER NOT NULL,
    total_cases INTEGER NOT NULL,
    hit_cases INTEGER NOT NULL,
    hit_rate_at_k NUMERIC(10, 4) NOT NULL,
    recall_at_k NUMERIC(10, 4) NOT NULL,
    mrr NUMERIC(10, 4) NOT NULL,
    context_cases INTEGER NOT NULL,
    context_hit_cases INTEGER NOT NULL,
    context_hit_rate NUMERIC(10, 4) NOT NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_context_retrieval_evaluation_reports PRIMARY KEY (id),
    CONSTRAINT ck_harness_context_retrieval_evaluation_reports_top_k CHECK (top_k > 0),
    CONSTRAINT ck_harness_context_retrieval_evaluation_reports_total CHECK (total_cases > 0),
    CONSTRAINT ck_harness_context_retrieval_evaluation_reports_counts CHECK (
        hit_cases >= 0 AND hit_cases <= total_cases
        AND context_cases >= 0 AND context_hit_cases >= 0 AND context_hit_cases <= context_cases
    )
);

CREATE INDEX idx_harness_context_retrieval_evaluation_reports_tenant_created
    ON harness_context_retrieval_evaluation_reports (tenant_id, created_at DESC);
