"""Feature engineering.

One row = one player in one fixture. Every feature describes what was known BEFORE
that fixture kicked off (rolling windows are shifted by one match), so the model never
sees the result it is trying to predict. Upcoming fixtures can be appended as rows
with no result: the same code then produces their features.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

TARGET = "total_points"

# player form: averages over the previous 3 and 5 matches
FORM_STATS = ["minutes", "total_points", "xg", "xa", "bonus", "bps", "saves", "starts"]
FORM_WINDOWS = (3, 5)
TEAM_WINDOW = 6

NUMERIC_FEATURES = (
    [f"{s}_last{w}" for w in FORM_WINDOWS for s in FORM_STATS]
    + [
        "played60_rate_last5",
        "matches_before",
        "season_points_per_match",
        "season_minutes_per_match",
        "season_xg_per90",
        "season_xa_per90",
        "value",
        "was_home",
        "team_xg_for",
        "team_xg_against",
        "team_goals_for",
        "team_goals_against",
        "opp_xg_for",
        "opp_xg_against",
        "opp_goals_for",
        "opp_goals_against",
    ]
)
CATEGORICAL_FEATURES = ["position"]
FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES


def _shifted_rolling_mean(grouped: pd.core.groupby.SeriesGroupBy, window: int) -> pd.Series:
    """Mean of the previous `window` values in each group (the current row excluded)."""
    return grouped.transform(lambda s: s.shift(1).rolling(window, min_periods=1).mean())


def add_player_form(df: pd.DataFrame) -> pd.DataFrame:
    df = df.sort_values(["season", "element", "kickoff_time", "fixture"]).copy()
    g = df.groupby(["season", "element"], sort=False)

    for w in FORM_WINDOWS:
        for stat in FORM_STATS:
            df[f"{stat}_last{w}"] = _shifted_rolling_mean(g[stat], w)

    played60 = (df["minutes"] >= 60).astype(float).where(df["minutes"].notna())
    df["played60_rate_last5"] = _shifted_rolling_mean(played60.groupby([df["season"], df["element"]], sort=False), 5)

    # season-to-date totals before this match
    df["matches_before"] = g.cumcount()
    cum = {s: g[s].transform(lambda x: x.shift(1).fillna(0).cumsum()) for s in ["total_points", "minutes", "xg", "xa"]}
    played_before = g["minutes"].transform(lambda x: x.shift(1).notna().cumsum())
    df["season_points_per_match"] = cum["total_points"] / played_before.replace(0, np.nan)
    df["season_minutes_per_match"] = cum["minutes"] / played_before.replace(0, np.nan)
    # per 90 minutes, with one "empty" match added so tiny samples don't explode
    df["season_xg_per90"] = cum["xg"] / (cum["minutes"] + 90) * 90
    df["season_xa_per90"] = cum["xa"] / (cum["minutes"] + 90) * 90
    return df


def team_matches(df: pd.DataFrame) -> pd.DataFrame:
    """One row per team per fixture with goals and xG for/against, and the
    rolling averages of the previous TEAM_WINDOW matches."""
    team_xg = df.groupby(["season", "fixture", "team_id"], as_index=False).agg(
        xg_for=("xg", lambda s: s.sum(min_count=1)),
        kickoff_time=("kickoff_time", "first"),
        was_home=("was_home", "first"),
        opponent_id=("opponent_id", "first"),
        team_h_score=("team_h_score", "first"),
        team_a_score=("team_a_score", "first"),
    )
    team_xg["goals_for"] = np.where(team_xg["was_home"], team_xg["team_h_score"], team_xg["team_a_score"])
    team_xg["goals_against"] = np.where(team_xg["was_home"], team_xg["team_a_score"], team_xg["team_h_score"])

    opponent = team_xg[["season", "fixture", "team_id", "xg_for"]].rename(
        columns={"team_id": "opponent_id", "xg_for": "xg_against"})
    t = team_xg.merge(opponent, on=["season", "fixture", "opponent_id"], how="left")
    t = t.sort_values(["season", "team_id", "kickoff_time", "fixture"])
    g = t.groupby(["season", "team_id"], sort=False)
    for stat in ["xg_for", "xg_against", "goals_for", "goals_against"]:
        t[f"{stat}_form"] = g[stat].transform(lambda s: s.shift(1).rolling(TEAM_WINDOW, min_periods=1).mean())
    return t


def add_team_strength(df: pd.DataFrame) -> pd.DataFrame:
    t = team_matches(df)
    form = t[["season", "fixture", "team_id", "xg_for_form", "xg_against_form", "goals_for_form", "goals_against_form"]]
    own = form.rename(columns={
        "xg_for_form": "team_xg_for", "xg_against_form": "team_xg_against",
        "goals_for_form": "team_goals_for", "goals_against_form": "team_goals_against"})
    opp = form.rename(columns={
        "team_id": "opponent_id",
        "xg_for_form": "opp_xg_for", "xg_against_form": "opp_xg_against",
        "goals_for_form": "opp_goals_for", "goals_against_form": "opp_goals_against"})
    df = df.merge(own, on=["season", "fixture", "team_id"], how="left")
    return df.merge(opp, on=["season", "fixture", "opponent_id"], how="left")


def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """Add every model feature. Rows with no result yet (upcoming fixtures) are fine."""
    out = add_player_form(df)
    out = add_team_strength(out)
    out["was_home"] = out["was_home"].astype(int)
    out["position"] = pd.Categorical(out["position"], categories=["GK", "DEF", "MID", "FWD"])
    return out.sort_values(["season", "kickoff_time", "fixture", "element"]).reset_index(drop=True)
