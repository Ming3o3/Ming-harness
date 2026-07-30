package org.mingharness.policy;

import org.mingharness.tool.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 默认确定性策略：先校验权限，再根据风险等级决定是否需要人工审批。 */
@Component
public class DefaultPolicyEngine implements PolicyEngine {

    @Override
    public PolicyDecision evaluate(PolicyContext context, ToolDefinition tool) {
        Set<String> granted = context.permissions() == null ? Set.of() : context.permissions();
        String missingPermission = tool.requiredPermissions().stream()
                .filter(permission -> !granted.contains(permission))
                .findFirst()
                .orElse(null);
        if (missingPermission != null) {
            return new PolicyDecision(PolicyDecisionType.DENY,
                    "缺少工具所需权限: " + missingPermission);
        }

        if ("ALLOW_EXTERNAL".equalsIgnoreCase(tool.networkPolicy())
                && !granted.contains("network.external")) {
            return new PolicyDecision(PolicyDecisionType.DENY,
                    "工具访问外部网络需要 network.external 权限");
        }

        String risk = tool.riskLevel() == null ? "LOW" : tool.riskLevel().toUpperCase();
        boolean highRisk = "HIGH".equals(risk) || "CRITICAL".equals(risk);
        if (tool.requiresApproval() || highRisk) {
            if (context.approvalGranted()) {
                return new PolicyDecision(PolicyDecisionType.ALLOW, "人工审批已通过");
            }
            return new PolicyDecision(PolicyDecisionType.REQUIRES_APPROVAL,
                    "高风险工具必须经过人工审批");
        }
        return new PolicyDecision(PolicyDecisionType.ALLOW, "策略允许执行");
    }
}
