import csv
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import education_experiment_analysis as analysis


class EducationExperimentAnalysisTests(unittest.TestCase):
    def _row(self, strategy, gain, reached, language="PYTHON"):
        row = {column: "" for column in analysis.REQUIRED_COLUMNS}
        row.update({
            "sample_id": f"sample-{strategy}",
            "learner_key": "learner-1",
            "learner_goal_key": "learner-goal-1",
            "run_status": "SUCCEEDED",
            "requested_strategy": strategy,
            "effective_strategy": strategy,
            "conditioning": "LOW_MASTERY_GAP_FIRST",
            "programming_language": language,
            "target_state_present": "true",
            "target_reached": "true" if reached else "false",
            "state_concept_count": "2",
            "target_attempts": "3",
            "target_correct_attempts": "1",
            "evidence_count": "1",
            "unique_evidence_count": "1",
            "evidence_chars": "120",
            "assessment_count": "1",
            "correct_assessment_count": "1" if reached else "0",
            "baseline_mastery": "0.2",
            "target_mastery": "0.8",
            "target_mastery_score": "0.2",
            "target_effective_mastery": "0.2",
            "target_conservative_mastery": "0.1",
            "target_uncertainty": "0.7",
            "target_retention_score": "0.9",
            "target_forgetting_risk": "0.1",
            "prerequisite_gap_coverage": "0.8",
            "evidence_redundancy_rate": "0.0",
            "average_ranking_score": "0.75",
            "graph_coverage": "0.8",
            "assessment_accuracy_rate": "1.0" if reached else "0.0",
            "average_mastery_gain": str(gain),
        })
        return row

    def _write(self, rows):
        handle = tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="", delete=False)
        handle.close()
        path = Path(handle.name)
        with path.open("w", encoding="utf-8", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=sorted(analysis.REQUIRED_COLUMNS))
            writer.writeheader()
            writer.writerows(rows)
        return path

    def test_analyzes_strategy_strata_pair_and_joint_ablation(self):
        rows = [
            self._row("FULL", 0.60, True),
            self._row("NO_LEARNER_STATE", 0.40, False),
            self._row("NO_DEPENDENCY_GRAPH", 0.50, True),
            self._row("NO_STATE_NO_GRAPH", 0.35, False),
            self._row("VECTOR_ONLY", 0.20, False),
        ]
        path = self._write(rows)
        try:
            result = analysis.analyze(analysis.read_samples(path))
        finally:
            path.unlink()

        full = next(item for item in result["strategies"] if item["effective_strategy"] == "FULL")
        self.assertEqual(full["assessment_count"], 1)
        self.assertEqual(full["target_reach_rate"], 1.0)
        self.assertEqual(full["sample_status"], "INSUFFICIENT_SAMPLE")
        self.assertEqual(len(result["conditioning_strata"]), 5)
        self.assertEqual(len(result["language_strata"]), 5)

        vector = next(item for item in result["paired_comparisons"]
                      if item["compared_strategy"] == "VECTOR_ONLY")
        self.assertAlmostEqual(vector["mastery_gain_delta"], -0.40)
        self.assertEqual(vector["paired_learner_goal_count"], 1)

        joint = result["joint_ablation"]
        self.assertEqual(joint["fully_paired_learner_goal_count"], 1)
        self.assertAlmostEqual(joint["interaction_effect"], 0.05)

    def test_rejects_missing_columns(self):
        handle = tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="", delete=False)
        handle.close()
        path = Path(handle.name)
        columns = sorted(analysis.REQUIRED_COLUMNS - {"target_reached"})
        with path.open("w", encoding="utf-8", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=columns)
            writer.writeheader()
            writer.writerow({column: "" for column in columns})
        try:
            with self.assertRaises(ValueError):
                analysis.read_samples(path)
        finally:
            path.unlink()


if __name__ == "__main__":
    unittest.main()
