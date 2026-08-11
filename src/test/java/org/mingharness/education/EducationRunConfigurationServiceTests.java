package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationRunOptions;
import org.mingharness.runtime.domain.Run;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationRunConfigurationServiceTests {

    @Test
    void shouldRejectEducationRunWithoutAVisibleMatchingCourseSource() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationKnowledgeService knowledge = mock(EducationKnowledgeService.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());
        when(knowledge.hasVisibleMatchingSource(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("student-1"),
                org.mockito.ArgumentMatchers.any(EducationRetrievalFilter.class)))
                .thenReturn(false);

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, null, null, null, null, null, null, knowledge,
                new SensitiveDataSanitizer());

        var exception = assertThrows(org.mingharness.common.BusinessException.class, () -> service.resolve(
                "tenant-a", "student-1", new EducationRunOptions(true, profile.getId(), null, null, null,
                        "函数", 2, 4, "PRACTICE")));

        assertEquals("EDUCATION_KNOWLEDGE_SOURCE_REQUIRED", exception.getCode());
    }

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

    @Test
    void shouldBindActiveLearningGoalAndFreezeItIntoRunConfiguration() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数基础", "函数", 0.35, 0.8);
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, goals, new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), goal.getId(), null, null,
                        null, null, null, null, "PRACTICE"));

        assertEquals(goal.getId(), configuration.learningGoalId());
        assertEquals("掌握函数基础", configuration.learningGoalTitle());
        assertEquals("函数", configuration.conceptKey());
        assertEquals(0.35, configuration.learningGoalBaselineMastery());
        assertEquals(0.8, configuration.learningGoalTargetMastery());
        assertTrue(configuration.promptSummary().contains("学习目标=掌握函数基础"));
    }

    @Test
    void shouldAllowACompletedGoalOnlyWhenItsReviewPlanIsDue() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningReviewPlanService reviewPlans = mock(LearningReviewPlanService.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数基础", "函数", 0.35, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", goal.getId(),
                profile.getId(), goal.getConceptKey(), java.time.Instant.now().minusSeconds(1));
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(reviewPlans.getById("tenant-a", "student-1", plan.getId())).thenReturn(plan);
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, goals, reviewPlans, new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), goal.getId(), plan.getId(), null, null,
                        null, null, null, null, "PRACTICE"));

        assertEquals(goal.getId(), configuration.learningGoalId());
        assertEquals(plan.getId(), configuration.reviewPlanId());
        assertEquals(profile.getId(), configuration.learnerProfileId());
    }

    @Test
    void shouldFreezeExplicitAssignmentAndRejectUnacceptedAssignmentRuns() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数基础", "函数", 0.35, 0.8);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                java.time.Instant.now().plusSeconds(3600));
        assignment.accept(profile.getId(), goal.getId(), java.time.Instant.now());
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, goals, null, assignments, new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), goal.getId(), assignment.getId(), null,
                        null, null, null, null, null, null, "PRACTICE"));

        assertEquals(assignment.getId(), configuration.learningAssignmentId());
        assertEquals(goal.getId(), configuration.learningGoalId());
        assertEquals("函数作业", configuration.learningAssignmentTitle());
        assertEquals("完成练习", configuration.learningAssignmentInstructions());
        assertTrue(configuration.promptSummary().contains("作业要求=完成练习"));
    }

    @Test
    void shouldDeriveAssignmentWhenRunSelectsItsBoundGoal() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数基础", "函数", 0.35, 0.8);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                java.time.Instant.now().plusSeconds(3600));
        assignment.accept(profile.getId(), goal.getId(), java.time.Instant.now());
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(assignments.findByTenantIdAndLearnerUserIdAndLearningGoalIdOrderByCreatedAtDesc(
                "tenant-a", "student-1", goal.getId())).thenReturn(List.of(assignment));
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, goals, null, assignments, new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), goal.getId(), null, null, null,
                        null, null, null, null, "PRACTICE"));

        assertEquals(assignment.getId(), configuration.learningAssignmentId());
    }

    @Test
    void shouldFreezeOpenTeacherInterventionIntoTheNextAssignmentRun() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearningAssignmentFeedbackRepository feedbacks = mock(LearningAssignmentFeedbackRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数基础", "函数", 0.35, 0.8);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                java.time.Instant.now().plusSeconds(3600));
        assignment.accept(profile.getId(), goal.getId(), java.time.Instant.now());
        LearningAssignmentFeedback feedback = new LearningAssignmentFeedback(
                "tenant-a", assignment.getId(), "teacher-1", "student-1",
                LearningAssignmentFeedbackAction.REQUEST_EVIDENCE,
                "请补充函数定义域的判定依据", null, java.time.Instant.now());
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());
        when(feedbacks.findByTenantIdAndLearningAssignmentIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(assignment.getId()),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(feedback));

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, goals, null, assignments, feedbacks, new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), goal.getId(), assignment.getId(), null,
                        null, null, null, null, null, null, "PRACTICE"));

        assertTrue(configuration.learningAssignmentInstructions().contains("教师当前干预"));
        assertTrue(configuration.promptSummary().contains("请补充函数定义域的判定依据"));
    }

    @Test
    void shouldFreezeTeacherRevisionNoteIntoTheNextAssignmentRun() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        LearningGoalRepository goals = mock(LearningGoalRepository.class);
        LearningAssignmentRepository assignments = mock(LearningAssignmentRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        LearningGoal goal = new LearningGoal("tenant-a", "student-1", profile.getId(),
                "掌握函数基础", "函数", 0.35, 0.8);
        goal.changeStatus(LearningGoalStatus.COMPLETED);
        LearningAssignment assignment = new LearningAssignment("tenant-a", "teacher-1", "student-1",
                "函数作业", "完成练习", "数学", "高中一年级", "人教A版", "函数", 0.8,
                java.time.Instant.now().plusSeconds(3600));
        assignment.accept(profile.getId(), goal.getId(), java.time.Instant.now());
        assignment.complete(java.time.Instant.now());
        goal.requestRevision(java.time.Instant.now());
        assignment.returnForRevision("teacher-1", "请补充定义域判定依据", java.time.Instant.now());
        when(assignments.findByTenantIdAndId("tenant-a", assignment.getId()))
                .thenReturn(Optional.of(assignment));
        when(goals.findByIdAndTenantIdAndUserId(goal.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(goal));
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, goals, null, assignments, new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), goal.getId(), assignment.getId(), null,
                        null, null, null, null, null, null, "PRACTICE"));

        assertEquals("请补充定义域判定依据", configuration.learningAssignmentTeacherReviewNote());
        assertTrue(configuration.promptSummary().contains("教师返工说明=请补充定义域判定依据"));
        Run run = new Run("tenant-a", "student-1", "函数作业", "返工",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        run.attachEducationConfiguration(configuration);
        assertEquals("请补充定义域判定依据",
                run.educationConfiguration().learningAssignmentTeacherReviewNote());
    }

    @Test
    void shouldFreezeAnActiveEnrolledCourseIntoDirectLearningRun() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "MATH-101",
                "函数基础", "数学", "高中一年级", "人教A版");
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(mastery.findByTenantIdAndLearnerProfileIdOrderByConceptKeyAsc("tenant-a", profile.getId()))
                .thenReturn(List.of());
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(enrollments.existsByTenantIdAndCourseIdAndLearnerUserIdAndStatus(
                "tenant-a", course.getId(), "student-1", EducationEnrollmentStatus.ACTIVE))
                .thenReturn(true);

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, null, null, null, null, courses, enrollments,
                new SensitiveDataSanitizer());
        EducationRunConfiguration configuration = service.resolve("tenant-a", "student-1",
                new EducationRunOptions(true, profile.getId(), null, null, null,
                        "数学", "高中一年级", "人教A版", "函数", null, null,
                        "PRACTICE", course.getId()));

        assertEquals(course.getId(), configuration.courseId());
        assertEquals("MATH-101", configuration.courseCode());
        assertEquals("函数基础", configuration.courseTitle());
        assertTrue(configuration.promptSummary().contains("课程实例=MATH-101 · 函数基础"));

        Run run = new Run("tenant-a", "student-1", "函数学习", "开始",
                BigDecimal.ONE, "demo-model", "prompt-v1", "policy-v1");
        run.attachEducationConfiguration(configuration);
        assertEquals(course.getId(), run.getEducationCourseId());
        assertEquals("MATH-101", run.educationConfiguration().courseCode());
    }

    @Test
    void shouldRejectDirectLearningRunWhenLearnerIsNotEnrolledInCourse() {
        LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
        LearnerMasteryRepository mastery = mock(LearnerMasteryRepository.class);
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        LearnerProfile profile = new LearnerProfile("tenant-a", "student-1", "数学",
                "高中一年级", "人教A版", null, "zh-CN");
        EducationCourse course = new EducationCourse("tenant-a", "teacher-1", "MATH-101",
                "函数基础", "数学", "高中一年级", "人教A版");
        when(profiles.findByIdAndTenantIdAndUserId(profile.getId(), "tenant-a", "student-1"))
                .thenReturn(Optional.of(profile));
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(enrollments.existsByTenantIdAndCourseIdAndLearnerUserIdAndStatus(
                "tenant-a", course.getId(), "student-1", EducationEnrollmentStatus.ACTIVE))
                .thenReturn(false);

        EducationRunConfigurationService service = new EducationRunConfigurationService(
                profiles, mastery, null, null, null, null, courses, enrollments,
                new SensitiveDataSanitizer());
        var exception = assertThrows(org.mingharness.common.BusinessException.class, () -> service.resolve(
                "tenant-a", "student-1", new EducationRunOptions(true, profile.getId(), null,
                        null, null, "数学", "高中一年级", "人教A版", "函数", null, null,
                        "AUTO", course.getId())));

        assertEquals("EDUCATION_COURSE_ENROLLMENT_REQUIRED", exception.getCode());
    }
}
