ALTER TABLE harness_learning_assignment_feedback
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP(6) WITH TIME ZONE;
