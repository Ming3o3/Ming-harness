package org.mingharness.feedback;

import org.mingharness.audit.AuditEvent;
import org.mingharness.audit.AuditTrailService;
import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.feedback.api.RunFeedbackRequest;
import org.mingharness.feedback.api.RunFeedbackView;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RunFeedbackService {

    private final RunFeedbackRepository feedbackRepository;
    private final RunRepository runRepository;
    private final AuditTrailService auditTrailService;
    private final SensitiveDataSanitizer sanitizer;

    public RunFeedbackService(RunFeedbackRepository feedbackRepository, RunRepository runRepository,
                              AuditTrailService auditTrailService, SensitiveDataSanitizer sanitizer) {
        this.feedbackRepository = feedbackRepository;
        this.runRepository = runRepository;
        this.auditTrailService = auditTrailService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public RunFeedbackView save(String runId, String tenantId, String userId, RunFeedbackRequest request) {
        Run run = runRepository.findById(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
        if (!tenantId.equals(run.getTenantId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "无权反馈其他组织的执行任务");
        }
        if (!userId.equals(run.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FEEDBACK_ACCESS_DENIED", "只能反馈自己发起的执行任务");
        }
        String rating = normalizeRating(request.rating());
        String reason = sanitizer.sanitize(normalize(request.reasonCode()));
        String note = sanitizer.sanitize(normalize(request.note()));
        RunFeedback feedback = feedbackRepository.findByRunIdAndUserId(runId, userId).orElse(null);
        if (feedback == null) {
            feedback = new RunFeedback(runId, sanitizer.sanitize(normalize(request.messageId())),
                    tenantId, userId, rating, reason, note);
        } else {
            feedback.update(sanitizer.sanitize(normalize(request.messageId())), rating, reason, note);
        }
        RunFeedbackView view = RunFeedbackView.from(feedbackRepository.save(feedback));
        auditTrailService.append(new AuditEvent(tenantId, userId, run.getTraceId(), runId, null,
                "RUN_FEEDBACK_RECORDED", "记录执行结果反馈: " + rating,
                "{\"rating\":\"" + rating + "\",\"reasonCode\":\"" + reason + "\"}"));
        return view;
    }

    @Transactional(readOnly = true)
    public RunFeedbackView get(String runId, String tenantId, String userId) {
        Run run = runRepository.findById(runId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "执行任务不存在: " + runId));
        if (!tenantId.equals(run.getTenantId()) || !userId.equals(run.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FEEDBACK_ACCESS_DENIED", "无权读取该执行任务反馈");
        }
        return feedbackRepository.findByRunIdAndUserId(runId, userId).map(RunFeedbackView::from).orElse(null);
    }

    private String normalizeRating(String value) {
        String normalized = normalize(value).toUpperCase(Locale.ROOT);
        if (!"POSITIVE".equals(normalized) && !"NEGATIVE".equals(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_RATING", "反馈类型只能是 POSITIVE 或 NEGATIVE");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
