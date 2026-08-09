package org.mingharness.education;

import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationRunOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 将创建请求解析为可审计、可复现的教育执行快照。 */
@Service
public class EducationRunConfigurationService {

    private final LearnerProfileRepository profileRepository;
    private final LearnerMasteryRepository masteryRepository;
    private final SensitiveDataSanitizer sanitizer;

    public EducationRunConfigurationService(LearnerProfileRepository profileRepository,
                                            LearnerMasteryRepository masteryRepository,
                                            SensitiveDataSanitizer sanitizer) {
        this.profileRepository = profileRepository;
        this.masteryRepository = masteryRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public EducationRunConfiguration resolve(String tenantId, String userId, EducationRunOptions options) {
        if (options == null || !options.isEnabled()) {
            return EducationRunConfiguration.disabled();
        }
        LearnerProfile profile = resolveProfile(tenantId, userId, options.learnerProfileId());
        String subject = firstNonBlank(options.subject(), profile.getSubject());
        String gradeLevel = firstNonBlank(options.gradeLevel(), profile.getGradeLevel());
        String curriculumVersion = firstNonBlank(options.curriculumVersion(), profile.getCurriculumVersion());
        if (subject == null || gradeLevel == null || curriculumVersion == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EDUCATION_CONTEXT_INCOMPLETE",
                    "教育 Agent 必须明确学科、年级和课程版本");
        }
        Integer minDifficulty = bound(options.minDifficulty());
        Integer maxDifficulty = bound(options.maxDifficulty());
        if (minDifficulty != null && maxDifficulty != null && minDifficulty > maxDifficulty) {
            int temporary = minDifficulty;
            minDifficulty = maxDifficulty;
            maxDifficulty = temporary;
        }
        return new EducationRunConfiguration(true, profile.getId(), clean(subject), clean(gradeLevel),
                clean(curriculumVersion), clean(options.conceptKey()), minDifficulty, maxDifficulty,
                options.effectivePedagogicalMode(), masterySummary(tenantId, profile.getId()));
    }

    private LearnerProfile resolveProfile(String tenantId, String userId, String profileId) {
        if (profileId != null && !profileId.isBlank()) {
            return profileRepository.findByIdAndTenantIdAndUserId(profileId.trim(), tenantId, userId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "LEARNER_PROFILE_NOT_FOUND", "指定的学习者画像不存在"));
        }
        return profileRepository.findTop1ByTenantIdAndUserIdAndActiveTrueOrderByUpdatedAtDesc(tenantId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "LEARNER_PROFILE_REQUIRED", "启用教育 Agent 前请先创建学习者画像"));
    }

    private String masterySummary(String tenantId, String profileId) {
        List<LearnerMastery> mastery = masteryRepository
                .findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc(tenantId, profileId);
        if (mastery.isEmpty()) return "暂无掌握度记录";
        return mastery.stream()
                .limit(40)
                .map(item -> item.getConceptKey() + "=" + String.format(Locale.ROOT, "%.2f", item.getMasteryScore()))
                .collect(Collectors.joining(", "));
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? clean(fallback) : clean(first);
    }

    private String clean(String value) {
        String result = sanitizer.sanitize(value == null ? "" : value.trim());
        return result.isBlank() ? null : result;
    }

    private Integer bound(Integer value) {
        return value == null ? null : Math.max(1, Math.min(5, value));
    }
}
