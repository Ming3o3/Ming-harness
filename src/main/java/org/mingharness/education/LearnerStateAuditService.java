package org.mingharness.education;

import org.mingharness.common.SensitiveDataSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 将掌握度更新转成可审计状态转移，并提供受租户约束的查询。 */
@Service
public class LearnerStateAuditService {

    private static final int MAX_EVIDENCE_TEXT = 4000;

    private final LearnerStateTransitionRepository repository;
    private final SensitiveDataSanitizer sanitizer;

    public LearnerStateAuditService(LearnerStateTransitionRepository repository,
                                    SensitiveDataSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public LearnerStateTransition record(String tenantId, String learnerUserId,
                                         String learnerProfileId, String conceptKey,
                                         LearnerMastery before, LearnerMastery after,
                                         LearnerStateTransitionContext context) {
        if (repository == null || after == null) return null;
        LearnerStateTransitionContext safeContext = sanitizeContext(context);
        return repository.save(new LearnerStateTransition(tenantId, learnerUserId, learnerProfileId,
                conceptKey, before, after, safeContext, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<LearnerStateTransition> listForProfile(String tenantId, String learnerProfileId,
                                                       String conceptKey) {
        if (repository == null) return List.of();
        if (conceptKey == null || conceptKey.isBlank()) {
            return repository.findTop200ByTenantIdAndLearnerProfileIdOrderByCreatedAtDesc(
                    tenantId, learnerProfileId);
        }
        return repository.findTop200ByTenantIdAndLearnerProfileIdAndConceptKeyOrderByCreatedAtDesc(
                tenantId, learnerProfileId, conceptKey.trim());
    }

    @Transactional(readOnly = true)
    public List<LearnerStateTransition> listForRun(String tenantId, String runId) {
        if (repository == null) return List.of();
        return repository.findByTenantIdAndRunIdOrderByCreatedAtAsc(tenantId, runId);
    }

    private LearnerStateTransitionContext sanitizeContext(LearnerStateTransitionContext context) {
        LearnerStateTransitionContext value = context == null
                ? LearnerStateTransitionContext.manualCalibration() : context;
        String evidenceText = value.evidenceText();
        if (evidenceText != null) {
            evidenceText = sanitizer.sanitize(evidenceText);
            if (evidenceText.length() > MAX_EVIDENCE_TEXT) {
                evidenceText = evidenceText.substring(0, MAX_EVIDENCE_TEXT);
            }
        }
        return new LearnerStateTransitionContext(value.runId(), value.evidenceSource(),
                value.assessmentType(), evidenceText, value.diagnosticCategory(),
                value.behaviorTestPassRate(), value.difficultyLevel(), value.evidenceWeight(),
                value.hintUsed(), value.independentEvidence());
    }
}
