-- 记录教育 Run 没有证据或执行失败时的可解释任务结果，避免任务永久停留在进行中。
ALTER TABLE harness_learning_tasks ADD COLUMN failure_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE harness_learning_tasks ADD COLUMN failure_reason TEXT;
ALTER TABLE harness_learning_tasks ADD COLUMN last_failed_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE harness_learning_tasks ADD COLUMN last_failed_run_id VARCHAR(255);

ALTER TABLE harness_learning_tasks ADD CONSTRAINT ck_harness_learning_task_failure_count
    CHECK (failure_count >= 0);
