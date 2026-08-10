package org.mingharness.education;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 将学习目标达标事实回写到教师布置的作业，避免作业状态停留在已接受。 */
@Service
public class LearningAssignmentCompletionService {

    private final LearningAssignmentRepository assignmentRepository;

    public LearningAssignmentCompletionService(LearningAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public int completeForGoal(String tenantId, String userId, String learningGoalId,
                               Instant completedAt) {
        int completed = 0;
        for (LearningAssignment assignment : assignmentRepository
                .findByTenantIdAndLearningGoalId(tenantId, learningGoalId)) {
            if (!tenantId.equals(assignment.getTenantId())
                    || !userId.equals(assignment.getLearnerUserId())) continue;
            if (assignment.getStatus() == LearningAssignmentStatus.ACCEPTED
                    || assignment.getStatus() == LearningAssignmentStatus.OVERDUE) {
                assignment.complete(completedAt);
                assignmentRepository.save(assignment);
                completed++;
            }
        }
        return completed;
    }
}
