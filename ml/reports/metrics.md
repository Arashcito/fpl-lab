# Model results (lgbm-20261127)

Trained on 119,822 player-fixture rows.

## Validation: trained on 2022-23, 2023-24, 2024-25, tested on 2025-26

| Model | Avg error (lower is better) | Ranking | Top-10 picks (pts) | Captain (pts) |
|---|---|---|---|---|
| Last-5 average (baseline) | 2.24 | 0.27 | 4.12 | 4.71 |
| FPL official xP* | 2.67 | 0.58 | 3.69 | 5.82 |
| Ridge regression | 1.99 | 0.34 | 4.72 | 7.29 |
| LightGBM | 1.99 | 0.34 | 4.87 | 7.13 |

## Test: trained up to 2025-26, tested on 2026-27 (GW1-10, never used for choosing settings)

| Model | Avg error (lower is better) | Ranking | Top-10 picks (pts) | Captain (pts) |
|---|---|---|---|---|
| Last-5 average (baseline) | 2.23 | 0.28 | 4.02 | 3.30 |
| FPL official xP* | 2.35 | 0.54 | 5.53 | 8.40 |
| Ridge regression | 1.98 | 0.37 | 4.19 | 4.30 |
| LightGBM | 1.97 | 0.35 | 4.94 | 4.80 |

Average error and ranking count players who played; top-10 and captain use the 10 / 1 highest-predicted players of each gameweek, averaged over gameweeks.

\* The dataset's `xP` column is FPL's own estimate as recorded by the dataset, which may be taken after a gameweek started (when line-ups are known). It is shown for reference, not as a fair rival; the live app compares against FPL's estimate saved before each deadline.
