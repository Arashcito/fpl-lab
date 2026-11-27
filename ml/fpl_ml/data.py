"""Load historical FPL data.

Source: the public "Fantasy-Premier-League" dataset by vaastav
(https://github.com/vaastav/Fantasy-Premier-League): one row per player per fixture,
for every season. Seasons from 2022-23 onwards include expected goals (xG/xA),
so those are the ones used here.
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd
import requests

SEASONS = ["2022-23", "2023-24", "2024-25", "2025-26", "2026-27"]
BASE_URL = "https://raw.githubusercontent.com/vaastav/Fantasy-Premier-League/master/data"
RAW_DIR = Path(__file__).resolve().parents[1] / "data" / "raw"
POSITIONS = ["GK", "DEF", "MID", "FWD"]

# columns every season is reduced to (same names the FPL API uses, where possible)
COLUMNS = [
    "season", "gw", "kickoff_time", "fixture", "element", "name", "position", "team_id",
    "opponent_id", "was_home", "value", "minutes", "starts", "total_points", "goals_scored",
    "assists", "clean_sheets", "goals_conceded", "saves", "bonus", "bps", "yellow_cards",
    "xg", "xa", "xgc", "team_h_score", "team_a_score", "fpl_xp",
]


def download(seasons: list[str] = SEASONS, force: bool = False) -> None:
    """Download each season's per-fixture player data and team list."""
    for season in seasons:
        folder = RAW_DIR / season
        folder.mkdir(parents=True, exist_ok=True)
        for remote, local in [("gws/merged_gw.csv", "merged_gw.csv"), ("teams.csv", "teams.csv")]:
            target = folder / local
            if target.exists() and not force:
                continue
            response = requests.get(f"{BASE_URL}/{season}/{remote}", timeout=120)
            response.raise_for_status()
            target.write_bytes(response.content)


def load_season(season: str) -> pd.DataFrame:
    folder = RAW_DIR / season
    df = pd.read_csv(folder / "merged_gw.csv")
    teams = pd.read_csv(folder / "teams.csv")
    team_ids = dict(zip(teams["name"], teams["id"]))

    out = pd.DataFrame({
        "season": season,
        "gw": df["GW"].astype(int),
        "kickoff_time": pd.to_datetime(df["kickoff_time"], utc=True),
        "fixture": df["fixture"].astype(int),
        "element": df["element"].astype(int),
        "name": df["name"],
        "position": df["position"],
        "team_id": df["team"].map(team_ids),
        "opponent_id": df["opponent_team"].astype(int),
        "was_home": df["was_home"].astype(bool),
        "value": df["value"] / 10.0,
        "minutes": df["minutes"],
        "starts": df["starts"],
        "total_points": df["total_points"],
        "goals_scored": df["goals_scored"],
        "assists": df["assists"],
        "clean_sheets": df["clean_sheets"],
        "goals_conceded": df["goals_conceded"],
        "saves": df["saves"],
        "bonus": df["bonus"],
        "bps": df["bps"],
        "yellow_cards": df["yellow_cards"],
        "xg": df["expected_goals"],
        "xa": df["expected_assists"],
        "xgc": df["expected_goals_conceded"],
        "team_h_score": df["team_h_score"],
        "team_a_score": df["team_a_score"],
        "fpl_xp": df["xP"],
    })
    # drop the 2024-25 "assistant manager" chip rows and the few duplicated rows in the source
    out = out[out["position"].isin(POSITIONS)]
    out = out.drop_duplicates(["season", "element", "fixture"])
    return out[COLUMNS].reset_index(drop=True)


def load_history(seasons: list[str] = SEASONS) -> pd.DataFrame:
    """All seasons in one table, downloading anything missing first."""
    download(seasons)
    df = pd.concat([load_season(s) for s in seasons], ignore_index=True)
    return df.sort_values(["season", "kickoff_time", "fixture", "element"]).reset_index(drop=True)


if __name__ == "__main__":
    data = load_history()
    print(data.groupby("season").size().rename("rows").to_string())
