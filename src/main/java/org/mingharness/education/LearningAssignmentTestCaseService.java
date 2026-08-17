package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentTestCaseRequest;
import org.mingharness.education.api.LearningAssignmentTestCaseView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 管理教师配置的编程行为测试用例，并在 Run 创建前提供确定性快照。 */
@Service
public class LearningAssignmentTestCaseService {

    private final LearningAssignmentService assignmentService;
    private final LearningAssignmentTestCaseRepository repository;
    private final SensitiveDataSanitizer sanitizer;

    public LearningAssignmentTestCaseService(LearningAssignmentService assignmentService,
                                             LearningAssignmentTestCaseRepository repository,
                                             SensitiveDataSanitizer sanitizer) {
        this.assignmentService = assignmentService;
        this.repository = repository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public LearningAssignmentTestCaseView create(String tenantId, String teacherUserId,
                                                 String assignmentId,
                                                 LearningAssignmentTestCaseRequest request) {
        LearningAssignment assignment = assignmentService.getForParticipant(
                tenantId, teacherUserId, assignmentId);
        if (!teacherUserId.equals(assignment.getTeacherUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_TEACHER_ONLY",
                    "只有布置者可以配置编程测试用例");
        }
        if (assignment.getStatus() != LearningAssignmentStatus.ASSIGNED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_TEST_CASES_FROZEN",
                    "作业被学习者接受后不能修改测试用例，请新建作业版本");
        }
        String caseKey = clean(request == null ? null : request.caseKey());
        if (repository.findByTenantIdAndLearningAssignmentIdAndCaseKey(
                tenantId, assignmentId, caseKey).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "ASSIGNMENT_TEST_CASE_DUPLICATE",
                    "同一作业中不能重复使用测试用例标识");
        }
        String input = rawTestData(request == null ? null : request.input(), "测试输入");
        String expectedOutput = rawTestData(request == null ? null : request.expectedOutput(), "期望输出");
        LearningAssignmentTestCase saved = repository.save(new LearningAssignmentTestCase(
                tenantId, assignmentId, caseKey,
                cleanNullable(request == null ? null : request.conceptKey()),
                cleanNullable(request == null ? null : request.name()), input, expectedOutput,
                request != null && request.hidden(), request == null ? 1.0 : request.effectiveWeight(),
                request == null ? 0 : request.effectiveSequence()));
        return LearningAssignmentTestCaseView.forTeacher(saved);
    }

    @Transactional(readOnly = true)
    public List<LearningAssignmentTestCaseView> list(String tenantId, String userId,
                                                     String assignmentId, boolean teacherView) {
        LearningAssignment assignment = assignmentService.getForParticipant(tenantId, userId, assignmentId);
        if (teacherView && !userId.equals(assignment.getTeacherUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_TEACHER_ONLY",
                    "只有布置者可以查看测试用例答案");
        }
        return repository.findByTenantIdAndLearningAssignmentIdAndEnabledTrueOrderBySequenceAscCreatedAtAsc(
                        tenantId, assignmentId).stream()
                .filter(item -> teacherView || !item.isHidden())
                .map(item -> teacherView
                        ? LearningAssignmentTestCaseView.forTeacher(item)
                        : LearningAssignmentTestCaseView.forLearner(item))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LearningAssignmentTestCaseView> listForGovernance(String tenantId, String assignmentId) {
        assignmentService.getForGovernance(tenantId, assignmentId);
        return repository.findByTenantIdAndLearningAssignmentIdAndEnabledTrueOrderBySequenceAscCreatedAtAsc(
                        tenantId, assignmentId).stream()
                .map(LearningAssignmentTestCaseView::forTeacher)
                .toList();
    }

    @Transactional(readOnly = true)
    public String snapshot(String tenantId, String assignmentId) {
        return EducationProgrammingTestCaseSnapshotCodec.encode(
                repository.findByTenantIdAndLearningAssignmentIdAndEnabledTrueOrderBySequenceAscCreatedAtAsc(
                        tenantId, assignmentId));
    }

    private String rawTestData(String value, String label) {
        String normalized = value == null ? "" : value;
        if (sanitizer.containsSensitiveData(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_TEST_CASE_SENSITIVE_DATA",
                    label + "疑似包含凭证或敏感配置，请删除后重试");
        }
        return normalized;
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private String cleanNullable(String value) {
        String normalized = clean(value);
        return normalized.isBlank() ? null : normalized;
    }
}
