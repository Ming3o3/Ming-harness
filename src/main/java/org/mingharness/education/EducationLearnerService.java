package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearnerProfileRequest;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

/** 学习者画像和知识点掌握度服务，所有查询都绑定租户和当前用户。 */
@Service
public class EducationLearnerService {

    private final LearnerProfileRepository profileRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final LearningGoalRepository goalRepository;
    private final LearningReviewPlanService reviewPlanService;
    private final LearningAssignmentCompletionService assignmentCompletionService;
    private final SensitiveDataSanitizer sanitizer;

    /** 兼容不启用学习目标存储的组件测试和旧扩展调用方。 */
    public EducationLearnerService(LearnerProfileRepository profileRepository,
                                   LearnerMasteryRepository masteryRepository,
                                   SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, null, null, null, sanitizer);
    }

    /** 兼容已有学习目标服务测试和旧扩展调用方。 */
    public EducationLearnerService(LearnerProfileRepository profileRepository,
                                   LearnerMasteryRepository masteryRepository,
                                   LearningGoalRepository goalRepository,
                                   SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, null, null, sanitizer);
    }

    public EducationLearnerService(LearnerProfileRepository profileRepository,
                                   LearnerMasteryRepository masteryRepository,
                                   LearningGoalRepository goalRepository,
                                   LearningReviewPlanService reviewPlanService,
                                   SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, reviewPlanService, null, sanitizer);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationLearnerService(LearnerProfileRepository profileRepository,
                                   LearnerMasteryRepository masteryRepository,
                                   LearningGoalRepository goalRepository,
                                   LearningReviewPlanService reviewPlanService,
                                   LearningAssignmentCompletionService assignmentCompletionService,
                                   SensitiveDataSanitizer sanitizer) {
        this.profileRepository = profileRepository;
        this.masteryRepository = masteryRepository;
        this.goalRepository = goalRepository;
        this.reviewPlanService = reviewPlanService;
        this.assignmentCompletionService = assignmentCompletionService;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public LearnerProfile upsertProfile(String tenantId, String userId, LearnerProfileRequest request) {
        String subject = clean(request.subject());
        String gradeLevel = clean(request.gradeLevel());
        String curriculumVersion = clean(request.curriculumVersion());
        LearnerProfile profile = profileRepository
                .findByTenantIdAndUserIdAndSubjectAndGradeLevelAndCurriculumVersion(
                        tenantId, userId, subject, gradeLevel, curriculumVersion)
                .orElseGet(() -> new LearnerProfile(tenantId, userId, subject, gradeLevel,
                        curriculumVersion, clean(request.learningGoal()), clean(request.language())));
        profile.update(subject, gradeLevel, curriculumVersion, clean(request.learningGoal()),
                clean(request.language()));
        profile.activate();
        return profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<LearnerProfile> listProfiles(String tenantId, String userId) {
        // 已删除的画像保留在数据库中，以便历史 Run、测评和学习目标继续可追溯，
        // 但不再出现在学习者的可选画像列表里。重新保存相同课程组合时会重新激活它。
        return profileRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId).stream()
                .filter(LearnerProfile::isActive)
                .toList();
    }

    /**
     * 删除画像采用可恢复的归档语义：画像会从当前学习上下文中移除，但不会破坏已经
     * 绑定它的历史 Run、掌握度、目标和作业记录。
     */
    @Transactional
    public void deleteProfile(String tenantId, String userId, String profileId) {
        LearnerProfile profile = profileRepository.findByIdAndTenantIdAndUserId(profileId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在"));
        if (!profile.isActive()) return;
        profile.deactivate();
        profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public LearnerProfile activeProfile(String tenantId, String userId) {
        return profileRepository.findTop1ByTenantIdAndUserIdAndActiveTrueOrderByUpdatedAtDesc(tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "当前用户还没有激活的学习者画像"));
    }

    @Transactional
    public LearnerMastery updateMastery(String tenantId, String userId, String profileId,
                                        MasteryUpdateRequest request) {
        if (request == null || request.isObservation()) {
            throw new BusinessException(HttpStatus.CONFLICT, "MASTERY_EVIDENCE_REQUIRED",
                    "答题观察必须通过绑定教育 Run 的测评接口提交，不能直接写入掌握度");
        }
        LearnerProfile profile = profileRepository.findByIdAndTenantIdAndUserId(profileId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在"));
        String conceptKey = clean(request.conceptKey());
        rejectCalibrationDuringActiveGoal(tenantId, userId, profileId, conceptKey);
        LearnerMastery mastery = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(tenantId, profile.getId(), conceptKey)
                .orElseGet(() -> new LearnerMastery(tenantId, profile.getId(), conceptKey,
                        request.effectiveMasteryScore(), 0, 0));
        if (request.masteryScore() != null) {
            mastery.setMastery(request.effectiveMasteryScore(),
                    request.attempts() == null ? mastery.getAttempts() : request.attempts(),
                    request.correctAttempts() == null ? mastery.getCorrectAttempts() : request.correctAttempts());
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MASTERY_UPDATE_REQUIRED",
                    "掌握度更新必须提供 masteryScore 或 correct");
        }
        LearnerMastery saved = masteryRepository.save(mastery);
        return saved;
    }

    /**
     * 只有经过 Run 绑定和证据校验的测评才可以推进目标与课程作业。
     * 普通画像写入口不能伪造一次答题事实。
     */
    @Transactional
    public LearnerMastery recordObservedMastery(String tenantId, String userId, String profileId,
                                                MasteryUpdateRequest request) {
        if (request == null || !request.isObservation()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MASTERY_OBSERVATION_REQUIRED",
                    "形成性测评必须提供答题观察结果");
        }
        LearnerProfile profile = profileRepository.findByIdAndTenantIdAndUserId(profileId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在"));
        String conceptKey = clean(request.conceptKey());
        LearnerMastery mastery = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(tenantId, profile.getId(), conceptKey)
                .orElseGet(() -> new LearnerMastery(tenantId, profile.getId(), conceptKey,
                        0.0, 0, 0));
        mastery.recordAssessment(Boolean.TRUE.equals(request.correct()), request.effectiveMasteryScore());
        LearnerMastery saved = masteryRepository.save(mastery);
        completeEligibleGoals(tenantId, userId, profile.getId(), conceptKey, saved.getMasteryScore());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LearnerMastery> listMastery(String tenantId, String userId, String profileId) {
        profileRepository.findByIdAndTenantIdAndUserId(profileId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在"));
        return masteryRepository.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc(tenantId, profileId);
    }

    /** 返回当前画像的掌握度快照，供依赖图预览和检索解释使用。 */
    @Transactional(readOnly = true)
    public Map<String, Double> masteryScores(String tenantId, String userId, String profileId) {
        String effectiveProfileId = profileId;
        if (effectiveProfileId == null || effectiveProfileId.isBlank()) {
            effectiveProfileId = profileRepository
                    .findTop1ByTenantIdAndUserIdAndActiveTrueOrderByUpdatedAtDesc(tenantId, userId)
                    .map(LearnerProfile::getId)
                    .orElse(null);
        }
        if (effectiveProfileId == null || effectiveProfileId.isBlank()) return Map.of();
        profileRepository.findByIdAndTenantIdAndUserId(effectiveProfileId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在"));
        Map<String, Double> scores = new LinkedHashMap<>();
        masteryRepository.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc(
                        tenantId, effectiveProfileId)
                .forEach(item -> scores.put(item.getConceptKey(), item.getMasteryScore()));
        return Map.copyOf(scores);
    }

    /** 返回当前用户可见的画像；没有画像时返回空，供课程知识图预览使用。 */
    @Transactional(readOnly = true)
    public Optional<LearnerProfile> profileFor(String tenantId, String userId, String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return profileRepository.findTop1ByTenantIdAndUserIdAndActiveTrueOrderByUpdatedAtDesc(tenantId, userId);
        }
        return Optional.of(profileRepository.findByIdAndTenantIdAndUserId(profileId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在")));
    }

    private String clean(String value) {
        return sanitizer.sanitize(value == null ? "" : value.trim());
    }

    /**
     * 所有掌握度写入口共享同一条目标状态投影规则，避免“掌握度已达标但目标仍进行中”。
     * 形成性测评服务仍会在保存测评事实后再次确认状态，以保证旧扩展实现也保持兼容。
     */
    private void completeEligibleGoals(String tenantId, String userId, String profileId,
                                       String conceptKey, double masteryScore) {
        if (goalRepository == null) return;
        goalRepository.findByTenantIdAndUserIdAndLearnerProfileIdAndConceptKeyIgnoreCase(
                        tenantId, userId, profileId, conceptKey)
                .stream()
                .filter(goal -> goal.getStatus() == LearningGoalStatus.ACTIVE)
                .filter(goal -> masteryScore >= goal.getTargetMastery())
                .forEach(goal -> {
                    goal.changeStatus(LearningGoalStatus.COMPLETED);
                    if (reviewPlanService != null) {
                        reviewPlanService.ensureForCompletedGoal(goal);
                    }
                    goalRepository.save(goal);
                    if (assignmentCompletionService != null) {
                        assignmentCompletionService.completeForGoal(tenantId, userId, goal.getId(),
                                java.time.Instant.now());
                    }
                });
    }

    private void rejectCalibrationDuringActiveGoal(String tenantId, String userId,
                                                    String profileId, String conceptKey) {
        if (goalRepository == null) return;
        boolean activeGoal = goalRepository
                .findByTenantIdAndUserIdAndLearnerProfileIdAndConceptKeyIgnoreCase(
                        tenantId, userId, profileId, conceptKey)
                .stream()
                .anyMatch(goal -> goal.getStatus() == LearningGoalStatus.ACTIVE);
        if (activeGoal) {
            throw new BusinessException(HttpStatus.CONFLICT, "MASTERY_EVIDENCE_REQUIRED",
                    "进行中的学习目标必须通过绑定 Run 的测评证据更新，不能直接校准掌握度");
        }
    }
}
