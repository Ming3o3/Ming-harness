package org.mingharness.policy;

public record PolicyDecision(PolicyDecisionType type, String reason) {

    public boolean isAllowed() {
        return type == PolicyDecisionType.ALLOW;
    }

    public boolean requiresApproval() {
        return type == PolicyDecisionType.REQUIRES_APPROVAL;
    }
}
