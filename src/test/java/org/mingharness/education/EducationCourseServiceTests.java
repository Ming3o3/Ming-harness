package org.mingharness.education;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.education.api.EducationCourseRequest;
import org.mingharness.education.api.EducationCourseJoinRequest;
import org.mingharness.education.api.EducationEnrollmentRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EducationCourseServiceTests {

    @Test
    void shouldCreateCourseEnrollLearnerAndExposeActiveRosterCount() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse entity = new EducationCourse(
                "tenant-a", "teacher-1", "math-g1", "高一数学", "数学", "高中一年级", "人教A版");
        when(courses.findByTenantIdAndCode("tenant-a", "math-g1")).thenReturn(Optional.empty());
        when(courses.save(any(EducationCourse.class))).thenReturn(entity);
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                any(), any(), eq(EducationEnrollmentStatus.ACTIVE))).thenReturn(1L);
        when(enrollments.findByTenantIdAndCourseIdAndLearnerUserId(
                any(), any(), any())).thenReturn(Optional.empty());
        when(enrollments.save(any(EducationEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EducationCourseService service = new EducationCourseService(
                courses, enrollments, new SensitiveDataSanitizer());
        var course = service.create("tenant-a", "teacher-1",
                new EducationCourseRequest("math-g1", "高一数学", "数学", "高中一年级", "人教A版"));
        when(courses.findByTenantIdAndId("tenant-a", course.id())).thenReturn(Optional.of(entity));
        var enrolled = service.enroll("tenant-a", "teacher-1", course.id(),
                new EducationEnrollmentRequest("student-1"));

        assertEquals("ACTIVE", course.status());
        assertEquals("student-1", enrolled.learnerUserId());
        assertEquals(1L, service.getForParticipant("tenant-a", "teacher-1", course.id())
                .activeEnrollmentCount());
    }

    @Test
    void shouldGenerateCourseCodeWhenTeacherLeavesItBlank() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        when(courses.findByTenantIdAndCode(any(), any())).thenReturn(Optional.empty());
        when(courses.save(any(EducationCourse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                any(), any(), eq(EducationEnrollmentStatus.ACTIVE))).thenReturn(0L);

        var course = new EducationCourseService(courses, enrollments, new SensitiveDataSanitizer())
                .create("tenant-a", "teacher-1",
                        new EducationCourseRequest(null, "高一数学", "数学", "高中一年级", "人教A版"));

        assertEquals(15, course.code().length());
        assertTrue(course.code().startsWith("COURSE-"));
    }

    @Test
    void shouldRejectLearnerAccessAndEnrollmentAfterCourseIsArchived() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = new EducationCourse(
                "tenant-a", "teacher-1", "math-g1", "高一数学", "数学", "高中一年级", "人教A版");
        course.archive(null);
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));

        EducationCourseService service = new EducationCourseService(
                courses, enrollments, new SensitiveDataSanitizer());
        BusinessException archived = assertThrows(BusinessException.class,
                () -> service.enroll("tenant-a", "teacher-1", course.getId(),
                        new EducationEnrollmentRequest("student-1")));
        assertEquals("EDUCATION_COURSE_ARCHIVED", archived.getCode());

        when(enrollments.existsByTenantIdAndCourseIdAndLearnerUserIdAndStatus(
                "tenant-a", course.getId(), "student-1", EducationEnrollmentStatus.ACTIVE))
                .thenReturn(false);
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> service.getForParticipant("tenant-a", "student-1", course.getId()));
        assertEquals("EDUCATION_COURSE_ACCESS_DENIED", forbidden.getCode());
    }

    @Test
    void shouldReactivateRemovedEnrollmentWithoutCreatingDuplicateMember() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = new EducationCourse(
                "tenant-a", "teacher-1", "math-g1", "高一数学", "数学", "高中一年级", "人教A版");
        EducationEnrollment removed = new EducationEnrollment(
                "tenant-a", course.getId(), "student-1", java.time.Instant.now());
        removed.remove(java.time.Instant.now());
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));
        when(enrollments.findByTenantIdAndCourseIdAndLearnerUserId(
                "tenant-a", course.getId(), "student-1")).thenReturn(Optional.of(removed));
        when(enrollments.save(any(EducationEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new EducationCourseService(
                courses, enrollments, new SensitiveDataSanitizer())
                .enroll("tenant-a", "teacher-1", course.getId(), new EducationEnrollmentRequest("student-1"));

        assertEquals("ACTIVE", result.status());
        assertEquals(EducationEnrollmentStatus.ACTIVE, removed.getStatus());
    }

    @Test
    void shouldRejectRosterChangesAfterCourseCompletion() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = new EducationCourse(
                "tenant-a", "teacher-1", "math-g1", "高一数学", "数学", "高中一年级", "人教A版");
        course.complete("teacher-1", "本期结课", java.time.Instant.now());
        when(courses.findByTenantIdAndId("tenant-a", course.getId())).thenReturn(Optional.of(course));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new EducationCourseService(courses, enrollments, new SensitiveDataSanitizer())
                        .enroll("tenant-a", "teacher-1", course.getId(),
                                new EducationEnrollmentRequest("student-1")));

        assertEquals("EDUCATION_COURSE_COMPLETED", exception.getCode());
    }

    @Test
    void shouldLetLearnerJoinByCodeAndMakeRepeatedJoinIdempotent() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourse course = new EducationCourse(
                "tenant-a", "teacher-1", "math-g1", "高一数学", "数学", "高中一年级", "人教A版", "AB12CD34");
        when(courses.findByTenantIdAndJoinCode("tenant-a", "AB12CD34")).thenReturn(Optional.of(course));
        when(enrollments.findByTenantIdAndCourseIdAndLearnerUserId(
                "tenant-a", course.getId(), "student-1")).thenReturn(Optional.empty());
        when(enrollments.save(any(EducationEnrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.countByTenantIdAndCourseIdAndStatus(
                "tenant-a", course.getId(), EducationEnrollmentStatus.ACTIVE)).thenReturn(1L);

        EducationCourseService service = new EducationCourseService(
                courses, enrollments, new SensitiveDataSanitizer());
        var joined = service.joinByCode("tenant-a", "student-1",
                new EducationCourseJoinRequest(" ab12cd34 "));

        assertEquals(course.getId(), joined.id());
        assertEquals("AB12CD34", joined.joinCode());
        assertEquals(1L, joined.activeEnrollmentCount());
    }

    @Test
    void shouldRejectUnknownOrMalformedCourseJoinCode() {
        EducationCourseRepository courses = mock(EducationCourseRepository.class);
        EducationEnrollmentRepository enrollments = mock(EducationEnrollmentRepository.class);
        EducationCourseService service = new EducationCourseService(
                courses, enrollments, new SensitiveDataSanitizer());

        when(courses.findByTenantIdAndJoinCode("tenant-a", "NOTFOUND"))
                .thenReturn(Optional.empty());
        BusinessException unknown = assertThrows(BusinessException.class, () -> service.joinByCode(
                "tenant-a", "student-1", new EducationCourseJoinRequest("NOTFOUND")));
        assertEquals("EDUCATION_COURSE_JOIN_CODE_NOT_FOUND", unknown.getCode());

        BusinessException malformed = assertThrows(BusinessException.class, () -> service.joinByCode(
                "tenant-a", "student-1", new EducationCourseJoinRequest("bad code")));
        assertEquals("EDUCATION_COURSE_JOIN_CODE_INVALID", malformed.getCode());
    }
}
