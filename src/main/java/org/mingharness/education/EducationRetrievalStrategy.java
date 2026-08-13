package org.mingharness.education;

import java.util.Locale;

/**
 * 教育检索的可复现实验条件。
 *
 * <p>策略在 Run 创建时冻结，既用于论文基线/消融，也避免重试时因服务端配置变化
 * 导致同一个学习样本使用不同的检索方法。</p>
 */
public enum EducationRetrievalStrategy {
    /** 向量 + 关键词 + 课程约束 + 掌握度/依赖图边际覆盖选择。 */
    FULL,
    /** 仅向量召回，保留课程硬约束但不使用学习者状态重排。 */
    VECTOR_ONLY,
    /** 仅关键词召回，保留课程硬约束但不使用学习者状态重排。 */
    KEYWORD_ONLY,
    /** 混合召回，但去掉掌握度和依赖图状态项，用于状态消融实验。 */
    NO_LEARNER_STATE,
    /** 混合召回并使用学习者状态，但固定排序权重，用于自适应权重消融。 */
    STATIC_WEIGHT;

    public static EducationRetrievalStrategy parse(String value) {
        if (value == null || value.isBlank()) return FULL;
        try {
            return value.trim().toUpperCase(Locale.ROOT).equals("HYBRID")
                    ? FULL : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FULL;
        }
    }

    public boolean usesVector() {
        return this != KEYWORD_ONLY;
    }

    public boolean usesKeyword() {
        return this != VECTOR_ONLY;
    }

    public boolean usesLearnerState() {
        return this == FULL || this == STATIC_WEIGHT;
    }

    public boolean usesAdaptiveWeights() {
        return this == FULL;
    }
}
