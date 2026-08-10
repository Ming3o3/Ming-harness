package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearningAssignmentAcceptView;
import org.mingharness.education.api.LearningAssignmentRequest;
import org.mingharness.education.api.LearningAssignmentView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** 管理课程作业入口，并把学习者接受作业转换为画像和结构化学习目标。 */
@Service
public class LearningAssignmentService {

    private final LearningAssignmentRepository assignmentRepository;
    private final LearnerProfileRepository profileRepository;
    private final LearningGoalRepository goalRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final SensitiveDataSanitizer sanitizer;
    private final LearningAssignmentNotificationService notificationService;

    /** 保留旧构造器，方便已有单元测试和嵌入式调用；生产环境由 Spring 使用带通知服务的构造器。 */
    public LearningAssignmentService(LearningAssignmentRepository assignmentRepository,
                                     LearnerProfileRepository profileRepository,
                                     LearningGoalRepository goalRepository,
                                     LearnerMasteryRepository masteryRepository,
                                     SensitiveDataSanitizer sanitizer) {
        this(assignmentRepository, profileRepository, goalRepository, masteryRepository, sanitizer, null);
    }

    @Autowired
    public LearningAssignmentService(LearningAssignmentRepository assignmentRepository,
                                     LearnerProfileRepository profileRepository,
                                     LearningGoalRepository goalRepository,
                                     LearnerMasteryRepository masteryRepository,
                                     SensitiveDataSanitizer sanitizer,
                                     LearningAssignmentNotificationService notificationService) {
        this.assignmentRepository = assignmentRepository;
        this.profileRepository = profileRepository;
        this.goalRepository = goalRepository;
        this.masteryRepository = masteryRepository;
        this.sanitizer = sanitizer;
        this.notificationService = notificationService;
    }

    @Transactional
    public LearningAssignment create(String tenantId, String teacherUserId,
                                     LearningAssignmentRequest request) {
        String learnerUserId = clean(request.learnerUserId());
        if (learnerUserId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_LEARNER_REQUIRED",
                    "布置作业时必须指定学习者");
        }
        LearningAssignment saved = assignmentRepository.save(new LearningAssignment(
                tenantId, teacherUserId, learnerUserId,
                clean(request.title()), clean(request.instructions()), clean(request.subject()),
                clean(request.gradeLevel()), clean(request.curriculumVersion()),
                clean(request.conceptKey()), request.effectiveTargetMastery(), request.dueAt()));
        notifyState(saved);
        return saved;
    }

    @Transactional
    public List<LearningAssignment> list(String tenantId, String userId) {
        expireOverdue(tenantId, Instant.now(), 100);
        LinkedHashMap<String, LearningAssignment> merged = new LinkedHashMap<>();
        assignmentRepository.findByTenantIdAndTeacherUserIdOrderByCreatedAtDesc(tenantId, userId)
                .forEach(item -> merged.put(item.getId(), item));
        assignmentRepository.findByTenantIdAndLearnerUserIdOrderByCreatedAtDesc(tenantId, userId)
                .forEach(item -> merged.put(item.getId(), item));
        return new ArrayList<>(merged.values()).stream()
                .sorted(Comparator.comparing(LearningAssignment::getCreatedAt).reversed())
                .toList();
    }

    @Transactional
    public LearningAssignment getForParticipant(String tenantId, String userId, String assignmentId) {
        LearningAssignment assignment = assignmentRepository.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNING_ASSIGNMENT_NOT_FOUND", "课程作业不存在"));
        if (!userId.equals(assignment.getTeacherUserId())
                && !userId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_ACCESS_DENIED",
                    "无权访问该课程作业");
        }
        if (assignment.isOverdue(Instant.now())) {
            assignment.markOverdue(Instant.now());
            assignmentRepository.save(assignment);
            notifyState(assignment);
        }
        return assignment;
    }

    @Transactional
    public LearningAssignmentAcceptView accept(String tenantId, String learnerUserId,
                                               String assignmentId) {
        LearningAssignment assignment = getForParticipant(tenantId, learnerUserId, assignmentId);
        if (!learnerUserId.equals(assignment.getLearnerUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_LEARNER_ONLY",
                    "只有被布置作业的学习者可以接受作业");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.ACCEPTED
                || assignment.getStatus() == LearningAssignmentStatus.AWAITING_EVIDENCE
                || assignment.getStatus() == LearningAssignmentStatus.COMPLETED) {
            return new LearningAssignmentAcceptView(LearningAssignmentView.from(assignment),
                    assignment.getLearnerProfileId(), assignment.getLearningGoalId());
        }
        if (assignment.getStatus() != LearningAssignmentStatus.ASSIGNED) {
            if (assignment.getStatus() == LearningAssignmentStatus.OVERDUE) {
                throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_OVERDUE",
                        "课程作业已逾期，不能再接受");
            }
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_NOT_ACCEPTABLE",
                    "当前作业不能接受");
        }

        LearnerProfile profile = profileRepository
                .findByTenantIdAndUserIdAndSubjectAndGradeLevelAndCurriculumVersion(
                        tenantId, learnerUserId, assignment.getSubject(), assignment.getGradeLevel(),
                        assignment.getCurriculumVersion())
                .orElseGet(() -> profileRepository.save(new LearnerProfile(
                        tenantId, learnerUserId, assignment.getSubject(), assignment.getGradeLevel(),
                        assignment.getCurriculumVersion(), assignment.getTitle(), "zh-CN")));
        double baseline = masteryRepository.findByTenantIdAndLearnerProfileIdAndConceptKey(
                        tenantId, profile.getId(), assignment.getConceptKey())
                .map(LearnerMastery::getMasteryScore).orElse(0.0);
        if (assignment.getTargetMastery() <= baseline) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_ALREADY_MASTERED",
                    "学习者当前掌握度已达到该作业目标，不能重复生成进行中的学习目标");
        }
        LearningGoal goal = goalRepository.save(new LearningGoal(
                tenantId, learnerUserId, profile.getId(), assignment.getTitle(),
                assignment.getConceptKey(), baseline, assignment.getTargetMastery()));
        assignment.accept(profile.getId(), goal.getId(), Instant.now());
        LearningAssignment saved = assignmentRepository.save(assignment);
        notifyState(saved);
        return new LearningAssignmentAcceptView(LearningAssignmentView.from(saved),
                profile.getId(), goal.getId());
    }

    @Transactional
    public LearningAssignment cancel(String tenantId, String teacherUserId, String assignmentId) {
        LearningAssignment assignment = getForParticipant(tenantId, teacherUserId, assignmentId);
        if (!teacherUserId.equals(assignment.getTeacherUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LEARNING_ASSIGNMENT_TEACHER_ONLY",
                    "只有布置者可以取消课程作业");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEARNING_ASSIGNMENT_ALREADY_COMPLETED",
                    "已完成的课程作业不能取消");
        }
        if (assignment.getStatus() == LearningAssignmentStatus.CANCELLED) return assignment;
        assignment.cancel();
        LearningAssignment saved = assignmentRepository.save(assignment);
        notifyState(saved);
        return saved;
    }

    /** 后台和查询入口共同调用，确保作业不会永久停留在已布置/学习中。 */
    @Transactional
    public int expireOverdue(Instant reference, int limit) {
        return expireOverdue(null, reference, limit);
    }

    @Transactional
    public int expireOverdue(String tenantId, Instant reference, int limit) {
        Instant now = reference == null ? Instant.now() : reference;
        int boundedLimit = Math.max(1, Math.min(500, limit));
        List<LearningAssignment> candidates = tenantId == null
                ? assignmentRepository.findByStatusInAndDueAtLessThanEqualOrderByDueAtAsc(
                List.of(LearningAssignmentStatus.ASSIGNED, LearningAssignmentStatus.ACCEPTED),
                now, PageRequest.of(0, boundedLimit))
                : assignmentRepository.findByTenantIdAndStatusInAndDueAtLessThanEqualOrderByDueAtAsc(
                tenantId, List.of(LearningAssignmentStatus.ASSIGNED, LearningAssignmentStatus.ACCEPTED),
                now, PageRequest.of(0, boundedLimit));
        int changed = 0;
        for (LearningAssignment assignment : candidates) {
            if (!assignment.isOverdue(now)) continue;
            assignment.markOverdue(now);
            assignmentRepository.save(assignment);
            notifyState(assignment);
            changed++;
        }
        return changed;
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    private void notifyState(LearningAssignment assignment) {
        if (notificationService != null) notificationService.ensureForState(assignment);
    }
}
