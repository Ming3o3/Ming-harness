package org.mingharness.education;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 复习证据到任务结果的最小回写服务。
 *
 * <p>该服务刻意只依赖任务仓储，避免教育测评工具、会话执行和任务编排之间形成循环依赖。</p>
 */
@Service
public class LearningTaskCompletionService {

    private final LearningTaskRepository taskRepository;

    public LearningTaskCompletionService(LearningTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public void completeForReview(String tenantId, String userId, String runId,
                                  boolean correct, Instant completedAt) {
        if (runId == null || runId.isBlank()) return;
        taskRepository.findFirstByTenantIdAndUserIdAndRunIdAndStatusIn(
                        tenantId, userId, runId,
                        List.of(LearningTaskStatus.OPEN, LearningTaskStatus.IN_PROGRESS))
                .ifPresent(task -> {
                    task.complete(correct, completedAt);
                    taskRepository.save(task);
                });
    }
}
