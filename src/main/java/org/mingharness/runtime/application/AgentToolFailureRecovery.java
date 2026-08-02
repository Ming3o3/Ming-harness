package org.mingharness.runtime.application;

/** Agent 工具结果中与“不可用但可继续”相关的轻量判定。 */
final class AgentToolFailureRecovery {

    private AgentToolFailureRecovery() {
    }

    /** Git 不可用结果仍会以成功步骤传回模型，但不能冒充修改后的有效核验。 */
    static boolean verificationEligible(String output) {
        return output == null || !output.contains("\"verificationEligible\":false");
    }
}
