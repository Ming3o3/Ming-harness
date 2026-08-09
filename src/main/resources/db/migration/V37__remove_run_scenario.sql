DROP INDEX IF EXISTS idx_harness_runs_tenant_scenario_created;

ALTER TABLE harness_runs DROP COLUMN IF EXISTS scenario;
