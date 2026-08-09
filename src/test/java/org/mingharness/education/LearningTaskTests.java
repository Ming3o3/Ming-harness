package org.mingharness.education;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LearningTaskTests {

    @Test
    void shouldMoveFromOpenToInProgressAndCompleted() {
        LearningTask task = task(Instant.now().minusSeconds(1));

        task.start("conversation-1", "run-1", Instant.now());
        assertEquals(LearningTaskStatus.IN_PROGRESS, task.getStatus());
        task.complete(true, Instant.now());

        assertEquals(LearningTaskStatus.COMPLETED, task.getStatus());
        assertEquals(Boolean.TRUE, task.getOutcomeCorrect());
    }

    @Test
    void shouldDeferAndBecomeAvailableAtTheNextDueTime() {
        LearningTask task = task(Instant.now().minusSeconds(1));
        Instant deferredUntil = Instant.now().plusSeconds(60);

        task.deferUntil(deferredUntil, Instant.now());
        assertEquals(LearningTaskStatus.DEFERRED, task.getStatus());
        assertEquals(1, task.getDeferCount());
        task.makeAvailable(deferredUntil);

        assertEquals(LearningTaskStatus.OPEN, task.getStatus());
    }

    @Test
    void shouldRejectCompletionAfterCancellation() {
        LearningTask task = task(Instant.now());
        task.cancel();

        assertThrows(IllegalStateException.class, () -> task.complete(false, Instant.now()));
    }

    private LearningTask task(Instant scheduledAt) {
        return new LearningTask("tenant-a", "student-1", LearningTaskType.REVIEW,
                "goal-1", "plan-1", 0, "保持度复习", "请完成复习题", scheduledAt);
    }
}
