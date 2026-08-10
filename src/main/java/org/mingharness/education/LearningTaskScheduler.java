package org.mingharness.education;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 在后台把到期的长期计划转成学习者可以看到的任务。 */
@Component
public class LearningTaskScheduler {

    private final LearningTaskService taskService;
    private final LearningTaskReconciliationService reconciliationService;
    private final LearningAssignmentReconciliationService assignmentReconciliationService;
    private final LearningAssignmentService assignmentService;

    public LearningTaskScheduler(LearningTaskService taskService,
                                LearningTaskReconciliationService reconciliationService,
                                LearningAssignmentReconciliationService assignmentReconciliationService,
                                LearningAssignmentService assignmentService) {
        this.taskService = taskService;
        this.reconciliationService = reconciliationService;
        this.assignmentReconciliationService = assignmentReconciliationService;
        this.assignmentService = assignmentService;
    }

    @Scheduled(
            fixedDelayString = "${harness.education.task-materialization-interval-ms:60000}",
            initialDelayString = "${harness.education.task-materialization-initial-delay-ms:10000}"
    )
    public void materializeDueTasks() {
        assignmentService.expireOverdue(Instant.now(), 100);
        assignmentReconciliationService.reconcile(Instant.now(), 100);
        reconciliationService.reconcile(Instant.now(), 100);
        taskService.materializeDueTasks(Instant.now(), 100);
    }
}
