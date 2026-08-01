ALTER TABLE harness_runs DROP CONSTRAINT IF EXISTS ck_harness_runs_max_turns;
ALTER TABLE harness_runs ADD CONSTRAINT ck_harness_runs_max_turns
    CHECK (max_turns >= 1 AND max_turns <= 1000);
