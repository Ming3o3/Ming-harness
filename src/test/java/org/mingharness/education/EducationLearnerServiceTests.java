package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.LearnerProfileRequest;
import org.mingharness.education.api.MasteryUpdateRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EducationLearnerServiceTests {

    @Test
    void shouldCreateProfileAndUpdateMasteryWithSmoothedObservation() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        when(profiles.findByTenantIdAndUserIdAndSubjectAndGradeLevelAndCurriculumVersion(
                "tenant-a", "student-1", "数学", "高中一年级", "人教A版"))
                .thenReturn(Optional.empty());
        when(profiles.save(any(LearnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", "profile-1", "函数")).thenReturn(Optional.empty());
        when(mastery.save(any(LearnerMastery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EducationLearnerService service = new EducationLearnerService(profiles, mastery,
                new SensitiveDataSanitizer());
        LearnerProfile profile = service.upsertProfile("tenant-a", "student-1",
                new LearnerProfileRequest("数学", "高中一年级", "人教A版", "掌握函数基础", null));

        // 用固定 ID 模拟持久化返回值，后续掌握度更新应绑定到画像而不是用户裸 ID。
        var profileId = profile.getId();
        when(profiles.findByIdAndTenantIdAndUserId(profileId, "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        LearnerMastery existing = new LearnerMastery("tenant-a", profileId, "函数", 0.4, 2, 1);
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", profileId, "函数")).thenReturn(Optional.of(existing));

        LearnerMastery updated = service.recordObservedMastery("tenant-a", "student-1", profileId,
                new MasteryUpdateRequest("函数", 1.0, true, null, null));

        assertEquals(profileId, updated.getLearnerProfileId());
        assertTrue(updated.getMasteryScore() > 0.4 && updated.getMasteryScore() < 1.0);
        assertEquals(3, updated.getAttempts());
        assertEquals(2, updated.getCorrectAttempts());
    }

    @Test
    void shouldCompleteActiveGoalsWhenAnyMasteryWriteReachesTarget() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", "掌握函数", "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数", "函数", 0.2, 0.6);

        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(java.util.Optional.of(profile));
        LearnerMastery existing = new LearnerMastery("tenant-a", profile.getId(), "函数", 0.5, 1, 0);
        when(mastery.findByTenantIdAndLearnerProfileIdAndConceptKey(
                "tenant-a", profile.getId(), "函数")).thenReturn(java.util.Optional.of(existing));
        when(mastery.save(any(LearnerMastery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(goals.findByTenantIdAndUserIdAndLearnerProfileIdAndConceptKeyIgnoreCase(
                "tenant-a", "student-1", profile.getId(), "函数"))
                .thenReturn(java.util.List.of(goal));
        when(goals.save(any(LearningGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EducationLearnerService service = new EducationLearnerService(profiles, mastery, goals,
                new SensitiveDataSanitizer());
        service.recordObservedMastery("tenant-a", "student-1", profile.getId(),
                new MasteryUpdateRequest("函数", 1.0, true, null, null));

        assertEquals(LearningGoalStatus.COMPLETED, goal.getStatus());
        verify(goals).save(goal);
    }

    @Test
    void shouldRejectDirectCalibrationWhileAnActiveGoalExists() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", "掌握函数", "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数", "函数", 0.2, 0.8);
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(goals.findByTenantIdAndUserIdAndLearnerProfileIdAndConceptKeyIgnoreCase(
                "tenant-a", "student-1", profile.getId(), "函数"))
                .thenReturn(java.util.List.of(goal));

        EducationLearnerService service = new EducationLearnerService(profiles, mastery, goals,
                new SensitiveDataSanitizer());
        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                org.mingharness.common.BusinessException.class,
                () -> service.updateMastery("tenant-a", "student-1", profile.getId(),
                        new MasteryUpdateRequest("函数", 1.0, null, null, null)));

        assertEquals("MASTERY_EVIDENCE_REQUIRED", exception.getCode());
    }

    @Test
    void shouldRejectObservationOnDirectMasteryWrite() {
        EducationLearnerService service = new EducationLearnerService(
                mock(LearnerProfileRepository.class), mock(LearnerMasteryRepository.class),
                new SensitiveDataSanitizer());

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                org.mingharness.common.BusinessException.class,
                () -> service.updateMastery("tenant-a", "student-1", "profile-1",
                        new MasteryUpdateRequest("函数", 1.0, true, null, null)));

        assertEquals("MASTERY_EVIDENCE_REQUIRED", exception.getCode());
    }
}
