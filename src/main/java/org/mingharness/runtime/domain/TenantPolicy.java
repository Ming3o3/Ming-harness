package org.mingharness.runtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 租户级运行资源策略。
 *
 * <p>平台级 {@code RuntimeLimits} 是所有租户都不能突破的硬上限，本实体只保存租户更严格的覆盖值，
 * 从而避免单个租户占满共享 Worker、数据库连接或模型预算。</p>
 */
@Entity
@Table(name = "harness_tenant_policies")
public class TenantPolicy {

    @Id
    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    @Column(name = "max_active_runs", nullable = false)
    private int maxActiveRuns;

    @Column(name = "max_steps_per_run", nullable = false)
    private int maxStepsPerRun;

    @Column(name = "max_input_length", nullable = false)
    private int maxInputLength;

    @Column(name = "max_budget", nullable = false, precision = 38, scale = 8)
    private BigDecimal maxBudget;

    @Column(name = "max_creates_per_minute", nullable = false)
    private int maxCreatesPerMinute;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TenantPolicy() {
    }

    public TenantPolicy(String tenantId, TenantPolicyLimits limits) {
        this.tenantId = tenantId;
        this.createdAt = Instant.now();
        update(limits);
    }

    /** 用一次完整快照更新策略，避免部分字段沿用过期值。 */
    public void update(TenantPolicyLimits limits) {
        this.maxActiveRuns = limits.maxActiveRuns();
        this.maxStepsPerRun = limits.maxStepsPerRun();
        this.maxInputLength = limits.maxInputLength();
        this.maxBudget = limits.maxBudget();
        this.maxCreatesPerMinute = limits.maxCreatesPerMinute();
        this.updatedAt = Instant.now();
    }

    public String getTenantId() { return tenantId; }
    public int getMaxActiveRuns() { return maxActiveRuns; }
    public int getMaxStepsPerRun() { return maxStepsPerRun; }
    public int getMaxInputLength() { return maxInputLength; }
    public BigDecimal getMaxBudget() { return maxBudget; }
    public int getMaxCreatesPerMinute() { return maxCreatesPerMinute; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
