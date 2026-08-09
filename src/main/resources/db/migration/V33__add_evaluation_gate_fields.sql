ALTER TABLE harness_evaluation_reports ADD COLUMN baseline_report_id VARCHAR(128);
ALTER TABLE harness_evaluation_reports ADD COLUMN baseline_success_rate NUMERIC(10, 4);
ALTER TABLE harness_evaluation_reports ADD COLUMN success_rate_delta NUMERIC(10, 4);
ALTER TABLE harness_evaluation_reports ADD COLUMN minimum_success_rate NUMERIC(10, 4);
ALTER TABLE harness_evaluation_reports ADD COLUMN gate_passed BOOLEAN DEFAULT TRUE;
UPDATE harness_evaluation_reports SET gate_passed = TRUE WHERE gate_passed IS NULL;
ALTER TABLE harness_evaluation_reports ALTER COLUMN gate_passed SET NOT NULL;
