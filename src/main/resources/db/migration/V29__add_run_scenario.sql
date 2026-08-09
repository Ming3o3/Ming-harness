ALTER TABLE harness_runs ADD COLUMN scenario VARCHAR(32);

UPDATE harness_runs
SET scenario = CASE
    WHEN workspace_id IS NOT NULL THEN 'CODE_AGENT'
    WHEN agent_mode = TRUE THEN 'PROCESS_AUTOMATION'
    ELSE 'UNCLASSIFIED'
END
WHERE scenario IS NULL;

ALTER TABLE harness_runs ALTER COLUMN scenario SET DEFAULT 'UNCLASSIFIED';
ALTER TABLE harness_runs ALTER COLUMN scenario SET NOT NULL;

CREATE INDEX idx_harness_runs_tenant_scenario_created
    ON harness_runs (tenant_id, scenario, created_at DESC);
