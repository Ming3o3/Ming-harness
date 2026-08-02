package org.mingharness.runtime.application;

/** 为 Agent 下一轮模型上下文保留任务约束和最新工具证据，避免截断后只剩无上下文的尾部。 */
final class AgentTranscriptFormatter {

    private static final String TRUNCATION_MARKER =
            "\n\n[…中间步骤已截断，保留任务和最近结果…]\n\n";

    private AgentTranscriptFormatter() {
    }

    static String fit(String value, int maximumChars) {
        if (value == null || value.isEmpty()) return "";
        int maximum = Math.max(1, maximumChars);
        if (value.length() <= maximum) return value;
        if (maximum <= TRUNCATION_MARKER.length() + 2) {
            return value.substring(0, maximum);
        }
        int available = maximum - TRUNCATION_MARKER.length();
        int headLength = Math.max(1, available / 2);
        int tailLength = Math.max(1, available - headLength);
        return value.substring(0, headLength) + TRUNCATION_MARKER
                + value.substring(value.length() - tailLength);
    }
}
