package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationRunOptions;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationRunConfigurationServiceTests {

    @Test
    void shouldFreezeProfileDefaultsAndLearnerMasteryIntoRunConfiguration() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", "掌握函数", "zh-CN");
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        LearnerMastery function = new LearnerMastery("tenant-a", profile.getId(), "函数", 0.35, 3, 1);
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of(function));

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), null, null, null,
                        "函数", 2, 4, "SOCRATIC"));

        assertTrue(configuration.enabled());
        assertEquals("数学", configuration.subject());
        assertEquals("高中一年级", configuration.gradeLevel());
        assertEquals("人教A版", configuration.curriculumVersion());
        assertEquals("函数", configuration.conceptKey());
        assertEquals("SOCRATIC", configuration.pedagogicalMode());
        assertEquals("函数=0.35", configuration.learnerStateSummary());
        assertTrue(configuration.retrievalFilter().matches(new EducationKnowledgeSource(
                "tenant-a", "doc-1", "数学", "高中一年级", "人教A版", "第一章",
                "理解函数", "函数", "集合", 3, "TEXTBOOK")));
        assertEquals(0.35, configuration.retrievalFilter().masteryFor("函数"));
    }

    @Test
    void shouldRejectUnknownPedagogicalMode() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, new SensitiveDataSanitizer());

        var exception = assertThrows(org.mingharness.common.BusinessException.class, () -> service.resolve(
                "tenant-a", "student-1", new EducationRunOptions(true, profile.getId(),
                        null, null, null, "函数", null, null, "FREE_CHAT")));
        assertEquals("EDUCATION_PEDAGOGICAL_MODE_INVALID", exception.getCode());
    }
}
