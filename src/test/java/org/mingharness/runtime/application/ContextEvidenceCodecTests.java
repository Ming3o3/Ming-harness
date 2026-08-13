package org.mingharness.runtime.application;

import org.junit.jupiter.api.Test;
import org.mingharness.context.api.ContextEvidence;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEvidenceCodecTests {

    @Test
    void shouldRoundTripAuthorizedEvidenceSnapshot() {
        List<ContextEvidence> evidences = List.of(
                new ContextEvidence("document-1", "退款规则", "document:document-1#window-0", "退款将在 3 个工作日到账",
                        0.87, "匹配课程约束，并优先覆盖前置知识缺口：集合", List.of("集合")),
                new ContextEvidence("memory-1", "用户偏好", "memory:memory-1#window-0", "优先给出处理时限")
        );

        List<ContextEvidence> decoded = ContextEvidenceCodec.decode(ContextEvidenceCodec.encode(evidences));
        assertEquals(evidences, decoded);
        assertEquals(0.87, decoded.get(0).retrievalScore());
        assertEquals(List.of("集合"), decoded.get(0).prerequisiteGaps());
    }

    @Test
    void shouldIgnoreMalformedEvidenceSnapshot() {
        assertTrue(ContextEvidenceCodec.decode("not-json").isEmpty());
        assertTrue(ContextEvidenceCodec.decode("{\"citation\":\"document:1\"}").isEmpty());
    }
}
