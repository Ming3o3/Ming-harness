package org.mingharness.education.api;

import java.time.Instant;
import java.util.List;

/** 一次课程批量布置的结果；重复幂等键会返回原批次而不重复分配。 */
public record EducationCourseAssignmentBatchView(
        String courseId,
        String batchId,
        boolean reused,
        int assignmentCount,
        Instant createdAt,
        List<LearningAssignmentView> assignments
) {
}
