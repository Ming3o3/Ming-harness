package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningReviewPlanTests {

    @Test
    void shouldScheduleFirstReviewImmediatelyAndExpandAfterSuccess() {
        Instant completedAt = Instant.parse("2026-08-01T00:00:00Z");
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", "goal-1",
                "profile-1", "函数", completedAt);

        assertTrue(plan.isDue(completedAt));
        plan.recordReview(true, completedAt);

        assertEquals(1, plan.getReviewCount());
        assertEquals(1, plan.getSuccessfulReviewCount());
        assertEquals(1, plan.getIntervalDays());
        assertEquals(completedAt.plus(1, ChronoUnit.DAYS), plan.getNextReviewAt());
        assertFalse(plan.isDue(completedAt));
        assertTrue(plan.isDue(completedAt.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    void shouldResetIntervalAfterFailedReview() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", "goal-1",
                "profile-1", "函数", start);
        plan.recordReview(true, start);
        Instant second = start.plus(1, ChronoUnit.DAYS);
        plan.recordReview(true, second);
        assertEquals(2, plan.getIntervalDays());

        Instant failed = second.plus(2, ChronoUnit.DAYS);
        plan.recordReview(false, failed);
        assertEquals(3, plan.getReviewCount());
        assertEquals(2, plan.getSuccessfulReviewCount());
        assertEquals(1, plan.getIntervalDays());
        assertEquals(failed.plus(1, ChronoUnit.DAYS), plan.getNextReviewAt());
        assertEquals(Boolean.FALSE, plan.getLastReviewCorrect());
    }

    @Test
    void shouldKeepHistoricalReviewSequenceWhenRestartingAfterRevision() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        LearningReviewPlan plan = new LearningReviewPlan("tenant-a", "student-1", "goal-1",
                "profile-1", "函数", start);
        plan.recordReview(true, start);
        plan.restartFromCompletion(Instant.parse("2026-08-10T00:00:00Z"));

        assertEquals(1, plan.getReviewCount());
        assertEquals(1, plan.getSuccessfulReviewCount());
        assertEquals(0, plan.getIntervalDays());
        assertEquals(Instant.parse("2026-08-10T00:00:00Z"), plan.getNextReviewAt());
        assertTrue(plan.isDue(Instant.parse("2026-08-10T00:00:00Z")));
    }
}
