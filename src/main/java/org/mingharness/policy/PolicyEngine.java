package org.mingharness.policy;

import org.mingharness.tool.ToolDefinition;

public interface PolicyEngine {

    PolicyDecision evaluate(PolicyContext context, ToolDefinition tool);
}
