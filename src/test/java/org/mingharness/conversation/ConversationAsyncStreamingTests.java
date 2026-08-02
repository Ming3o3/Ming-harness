package org.mingharness.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditEventRepository;
import org.mingharness.conversation.api.ConversationDetail;
import org.mingharness.conversation.api.CreateConversationRequest;
import org.mingharness.conversation.api.SendConversationMessageRequest;
import org.mingharness.model.ModelGateway;
import org.mingharness.model.ModelRequest;
import org.mingharness.model.ModelResponse;
import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(ConversationAsyncStreamingTests.StreamingModelConfiguration.class)
@TestPropertySource(properties = {
        "harness.local-execution.async=true",
        "harness.runtime.event-stream-poll-ms=100"
})
class ConversationAsyncStreamingTests {

    @Autowired
    private ConversationService conversationService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ConversationContextRepository contextRepository;
    @Autowired
    private ConversationMessageRepository messageRepository;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void cleanDatabase() {
        contextRepository.deleteAll();
        messageRepository.deleteAll();
        auditEventRepository.deleteAll();
        runRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Test
    void shouldReturnPendingConversationThenPersistStreamingAssistantContent() throws InterruptedException {
        ConversationDetail created = conversationService.create(
                "tenant-stream", "operator", new CreateConversationRequest("流式对话"));

        Instant startedAt = Instant.now();
        ConversationDetail submitted = conversationService.send(
                created.conversation().id(), "tenant-stream", "operator",
                new SendConversationMessageRequest("请流式回答", null, 2),
                "stream-round-1", "workspace.read");

        assertTrue(Duration.between(startedAt, Instant.now()).toMillis() < 300,
                "提交不应等待模型生成完成");
        assertEquals(2, submitted.messages().size());
        assertEquals(ConversationMessageStatus.PENDING, submitted.messages().get(1).status());

        boolean sawPartialContent = false;
        ConversationDetail latest = submitted;
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            latest = conversationService.detail(created.conversation().id(), "tenant-stream", "operator");
            String content = latest.messages().get(1).content();
            sawPartialContent = sawPartialContent || "正在生成".equals(content);
            if (latest.messages().get(1).status() == ConversationMessageStatus.COMPLETED) break;
            Thread.sleep(25);
        }

        assertTrue(sawPartialContent, "执行中应保存可见的流式内容");
        assertEquals(ConversationMessageStatus.COMPLETED, latest.messages().get(1).status());
        assertEquals("正在生成完整回答", latest.messages().get(1).content());
        assertEquals(RunStatus.SUCCEEDED,
                runRepository.findById(latest.messages().get(1).runId()).orElseThrow().getStatus());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StreamingModelConfiguration {
        @Bean
        @Primary
        ModelGateway streamingModelGateway() {
            return new ModelGateway() {
                @Override
                public ModelResponse complete(ModelRequest request) {
                    return new ModelResponse("正在生成完整回答", "test-model", "prompt-v1", 1, 2,
                            BigDecimal.ZERO);
                }

                @Override
                public ModelResponse completeStreaming(ModelRequest request, Consumer<String> onContent) {
                    onContent.accept("正在生成");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    onContent.accept("正在生成完整回答");
                    return complete(request);
                }
            };
        }
    }
}
