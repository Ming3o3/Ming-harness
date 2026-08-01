-- Agent 模式需要在数据库中持久化，Worker 重启后才能继续同一轮 Tool Call 编排。
ALTER TABLE harness_runs ADD COLUMN IF NOT EXISTS agent_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE harness_runs ADD COLUMN IF NOT EXISTS max_turns INTEGER NOT NULL DEFAULT 8;

ALTER TABLE harness_runs ADD CONSTRAINT ck_harness_runs_max_turns
    CHECK (max_turns >= 1 AND max_turns <= 20);
