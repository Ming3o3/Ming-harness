-- 为课程增加可分享的短邀请码，降低教师逐个录入学生账号的使用门槛。
ALTER TABLE harness_education_courses
    ADD COLUMN IF NOT EXISTS join_code VARCHAR(12);

-- 历史课程使用课程 UUID 派生值回填，保证升级过程无需人工补数据。
UPDATE harness_education_courses
SET join_code = upper(substr(replace(id, '-', ''), 1, 8))
WHERE join_code IS NULL;

ALTER TABLE harness_education_courses
    ALTER COLUMN join_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_harness_education_course_join_code
    ON harness_education_courses (tenant_id, join_code);
