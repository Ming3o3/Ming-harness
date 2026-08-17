package org.mingharness.education;

/** 目标知识点到某个前置知识点的最短依赖深度及学习者状态证据。 */
public record EducationDependencyPath(String conceptKey, int depth,
                                      double masteryScore, double deficit,
                                      double uncertainty, double forgettingRisk) {

    /** 兼容尚未携带状态风险的旧调用方。 */
    public EducationDependencyPath(String conceptKey, int depth,
                                   double masteryScore, double deficit) {
        this(conceptKey, depth, masteryScore, deficit, 0.0, 0.0);
    }

    public EducationDependencyPath {
        conceptKey = conceptKey == null ? "" : conceptKey.trim();
        depth = Math.max(1, depth);
        masteryScore = bounded(masteryScore);
        deficit = bounded(deficit);
        uncertainty = bounded(uncertainty);
        forgettingRisk = bounded(forgettingRisk);
    }

    /**
     * 用可解释的状态优先级安排前置知识：缺口是主信号，不确定性和遗忘风险
     * 用于避免跳过尚未可靠掌握或可能已经遗忘的节点；更深层依赖适度降权。
     */
    public double statePriority() {
        return bounded((0.70 * deficit + 0.20 * uncertainty + 0.10 * forgettingRisk)
                / Math.max(1, depth));
    }

    private static double bounded(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
