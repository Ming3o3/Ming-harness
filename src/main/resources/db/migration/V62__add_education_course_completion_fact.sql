ALTER TABLE harness_education_courses
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE harness_education_courses
    ADD COLUMN IF NOT EXISTS completed_by_user_id VARCHAR(255);

ALTER TABLE harness_education_courses
    ADD COLUMN IF NOT EXISTS completion_note TEXT;
