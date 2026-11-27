"""Evaluation, using the same definitions as the app's Accuracy page.

Predictions are per fixture; they are summed per player per gameweek (a double
gameweek counts both matches), then for every gameweek:

- average error (MAE): mean |predicted - actual| over players who played
- ranking: Spearman correlation between predicted and actual order, players who played
- top-10 picks: average real points of the 10 highest-predicted players
- captain: real points of the single highest-predicted player

and those are averaged over all gameweeks.
"""
from __future__ import annotations

import pandas as pd


def per_gameweek(df: pd.DataFrame, prediction: str) -> pd.DataFrame:
    """Sum fixtures into player-gameweeks."""
    return df.groupby(["season", "gw", "element"], as_index=False).agg(
        predicted=(prediction, "sum"),
        actual=("total_points", "sum"),
        minutes=("minutes", "sum"),
        position=("position", "first"),
    )


def gameweek_scores(df: pd.DataFrame, prediction: str, top_n: int = 10) -> pd.DataFrame:
    rows = []
    for (season, gw), g in per_gameweek(df, prediction).groupby(["season", "gw"]):
        played = g[g["minutes"] > 0]
        best = g.nlargest(top_n, "predicted")
        rows.append({
            "season": season,
            "gw": gw,
            "mae": (played["predicted"] - played["actual"]).abs().mean(),
            "bias": (played["predicted"] - played["actual"]).mean(),
            "rank_corr": played["predicted"].rank().corr(played["actual"].rank()),
            "top10_points": best["actual"].mean(),
            "captain_points": best["actual"].iloc[0],
        })
    return pd.DataFrame(rows)


def summary(df: pd.DataFrame, prediction: str) -> dict:
    s = gameweek_scores(df, prediction)
    return {
        "gameweeks": int(len(s)),
        "mae": round(float(s["mae"].mean()), 3),
        "bias": round(float(s["bias"].mean()), 3),
        "rank_corr": round(float(s["rank_corr"].mean()), 3),
        "top10_points": round(float(s["top10_points"].mean()), 3),
        "captain_points": round(float(s["captain_points"].mean()), 3),
    }
