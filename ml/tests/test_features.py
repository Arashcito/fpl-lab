import numpy as np
import pandas as pd
import pytest

from fpl_ml import features, metrics


def toy_season(points=(2, 6, 1, 9, 3), minutes=(90, 90, 30, 90, 0)):
    """One player (team 1) playing team 2 five times, plus one opponent player."""
    rows = []
    for i, (pts, mins) in enumerate(zip(points, minutes)):
        kickoff = pd.Timestamp("2025-08-01", tz="UTC") + pd.Timedelta(days=7 * i)
        common = dict(season="2025-26", gw=i + 1, kickoff_time=kickoff, fixture=100 + i,
                      team_h_score=2, team_a_score=1, fpl_xp=1.0, value=6.0)
        rows.append(dict(common, element=1, name="A", position="MID", team_id=1, opponent_id=2, was_home=True,
                         minutes=mins, starts=int(mins >= 60), total_points=pts, goals_scored=0, assists=0,
                         clean_sheets=0, goals_conceded=1, saves=0, bonus=0, bps=10, yellow_cards=0,
                         xg=0.2 * (i + 1), xa=0.1, xgc=1.0))
        rows.append(dict(common, element=2, name="B", position="DEF", team_id=2, opponent_id=1, was_home=False,
                         minutes=90, starts=1, total_points=1, goals_scored=0, assists=0, clean_sheets=0,
                         goals_conceded=2, saves=0, bonus=0, bps=5, yellow_cards=0, xg=0.5, xa=0.0, xgc=1.5))
    return pd.DataFrame(rows)


def player(df, element=1):
    return df[df["element"] == element].sort_values("kickoff_time").reset_index(drop=True)


def test_rolling_form_uses_only_previous_matches():
    f = player(features.build_features(toy_season()))
    assert np.isnan(f.loc[0, "total_points_last3"])            # nothing known before the first match
    assert f.loc[1, "total_points_last3"] == 2                  # only match 1
    assert f.loc[3, "total_points_last3"] == pytest.approx((2 + 6 + 1) / 3)
    assert f.loc[4, "total_points_last5"] == pytest.approx((2 + 6 + 1 + 9) / 4)
    assert f.loc[3, "played60_rate_last5"] == pytest.approx(2 / 3)


def test_changing_a_result_never_changes_that_matchs_features():
    """The core no-leakage guarantee."""
    base = player(features.build_features(toy_season()))
    changed = player(features.build_features(toy_season(points=(2, 6, 1, 25, 3))))
    cols = features.NUMERIC_FEATURES
    pd.testing.assert_frame_equal(base.loc[:3, cols], changed.loc[:3, cols])   # match 4 and earlier: identical
    assert changed.loc[4, "total_points_last3"] != base.loc[4, "total_points_last3"]  # the next match sees it


def test_upcoming_fixture_rows_get_features():
    season = toy_season()
    upcoming = season[season["gw"] == 5].copy()
    upcoming[["minutes", "total_points", "xg", "team_h_score", "team_a_score"]] = np.nan
    df = pd.concat([season[season["gw"] < 5], upcoming])
    f = player(features.build_features(df))
    assert f.loc[4, "total_points_last3"] == pytest.approx((6 + 1 + 9) / 3)
    assert not np.isnan(f.loc[4, "team_xg_for"])


def test_team_strength_comes_from_previous_matches():
    f = player(features.build_features(toy_season()))
    assert np.isnan(f.loc[0, "team_goals_for"])
    assert f.loc[1, "team_goals_for"] == 2          # home team scored 2 in the previous match
    assert f.loc[1, "opp_goals_for"] == 1           # opponent scored 1


def test_metrics_match_accuracy_page_definitions():
    df = pd.DataFrame({
        "season": "s", "gw": 1, "element": [1, 2, 3], "position": "MID",
        "pred": [5.0, 3.0, 1.0], "total_points": [2, 6, 0], "minutes": [90, 90, 0],
    })
    s = metrics.summary(df, "pred")
    assert s["mae"] == pytest.approx((3 + 3) / 2)        # only players who played
    assert s["captain_points"] == 2                      # highest prediction scored 2
    assert s["rank_corr"] == pytest.approx(-1.0)          # order reversed among those who played
