"""Train, evaluate and save the model.

    python -m fpl_ml.train

Time-based evaluation (never a random split: the model must only learn from the past):

1. validation: train on 2022-23 .. 2024-25, evaluate on all of 2025-26 (used to choose settings)
2. test:       train on 2022-23 .. 2025-26, evaluate on 2026-27 so far (never used for choices)
3. final:      train on every season, save to models/ for weekly predictions

Writes reports/metrics.json, reports/metrics.md and models/fpl_lgbm.joblib.
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
import pandas as pd

from . import data, features, metrics, models

ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / "models" / "fpl_lgbm.joblib"
REPORT_DIR = ROOT / "reports"

# chosen on the validation season (see notebooks/02_modeling.ipynb)
LGBM_PARAMS = dict(n_estimators=150, num_leaves=31, min_child_samples=200, learning_rate=0.03)
RIDGE_ALPHA = 10.0

TRAIN_SEASONS = ["2022-23", "2023-24", "2024-25"]
VALIDATION_SEASON = "2025-26"
TEST_SEASON = "2026-27"

MODEL_LABELS = {
    "last5": "Last-5 average (baseline)",
    "fpl_xp": "FPL official xP*",
    "ridge": "Ridge regression",
    "lgbm": "LightGBM",
}


def load_features() -> pd.DataFrame:
    return features.build_features(data.load_history())


def evaluate(train: pd.DataFrame, test: pd.DataFrame) -> tuple[dict, pd.DataFrame]:
    """Fit every model on `train`, predict `test`, return summary metrics and the predictions."""
    test = test.copy()
    fallback = train.groupby(train["position"].astype(str), observed=True)["total_points"].mean()
    test["last5"] = models.last5_average(test, fallback)
    test["ridge"] = models.predict(models.fit(models.make_ridge(RIDGE_ALPHA), train), test)
    test["lgbm"] = models.predict(models.fit(models.make_lightgbm(**LGBM_PARAMS), train), test)
    return {name: metrics.summary(test, name) for name in MODEL_LABELS}, test


def markdown_table(results: dict) -> str:
    lines = ["| Model | Avg error (lower is better) | Ranking | Top-10 picks (pts) | Captain (pts) |",
             "|---|---|---|---|---|"]
    for key, label in MODEL_LABELS.items():
        r = results[key]
        lines.append(f"| {label} | {r['mae']:.2f} | {r['rank_corr']:.2f} | {r['top10_points']:.2f} | {r['captain_points']:.2f} |")
    return "\n".join(lines)


def main() -> None:
    df = load_features()
    train_val = df[df["season"].isin(TRAIN_SEASONS)]
    validation = df[df["season"] == VALIDATION_SEASON]
    train_test = df[df["season"].isin(TRAIN_SEASONS + [VALIDATION_SEASON])]
    test = df[(df["season"] == TEST_SEASON) & df["total_points"].notna()]

    val_results, _ = evaluate(train_val, validation)
    test_results, _ = evaluate(train_test, test)

    final = models.fit(models.make_lightgbm(**LGBM_PARAMS), df[df["total_points"].notna()])
    importance = (pd.Series(final.booster_.feature_importance("gain"), index=features.FEATURES)
                  .sort_values(ascending=False))
    version = datetime.now(timezone.utc).strftime("lgbm-%Y%m%d")
    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": final, "features": features.FEATURES, "version": version,
                 "trained_on": sorted(df["season"].unique().tolist())}, MODEL_PATH)

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "version": version,
        "params": LGBM_PARAMS,
        "rows": int(len(df)),
        "validation": {"train": TRAIN_SEASONS, "test": VALIDATION_SEASON, "results": val_results},
        "test": {"train": TRAIN_SEASONS + [VALIDATION_SEASON], "test": TEST_SEASON,
                 "gameweeks": test_results["lgbm"]["gameweeks"], "results": test_results},
        "feature_importance": {k: round(float(v), 1) for k, v in importance.head(15).items()},
    }
    (REPORT_DIR / "metrics.json").write_text(json.dumps(report, indent=2))
    (REPORT_DIR / "metrics.md").write_text(
        f"# Model results ({version})\n\n"
        f"Trained on {len(df):,} player-fixture rows.\n\n"
        f"## Validation: trained on {', '.join(TRAIN_SEASONS)}, tested on {VALIDATION_SEASON}\n\n"
        f"{markdown_table(val_results)}\n\n"
        f"## Test: trained up to {VALIDATION_SEASON}, tested on {TEST_SEASON} "
        f"(GW1-{test_results['lgbm']['gameweeks']}, never used for choosing settings)\n\n"
        f"{markdown_table(test_results)}\n\n"
        "Average error and ranking count players who played; top-10 and captain use the "
        "10 / 1 highest-predicted players of each gameweek, averaged over gameweeks.\n\n"
        "\\* The dataset's `xP` column is FPL's own estimate as recorded by the dataset, which may "
        "be taken after a gameweek started (when line-ups are known). It is shown for reference, "
        "not as a fair rival; the live app compares against FPL's estimate saved before each deadline.\n")
    print((REPORT_DIR / "metrics.md").read_text())
    print(f"Saved {MODEL_PATH.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
