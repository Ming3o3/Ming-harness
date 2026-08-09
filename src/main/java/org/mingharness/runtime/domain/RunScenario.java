package org.mingharness.runtime.domain;

/** 业务场景用于把运行指标和治理动作映射回真实 Agent 工作流。 */
public enum RunScenario {
    UNCLASSIFIED,
    KNOWLEDGE_QA,
    CODE_AGENT,
    PROCESS_AUTOMATION
}
