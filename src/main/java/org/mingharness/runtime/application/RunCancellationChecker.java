package org.mingharness.runtime.application;

import org.mingharness.runtime.domain.RunStatus;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 使用独立只读事务读取 Run 取消状态，避免 Worker 长事务缓存旧实体。 */
@Service
public class RunCancellationChecker {

    private final RunRepository runRepository;

    public RunCancellationChecker(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isCancelled(String runId) {
        return runRepository.findStatusById(runId).filter(RunStatus.CANCELLED::equals).isPresent();
    }
}
