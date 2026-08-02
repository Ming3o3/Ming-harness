package org.mingharness.runtime;

import org.junit.jupiter.api.Test;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.conversation.ConversationMessageWriter;
import org.mingharness.observability.HarnessMetrics;
import org.mingharness.runtime.application.RunRecoveryService;
import org.mingharness.runtime.application.RuntimeLimits;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 验证应用关闭后，恢复调度不会再访问已销毁的数据源。 */
class RunRecoveryServiceShutdownTests {

    @Test
    void shouldSkipScheduledRecoveryAfterContextClosed() {
        RunRepository runRepository = mock(RunRepository.class);
        AuditTrailService auditTrailService = mock(AuditTrailService.class);
        HarnessMetrics metrics = mock(HarnessMetrics.class);
        ConversationMessageWriter messageWriter = mock(ConversationMessageWriter.class);
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        org.mockito.Mockito.when(entityManagerFactory.isOpen()).thenReturn(true);
        RunRecoveryService service = new RunRecoveryService(
                runRepository, auditTrailService,
                new RuntimeLimits(1, 1, 1, BigDecimal.ONE, 1, 1, 1, 1, 1),
                metrics, messageWriter, entityManagerFactory);

        service.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
        service.recoverStaleRuns();

        verifyNoInteractions(runRepository, auditTrailService, metrics, messageWriter);
    }

    @Test
    void shouldSkipScheduledRecoveryAfterBeanDestructionStarts() {
        RunRepository runRepository = mock(RunRepository.class);
        AuditTrailService auditTrailService = mock(AuditTrailService.class);
        HarnessMetrics metrics = mock(HarnessMetrics.class);
        ConversationMessageWriter messageWriter = mock(ConversationMessageWriter.class);
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        org.mockito.Mockito.when(entityManagerFactory.isOpen()).thenReturn(true);
        RunRecoveryService service = new RunRecoveryService(
                runRepository, auditTrailService,
                new RuntimeLimits(1, 1, 1, BigDecimal.ONE, 1, 1, 1, 1, 1),
                metrics, messageWriter, entityManagerFactory);

        service.shutdown();
        service.recoverStaleRuns();

        verifyNoInteractions(runRepository, auditTrailService, metrics, messageWriter);
    }
}
