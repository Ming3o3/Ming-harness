package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearnerProfileRequest;
import org.mingharness.education.api.MasteryUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 学习者画像和知识点掌握度服务，所有查询都绑定租户和当前用户。 */
@Service
public class EducationLearnerService {

    private final LearnerProfileRepository profileRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final LearningGoalRepository goalRepository;
    private final LearningReviewPlanService reviewPlanService;
    private final SensitiveDataSanitizer sanitizer;

    /** 兼容不启用学习目标存储的组件测试和旧扩展调用方。 */
    public EducationLearnerService(LearnerProfileRepository profileRepository,
                                   LearnerMasteryRepository masteryRepository,
                                   SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, null, null, sanitizer);
    }

    /** 兼容已有学习目标服务测试和旧扩展调用方。 */
    public EducationLearnerService(LearnerProfileRepository profileRepository,
                                   LearnerMasteryRepository masteryRepository,
                                   LearningGoalRepository goalRepository,
                                   SensitiveDataSanitizer sanitizer) {
        this(profileRepository, masteryRepository, goalRepository, null, sanitizer);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EducationLearnerService(LearnerProfileRepository profileRepository,
                                   LearnerMasteryRepository masteryRepository,
                                   LearningGoalRepository goalRepository,
                                   LearningReviewPlanService reviewPlanService,
                                   SensitiveDataSanitizer sanitizer) {
        this.profileRepository = profileRepository;
        this.masteryRepository = masteryRepository;
        this.goalRepository = goalRepository;
        this.reviewPlanService = reviewPlanService;
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
        return profileRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId);
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
        LearnerProfile profile = profileRepository.findByIdAndTenantIdAndUserId(profileId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LEARNER_PROFILE_NOT_FOUND", "学习者画像不存在"));
        String conceptKey = clean(request.conceptKey());
        LearnerMastery mastery = masteryRepository
                .findByTenantIdAndLearnerProfileIdAndConceptKey(tenantId, profile.getId(), conceptKey)
                .orElseGet(() -> new LearnerMastery(tenantId, profile.getId(), conceptKey,
                        request.effectiveMasteryScore(), 0, 0));
        if (request.isObservation()) {
            mastery.recordAssessment(Boolean.TRUE.equals(request.correct()), request.effectiveMasteryScore());
        } else if (request.masteryScore() != null) {
            mastery.setMastery(request.effectiveMasteryScore(),
                    request.attempts() == null ? mastery.getAttempts() : request.attempts(),
                    request.correctAttempts() == null ? mastery.getCorrectAttempts() : request.correctAttempts());
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MASTERY_UPDATE_REQUIRED",
                    "掌握度更新必须提供 masteryScore 或 correct");
        }
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
                });
    }
}
