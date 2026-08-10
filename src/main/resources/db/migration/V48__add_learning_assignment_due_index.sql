-- 让后台按生命周期状态和截止时间收敛逾期作业时走有界索引。
CREATE INDEX idx_harness_learning_assignments_due_status
    ON harness_learning_assignments (status, due_at);
