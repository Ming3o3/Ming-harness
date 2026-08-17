package org.mingharness.education;

import org.mingharness.common.SensitiveDataSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearnerStateAuditServiceTests {

    @Test
    void recordsBeforeAfterRunAndBehaviorEvidence() {
        LearnerStateTransitionRepository repository = mock(LearnerStateTransitionRepository.class);
        when(repository.save(any(LearnerStateTransition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LearnerStateAuditService service = new LearnerStateAuditService(repository,
                new SensitiveDataSanitizer());
        LearnerMastery before = new LearnerMastery("tenant-a", "profile-1", "循环", 0.35, 2, 1);
        LearnerMastery after = new LearnerMastery("tenant-a", "profile-1", "循环", 0.56, 3, 2);

        LearnerStateTransition saved = service.record("tenant-a", "student-1", "profile-1", "循环",
                before, after, LearnerStateTransitionContext.code(
                        "run-1", "行为测试通过", "OUTPUT_MISMATCH", 0.75));

        assertNotNull(saved);
        assertEquals("run-1", saved.getRunId());
        assertEquals(0.35, saved.getBeforeMastery());
        assertEquals(0.56, saved.getAfterMastery());
        assertEquals("CODE_EVALUATION", saved.getEvidenceSource());
        assertEquals("OUTPUT_MISMATCH", saved.getDiagnosticCategory());
        assertEquals(0.75, saved.getBehaviorTestPassRate());
        assertEquals(0.5, saved.getEvidenceWeight());
        verify(repository).save(any(LearnerStateTransition.class));
    }

    @Test
    void sanitizesSensitiveEvidenceAndCapsLongText() {
        LearnerStateTransitionRepository repository = mock(LearnerStateTransitionRepository.class);
        when(repository.save(any(LearnerStateTransition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LearnerStateAuditService service = new LearnerStateAuditService(repository,
                new SensitiveDataSanitizer());
        String longEvidence = "token=sk-12345678901234567890 " + "x".repeat(5000);
        LearnerMastery mastery = new LearnerMastery("tenant-a", "profile-1", "函数", 0.4, 1, 0);

        LearnerStateTransition saved = service.record("tenant-a", "student-1", "profile-1", "函数",
                mastery, mastery, new LearnerStateTransitionContext("run-2", "MODEL_TOOL", "FORMATIVE",
                        longEvidence, null, null, 3, 1.0, false, true));

        assertNotNull(saved.getEvidenceText());
        assertTrue(saved.getEvidenceText().length() <= 4000);
        assertTrue(saved.getEvidenceText().contains(SensitiveDataSanitizer.REDACTION_MARKER));
        assertTrue(!saved.getEvidenceText().contains("sk-12345678901234567890"));
    }

    @Test
    void listsProfileTransitionsWithOptionalConceptFilter() {
        LearnerStateTransitionRepository repository = mock(LearnerStateTransitionRepository.class);
        LearnerStateAuditService service = new LearnerStateAuditService(repository,
                new SensitiveDataSanitizer());
        when(repository.findTop200ByTenantIdAndLearnerProfileIdOrderByCreatedAtDesc("tenant-a", "profile-1"))
                .thenReturn(java.util.List.of());
        when(repository.findTop200ByTenantIdAndLearnerProfileIdAndConceptKeyOrderByCreatedAtDesc(
                "tenant-a", "profile-1", "函数")).thenReturn(java.util.List.of());

        assertEquals(0, service.listForProfile("tenant-a", "profile-1", null).size());
        assertEquals(0, service.listForProfile("tenant-a", "profile-1", "函数").size());
        verify(repository).findTop200ByTenantIdAndLearnerProfileIdOrderByCreatedAtDesc("tenant-a", "profile-1");
        verify(repository).findTop200ByTenantIdAndLearnerProfileIdAndConceptKeyOrderByCreatedAtDesc(
                "tenant-a", "profile-1", "函数");
    }
}
