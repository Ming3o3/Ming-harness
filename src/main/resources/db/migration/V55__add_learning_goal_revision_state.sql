ALTER TABLE harness_learning_goals
    ADD COLUMN IF NOT EXISTS revision_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE harness_learning_goals
    ADD COLUMN IF NOT EXISTS revision_count INTEGER NOT NULL DEFAULT 0;
