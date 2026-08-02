package org.mingharness.runtime.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTranscriptFormatterTests {

    @Test
    void shouldKeepTaskPrefixAndLatestToolEvidenceWhenContextIsTruncated() {
        String transcript = "任务约束：只修改订单状态逻辑，不要改数据库结构。"
                + "\n旧工具结果：" + "旧内容".repeat(120)
                + "\n最新工具结果：sha256=latest-file-hash，已读取真实文件。";

        String fitted = AgentTranscriptFormatter.fit(transcript, 180);

        assertTrue(fitted.length() <= 180);
        assertTrue(fitted.startsWith("任务约束：只修改订单状态逻辑"));
        assertTrue(fitted.contains("最新工具结果：sha256=latest-file-hash"));
        assertTrue(fitted.contains("中间步骤已截断"));
    }
}
