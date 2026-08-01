package org.mingharness.runtime.api;

import org.junit.jupiter.api.Test;
import org.mingharness.conversation.api.SendConversationMessageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTurnLimitDefaultsTests {

    @Test
    void shouldDefaultConversationAgentTurnsToOneThousand() {
        SendConversationMessageRequest request = new SendConversationMessageRequest("请分析项目", null, null);

        assertEquals(1_000, request.effectiveMaxTurns());
    }

    @Test
    void shouldDefaultRunAgentTurnsToOneThousand() {
        CreateRunRequest request = new CreateRunRequest("tenant-demo", "operator", "分析项目", "请分析项目",
                null, null, null, null, null);

        assertEquals(1_000, request.effectiveMaxTurns());
    }
}
