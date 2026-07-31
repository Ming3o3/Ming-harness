package org.mingharness.runtime.application;

import org.mingharness.common.BusinessException;
import org.mingharness.runtime.api.TenantPolicyRequest;
import org.mingharness.runtime.api.TenantPolicyView;
import org.mingharness.runtime.api.TenantPolicyAuditView;
import org.mingharness.runtime.domain.TenantPolicy;
import org.mingharness.runtime.domain.TenantPolicyAudit;
import org.mingharness.runtime.domain.TenantPolicyLimits;
import org.mingharness.runtime.repository.TenantPolicyAuditRepository;
import org.mingharness.runtime.repository.TenantPolicyRepository;
import org.mingharness.tool.ToolRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 解析和维护租户级运行资源策略。
 *
 * <p>查询未配置策略的租户时返回平台默认限制，不在数据库中惰性写入默认记录，
 * 避免每一次普通 Run 创建都造成无意义的配置写放大。</p>
 */
@Service
public class TenantPolicyService {

    private static final int MAX_AUDIT_RECORDS = 100;

    private final TenantPolicyRepository policyRepository;
    private final TenantPolicyAuditRepository auditRepository;
    private final RuntimeLimits runtimeLimits;
    private final ToolRegistry toolRegistry;

    public TenantPolicyService(TenantPolicyRepository policyRepository,
                               TenantPolicyAuditRepository auditRepository,
                               RuntimeLimits runtimeLimits,
                               ToolRegistry toolRegistry) {
        this.policyRepository = policyRepository;
        this.auditRepository = auditRepository;
        this.runtimeLimits = runtimeLimits;
        this.toolRegistry = toolRegistry;
    }

    /** 返回创建 Run 时使用的有效限制；平台默认值仍是无法突破的硬上限。 */
    @Transactional(readOnly = true)
    public TenantPolicyLimits limitsFor(String tenantId) {
        requireTenantId(tenantId);
        return policyRepository.findById(tenantId)
                .map(this::limitsOf)
                .orElseGet(this::platformDefaults);
    }

    /** 返回控制台/管理 API 使用的策略视图，并标明是否正在使用平台默认值。 */
    @Transactional(readOnly = true)
    public TenantPolicyView get(String tenantId) {
        requireTenantId(tenantId);
        return policyRepository.findById(tenantId)
                .map(policy -> toView(policy, false))
                .orElseGet(() -> TenantPolicyView.from(tenantId, platformDefaults(), true, null, null, 0));
    }

    /** 使用完整配置快照创建或更新租户策略，并记录操作人和前后差异。 */
    @Transactional
    public TenantPolicyView upsert(String tenantId, TenantPolicyRequest request, String actorId) {
        requireTenantId(tenantId);
        TenantPolicyLimits requested = validateAgainstPlatform(request);
        TenantPolicy existing = policyRepository.findById(tenantId).orElse(null);
        String action = existing == null ? "TENANT_POLICY_CREATED" : "TENANT_POLICY_UPDATED";
        String before = existing == null ? null : describe(limitsOf(existing));
        TenantPolicy policy = existing == null ? new TenantPolicy(tenantId, requested) : existing;
        if (existing != null) {
            policy.update(requested);
        }
        TenantPolicy saved = policyRepository.save(policy);
        auditRepository.save(new TenantPolicyAudit(tenantId, safeActor(actorId), action,
                "before=" + (before == null ? "default" : before) + ";after=" + describe(requested)));
        return toView(saved, false);
    }

    /** 删除覆盖策略后立即回退到平台默认值，并保留恢复默认的管理审计。 */
    @Transactional
    public TenantPolicyView reset(String tenantId, String actorId) {
        requireTenantId(tenantId);
        TenantPolicy existing = policyRepository.findById(tenantId).orElse(null);
        if (existing != null) {
            policyRepository.delete(existing);
            auditRepository.save(new TenantPolicyAudit(tenantId, safeActor(actorId), "TENANT_POLICY_RESET",
                    "before=" + describe(limitsOf(existing)) + ";after=default:" + describe(platformDefaults())));
        }
        return TenantPolicyView.from(tenantId, platformDefaults(), true, null, null, 0);
    }

    /** 返回最近策略管理记录，避免管理控制台无限量读取历史。 */
    @Transactional(readOnly = true)
    public List<TenantPolicyAuditView> auditTrail(String tenantId) {
        requireTenantId(tenantId);
        return auditRepository.findByTenantIdOrderByCreatedAtDesc(tenantId,
                        PageRequest.of(0, MAX_AUDIT_RECORDS))
                .stream()
                .map(event -> new TenantPolicyAuditView(event.getId(), event.getTenantId(), event.getActorId(),
                        event.getEventType(), event.getDetails(), event.getCreatedAt()))
                .toList();
    }

    private TenantPolicyLimits validateAgainstPlatform(TenantPolicyRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TENANT_POLICY_REQUIRED", "租户运行策略不能为空");
        }
        TenantPolicyLimits requested;
        try {
            requested = new TenantPolicyLimits(request.maxActiveRuns(), request.maxStepsPerRun(),
                    request.maxInputLength(), request.maxBudget(), request.maxCreatesPerMinute(),
                    normalizeAllowedTools(request.allowedTools()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_POLICY", exception.getMessage());
        }
        TenantPolicyLimits platform = platformDefaults();
        if (requested.maxActiveRuns() > platform.maxActiveRuns()
                || requested.maxStepsPerRun() > platform.maxStepsPerRun()
                || requested.maxInputLength() > platform.maxInputLength()
                || requested.maxBudget().compareTo(platform.maxBudget()) > 0
                || requested.maxCreatesPerMinute() > platform.maxCreatesPerMinute()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TENANT_POLICY_EXCEEDS_PLATFORM_LIMIT",
                    "租户策略不能超过平台配置的硬上限");
        }
        return requested;
    }

    private TenantPolicyLimits platformDefaults() {
        return new TenantPolicyLimits(runtimeLimits.maxActiveRunsPerTenant(), runtimeLimits.maxStepsPerRun(),
                runtimeLimits.maxInputLength(), runtimeLimits.maxBudget(), runtimeLimits.maxCreatesPerMinute());
    }

    private TenantPolicyLimits limitsOf(TenantPolicy policy) {
        return new TenantPolicyLimits(policy.getMaxActiveRuns(), policy.getMaxStepsPerRun(),
                policy.getMaxInputLength(), policy.getMaxBudget(), policy.getMaxCreatesPerMinute(),
                parseAllowedTools(policy.getAllowedTools()));
    }

    private TenantPolicyView toView(TenantPolicy policy, boolean defaulted) {
        return TenantPolicyView.from(policy.getTenantId(), limitsOf(policy), defaulted,
                policy.getCreatedAt(), policy.getUpdatedAt(), policy.getVersion());
    }

    private void requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_ID", "租户标识不合法");
        }
    }

    private String safeActor(String actorId) {
        return actorId == null || actorId.isBlank() ? "system" : actorId;
    }

    /** 审计中只记录数字限制，不记录请求正文、密钥或其他敏感业务数据。 */
    private String describe(TenantPolicyLimits limits) {
        BigDecimal budget = limits.maxBudget().stripTrailingZeros();
        return "activeRuns=" + limits.maxActiveRuns()
                + ",steps=" + limits.maxStepsPerRun()
                + ",inputChars=" + limits.maxInputLength()
                + ",budget=" + budget.toPlainString()
                + ",createsPerMinute=" + limits.maxCreatesPerMinute()
                + ",allowedTools=" + (limits.allowedTools().isEmpty()
                ? "*" : String.join(",", limits.allowedTools().stream().sorted().toList()));
    }

    private Set<String> normalizeAllowedTools(Set<String> rawTools) {
        if (rawTools == null || rawTools.isEmpty()) {
            return Set.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String rawTool : rawTools) {
            if (rawTool == null || rawTool.isBlank()
                    || !rawTool.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}")) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_TOOL_NAME",
                        "租户工具白名单包含不合法的工具名称");
            }
            normalized.add(rawTool.trim());
        }
        Set<String> registered = toolRegistry.definitions().stream()
                .map(definition -> definition.name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> unknown = normalized.stream().filter(name -> !registered.contains(name)).collect(java.util.stream.Collectors.toSet());
        if (!unknown.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TENANT_TOOL_NOT_FOUND",
                    "租户工具白名单包含未注册工具: " + String.join(",", unknown));
        }
        return Set.copyOf(normalized);
    }

    private Set<String> parseAllowedTools(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        TreeSet<String> parsed = new TreeSet<>();
        for (String item : csv.split(",")) {
            if (!item.isBlank()) {
                parsed.add(item.trim());
            }
        }
        return Set.copyOf(parsed);
    }
}
