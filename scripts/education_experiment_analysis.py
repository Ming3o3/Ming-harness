#!/usr/bin/env python3
"""Analyze the de-identified education experiment sample export.

The input is produced by ``GET /api/education/experiments/samples.csv``.  The
script intentionally uses only the Python standard library so that a paper
experiment can be rerun on a clean machine without a notebook environment.
It never reconstructs learner state from the current database: all state,
retrieval and assessment values come from the frozen Run sample rows.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from collections import defaultdict
from pathlib import Path
from typing import Iterable, Mapping, Sequence


MIN_RUNS_FOR_ANALYSIS = 30
MIN_ASSESSMENTS_FOR_ANALYSIS = 30

INTEGER_COLUMNS = {
    "state_concept_count",
    "target_attempts",
    "target_correct_attempts",
    "evidence_count",
    "unique_evidence_count",
    "evidence_chars",
    "assessment_count",
    "correct_assessment_count",
}
FLOAT_COLUMNS = {
    "baseline_mastery",
    "target_mastery",
    "target_mastery_score",
    "target_effective_mastery",
    "target_conservative_mastery",
    "target_uncertainty",
    "target_retention_score",
    "target_forgetting_risk",
    "prerequisite_gap_coverage",
    "evidence_redundancy_rate",
    "average_ranking_score",
    "graph_coverage",
    "assessment_accuracy_rate",
    "average_mastery_gain",
}
BOOLEAN_COLUMNS = {"target_state_present", "target_reached"}
REQUIRED_COLUMNS = {
    "sample_id",
    "learner_key",
    "learner_goal_key",
    "run_status",
    "requested_strategy",
    "effective_strategy",
    "conditioning",
    "programming_language",
    *INTEGER_COLUMNS,
    *FLOAT_COLUMNS,
    *BOOLEAN_COLUMNS,
}


def _blank(value: object) -> bool:
    return value is None or str(value).strip() == ""


def _number(value: str | None, column: str, line: int, integer: bool = False):
    if _blank(value):
        return None
    try:
        parsed = int(value) if integer else float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"第 {line} 行字段 {column} 不是数字: {value!r}") from exc
    if not math.isfinite(parsed):
        raise ValueError(f"第 {line} 行字段 {column} 不是有限数字: {value!r}")
    return parsed


def _boolean(value: str | None, column: str, line: int):
    if _blank(value):
        return None
    normalized = str(value).strip().lower()
    if normalized in {"true", "1", "yes"}:
        return True
    if normalized in {"false", "0", "no"}:
        return False
    raise ValueError(f"第 {line} 行字段 {column} 不是布尔值: {value!r}")


def read_samples(path: str | Path) -> list[dict[str, object]]:
    """Read and normalize a sample export, rejecting schema drift early."""
    with Path(path).open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        columns = set(reader.fieldnames or [])
        missing = sorted(REQUIRED_COLUMNS - columns)
        if missing:
            raise ValueError("样本 CSV 缺少字段: " + ", ".join(missing))
        rows: list[dict[str, object]] = []
        for line, raw in enumerate(reader, start=2):
            if not any(not _blank(value) for value in raw.values()):
                continue
            row: dict[str, object] = dict(raw)
            for column in INTEGER_COLUMNS:
                row[column] = _number(raw.get(column), column, line, integer=True)
            for column in FLOAT_COLUMNS:
                row[column] = _number(raw.get(column), column, line)
            for column in BOOLEAN_COLUMNS:
                row[column] = _boolean(raw.get(column), column, line)
            row["effective_strategy"] = (
                str(raw.get("effective_strategy") or raw.get("requested_strategy") or "FULL").strip()
            )
            row["requested_strategy"] = str(raw.get("requested_strategy") or "FULL").strip()
            row["conditioning"] = str(raw.get("conditioning") or "UNKNOWN").strip()
            row["programming_language"] = str(raw.get("programming_language") or "UNSPECIFIED").strip()
            row["run_status"] = str(raw.get("run_status") or "UNKNOWN").strip()
            rows.append(row)
    return rows


def _as_float(row: Mapping[str, object], column: str, default: float = 0.0) -> float:
    value = row.get(column)
    return default if value is None else float(value)


def _as_int(row: Mapping[str, object], column: str, default: int = 0) -> int:
    value = row.get(column)
    return default if value is None else int(value)


def _as_bool(row: Mapping[str, object], column: str, default: bool = False) -> bool:
    value = row.get(column)
    return default if value is None else bool(value)


def _mean(values: Iterable[float]) -> float:
    values = list(values)
    return sum(values) / len(values) if values else 0.0


def _weighted_mean(rows: Sequence[Mapping[str, object]], value_column: str,
                   weight_column: str) -> float:
    numerator = 0.0
    denominator = 0.0
    for row in rows:
        value = row.get(value_column)
        weight = _as_int(row, weight_column)
        if value is None or weight <= 0:
            continue
        numerator += float(value) * weight
        denominator += weight
    return numerator / denominator if denominator else 0.0


def _outcome_rows(rows: Sequence[Mapping[str, object]]) -> list[Mapping[str, object]]:
    # Match the paper-facing interpretation of a learning outcome: the Run
    # succeeded and has at least one formative observation bound to that Run.
    return [row for row in rows
            if str(row.get("run_status", "")).upper() == "SUCCEEDED"
            and _as_int(row, "assessment_count") > 0]


def _sample_status(run_count: int, assessment_count: int) -> str:
    if run_count == 0:
        return "NO_DATA"
    if run_count < MIN_RUNS_FOR_ANALYSIS or assessment_count < MIN_ASSESSMENTS_FOR_ANALYSIS:
        return "INSUFFICIENT_SAMPLE"
    return "ANALYSIS_READY"


def summarize_group(rows: Sequence[Mapping[str, object]]) -> dict[str, object]:
    """Aggregate one strategy/stratum using assessment-weighted learning gains."""
    successful = [row for row in rows if str(row.get("run_status", "")).upper() == "SUCCEEDED"]
    outcomes = _outcome_rows(rows)
    assessment_count = sum(_as_int(row, "assessment_count") for row in outcomes)
    correct_count = sum(_as_int(row, "correct_assessment_count") for row in outcomes)
    evidence_rows = [row for row in rows if _as_int(row, "evidence_count") > 0]
    outcome_evidence_rows = [row for row in outcomes if _as_int(row, "evidence_count") > 0]
    return {
        "run_count": len(rows),
        "successful_run_count": len(successful),
        "outcome_run_count": len(outcomes),
        "assessment_count": assessment_count,
        "correct_assessment_count": correct_count,
        "assessment_accuracy_rate": correct_count / assessment_count if assessment_count else 0.0,
        "average_mastery_gain": _weighted_mean(outcomes, "average_mastery_gain", "assessment_count"),
        "target_reach_rate": _mean(1.0 if _as_bool(row, "target_reached") else 0.0 for row in outcomes),
        "evidence_coverage_rate": len(evidence_rows) / len(rows) if rows else 0.0,
        "average_graph_coverage": _mean(_as_float(row, "graph_coverage") for row in outcome_evidence_rows),
        "average_prerequisite_gap_coverage": _mean(
            _as_float(row, "prerequisite_gap_coverage") for row in outcome_evidence_rows
        ),
        "average_evidence_redundancy_rate": _mean(
            _as_float(row, "evidence_redundancy_rate") for row in evidence_rows
        ),
        "average_target_uncertainty": _mean(
            _as_float(row, "target_uncertainty") for row in rows
            if row.get("target_uncertainty") is not None
        ),
        "average_forgetting_risk": _mean(
            _as_float(row, "target_forgetting_risk") for row in rows
            if row.get("target_forgetting_risk") is not None
        ),
        "sample_status": _sample_status(len(rows), assessment_count),
    }


def _group(rows: Sequence[Mapping[str, object]], columns: Sequence[str]):
    grouped: dict[tuple[object, ...], list[Mapping[str, object]]] = defaultdict(list)
    for row in rows:
        grouped[tuple(row.get(column) for column in columns)].append(row)
    return grouped


def _labeled_summaries(rows: Sequence[Mapping[str, object]], columns: Sequence[str]):
    result = []
    for key, values in sorted(_group(rows, columns).items(), key=lambda item: tuple(str(v) for v in item[0])):
        item = {column: value for column, value in zip(columns, key)}
        item.update(summarize_group(values))
        result.append(item)
    return result


def _goal_strategy_outcome(rows: Sequence[Mapping[str, object]]):
    outcomes = _outcome_rows(rows)
    if not outcomes:
        return None
    evidence_rows = [row for row in outcomes if _as_int(row, "evidence_count") > 0]
    return {
        "mastery_gain": _weighted_mean(outcomes, "average_mastery_gain", "assessment_count"),
        "target_reached": any(_as_bool(row, "target_reached") for row in outcomes),
        "prerequisite_gap_coverage": _mean(
            _as_float(row, "prerequisite_gap_coverage") for row in evidence_rows
        ),
    }


def paired_comparisons(rows: Sequence[Mapping[str, object]], reference: str = "FULL"):
    grouped = _group(rows, ("learner_goal_key", "effective_strategy"))
    by_goal: dict[object, dict[str, list[Mapping[str, object]]]] = defaultdict(dict)
    for (goal, strategy), values in grouped.items():
        by_goal[goal][str(strategy)] = values
    accumulators: dict[str, list[tuple[dict[str, object], dict[str, object]]]] = defaultdict(list)
    for strategies in by_goal.values():
        reference_outcome = _goal_strategy_outcome(strategies.get(reference, []))
        if reference_outcome is None:
            continue
        for strategy, values in strategies.items():
            if strategy == reference:
                continue
            compared = _goal_strategy_outcome(values)
            if compared is not None:
                accumulators[strategy].append((reference_outcome, compared))

    result = []
    for strategy in sorted(accumulators):
        pairs = accumulators[strategy]
        reference_gain = _mean(item[0]["mastery_gain"] for item in pairs)
        compared_gain = _mean(item[1]["mastery_gain"] for item in pairs)
        reference_reach = _mean(1.0 if item[0]["target_reached"] else 0.0 for item in pairs)
        compared_reach = _mean(1.0 if item[1]["target_reached"] else 0.0 for item in pairs)
        reference_gap = _mean(item[0]["prerequisite_gap_coverage"] for item in pairs)
        compared_gap = _mean(item[1]["prerequisite_gap_coverage"] for item in pairs)
        result.append({
            "reference_strategy": reference,
            "compared_strategy": strategy,
            "paired_learner_goal_count": len(pairs),
            "reference_average_mastery_gain": reference_gain,
            "compared_average_mastery_gain": compared_gain,
            "mastery_gain_delta": compared_gain - reference_gain,
            "reference_target_reach_rate": reference_reach,
            "compared_target_reach_rate": compared_reach,
            "target_reach_rate_delta": compared_reach - reference_reach,
            "reference_prerequisite_gap_coverage": reference_gap,
            "compared_prerequisite_gap_coverage": compared_gap,
            "prerequisite_gap_coverage_delta": compared_gap - reference_gap,
            "sample_status": _sample_status(len(pairs), len(pairs)),
        })
    return result


def joint_ablation(rows: Sequence[Mapping[str, object]]) -> dict[str, object]:
    arms = ("FULL", "NO_LEARNER_STATE", "NO_DEPENDENCY_GRAPH", "NO_STATE_NO_GRAPH")
    grouped = _group(rows, ("learner_goal_key", "effective_strategy"))
    by_goal: dict[object, dict[str, list[Mapping[str, object]]]] = defaultdict(dict)
    for (goal, strategy), values in grouped.items():
        by_goal[goal][str(strategy)] = values
    complete = []
    vector = []
    for strategies in by_goal.values():
        outcomes = {arm: _goal_strategy_outcome(strategies.get(arm, [])) for arm in arms}
        vector_outcome = _goal_strategy_outcome(strategies.get("VECTOR_ONLY", []))
        if vector_outcome is not None:
            vector.append(vector_outcome["mastery_gain"])
        if all(outcomes[arm] is not None for arm in arms):
            complete.append(outcomes)
    if not complete:
        return {
            "fully_paired_learner_goal_count": 0,
            "sample_status": "NO_DATA",
        }
    average = lambda arm: _mean(item[arm]["mastery_gain"] for item in complete)
    full = average("FULL")
    no_state = average("NO_LEARNER_STATE")
    no_graph = average("NO_DEPENDENCY_GRAPH")
    no_state_no_graph = average("NO_STATE_NO_GRAPH")
    return {
        "fully_paired_learner_goal_count": len(complete),
        "full_average_mastery_gain": full,
        "no_learner_state_average_mastery_gain": no_state,
        "no_dependency_graph_average_mastery_gain": no_graph,
        "no_state_no_graph_average_mastery_gain": no_state_no_graph,
        "vector_only_average_mastery_gain": _mean(vector),
        "full_minus_no_learner_state": full - no_state,
        "full_minus_no_dependency_graph": full - no_graph,
        "interaction_effect": full - no_state - no_graph + no_state_no_graph,
        "sample_status": _sample_status(len(complete), len(complete)),
    }


def analyze(rows: Sequence[Mapping[str, object]]) -> dict[str, object]:
    strategies = _labeled_summaries(rows, ("effective_strategy",))
    return {
        "input_run_count": len(rows),
        "input_outcome_run_count": len(_outcome_rows(rows)),
        "strategies": strategies,
        "conditioning_strata": _labeled_summaries(rows, ("effective_strategy", "conditioning")),
        "language_strata": _labeled_summaries(rows, ("effective_strategy", "programming_language")),
        "paired_comparisons": paired_comparisons(rows),
        "joint_ablation": joint_ablation(rows),
        "analysis_contract": {
            "outcome_definition": "run_status == SUCCEEDED and assessment_count > 0",
            "mastery_gain_weight": "assessment_count",
            "minimum_runs": MIN_RUNS_FOR_ANALYSIS,
            "minimum_assessments": MIN_ASSESSMENTS_FOR_ANALYSIS,
            "significance_testing": "not performed; sample_status is only a descriptive gate",
        },
    }


def write_summary_csv(path: str | Path, summaries: Sequence[Mapping[str, object]]) -> None:
    fields = [
        "effective_strategy", "run_count", "successful_run_count", "outcome_run_count",
        "assessment_count", "correct_assessment_count", "assessment_accuracy_rate",
        "average_mastery_gain", "target_reach_rate", "evidence_coverage_rate",
        "average_graph_coverage", "average_prerequisite_gap_coverage",
        "average_evidence_redundancy_rate", "average_target_uncertainty",
        "average_forgetting_risk", "sample_status",
    ]
    with Path(path).open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for summary in summaries:
            writer.writerow({field: summary.get(field, "") for field in fields})


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="分析教育 Agent 的去标识逐 Run 样本 CSV")
    parser.add_argument("samples", help="/api/education/experiments/samples.csv 的下载文件")
    parser.add_argument("--json-out", help="写入完整 JSON 分析结果；不提供时输出到 stdout")
    parser.add_argument("--summary-csv", help="额外写入按策略聚合的 CSV")
    args = parser.parse_args(argv)
    try:
        result = analyze(read_samples(args.samples))
        payload = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=False) + "\n"
        if args.json_out:
            Path(args.json_out).write_text(payload, encoding="utf-8")
        else:
            sys.stdout.write(payload)
        if args.summary_csv:
            write_summary_csv(args.summary_csv, result["strategies"])
        return 0
    except (OSError, ValueError) as exc:
        print(f"education experiment analysis failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
