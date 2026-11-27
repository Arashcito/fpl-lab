"""Predict points for the next gameweeks with the trained model.

    python -m fpl_ml.predict                 # print + save to output/predictions.csv
    DATABASE_URL_UNPOOLED=... python -m fpl_ml.predict   # also write to Postgres (table ml_predictions)

Steps:
1. FPL API: this season's per-match history of every player, the fixtures, and injury flags.
2. Add the upcoming fixtures as rows with no result and build the same features as in training.
3. Predict each fixture, multiply by the chance of playing, sum per player per gameweek.
"""
from __future__ import annotations

import argparse
import os
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import requests

from . import db, features
from .data import COLUMNS
from .train import MODEL_PATH

API = "https://fantasy.premierleague.com/api"
MODEL_NAME = "fpl-lab-ml"
POSITION = {1: "GK", 2: "DEF", 3: "MID", 4: "FWD"}
OUTPUT = Path(__file__).resolve().parents[1] / "output" / "predictions.csv"

session = requests.Session()
session.headers["User-Agent"] = "fpl-lab"


def get(path: str):
    response = session.get(f"{API}{path}", timeout=60)
    response.raise_for_status()
    return response.json()


def season_label(events: list[dict]) -> str:
    year = datetime.fromisoformat(events[0]["deadline_time"].replace("Z", "+00:00")).year
    return f"{year}-{(year + 1) % 100:02d}"


def availability(status: str, chance: float | None, gameweeks_ahead: int) -> float:
    """Same rule as the app's formula (PointsModel.availability)."""
    if chance is not None and not np.isnan(chance):
        now = chance / 100
    else:
        now = {"a": 1.0, "d": 0.75}.get(status, 0.0)
    if gameweeks_ahead == 0 or now >= 1:
        return now
    return (now + 1) / 2 if status == "d" else now


def history_rows(players: list[dict], fixtures: pd.DataFrame, season: str) -> pd.DataFrame:
    """Every player's matches so far this season, in the training data's columns."""
    def fetch(p):
        return p, get(f"/element-summary/{p['id']}/")["history"]

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(fetch, players))

    fixture_teams = fixtures.set_index("id")[["team_h", "team_a"]]
    rows = []
    for p, history in results:
        for h in history:
            if h["fixture"] not in fixture_teams.index:
                continue
            teams = fixture_teams.loc[h["fixture"]]
            rows.append({
                "season": season, "gw": h["round"], "kickoff_time": pd.Timestamp(h["kickoff_time"]),
                "fixture": h["fixture"], "element": p["id"], "name": p["web_name"],
                "position": POSITION[p["element_type"]],
                # the player's team in that match (correct even if he has since moved club)
                "team_id": teams["team_h"] if h["was_home"] else teams["team_a"],
                "opponent_id": h["opponent_team"], "was_home": h["was_home"], "value": h["value"] / 10,
                "minutes": h["minutes"], "starts": h.get("starts", 0), "total_points": h["total_points"],
                "goals_scored": h["goals_scored"], "assists": h["assists"], "clean_sheets": h["clean_sheets"],
                "goals_conceded": h["goals_conceded"], "saves": h["saves"], "bonus": h["bonus"], "bps": h["bps"],
                "yellow_cards": h["yellow_cards"], "xg": float(h["expected_goals"]),
                "xa": float(h["expected_assists"]), "xgc": float(h["expected_goals_conceded"]),
                "team_h_score": h["team_h_score"], "team_a_score": h["team_a_score"], "fpl_xp": np.nan,
            })
    return pd.DataFrame(rows, columns=COLUMNS)


def upcoming_rows(players: list[dict], fixtures: pd.DataFrame, events: list[int], season: str) -> pd.DataFrame:
    """One row per player per upcoming fixture, with no result."""
    rows = []
    upcoming = fixtures[fixtures["event"].isin(events)]
    for p in players:
        for _, f in upcoming[(upcoming["team_h"] == p["team"]) | (upcoming["team_a"] == p["team"])].iterrows():
            home = f["team_h"] == p["team"]
            rows.append({
                "season": season, "gw": int(f["event"]), "kickoff_time": pd.Timestamp(f["kickoff_time"]),
                "fixture": int(f["id"]), "element": p["id"], "name": p["web_name"],
                "position": POSITION[p["element_type"]], "team_id": p["team"],
                "opponent_id": int(f["team_a"] if home else f["team_h"]), "was_home": bool(home),
                "value": p["now_cost"] / 10,
            })
    return pd.DataFrame(rows).reindex(columns=COLUMNS)


def predict(horizon: int = 3) -> pd.DataFrame:
    artifact = joblib.load(MODEL_PATH)
    bootstrap = get("/bootstrap-static/")
    next_event = next((e["id"] for e in bootstrap["events"] if e["is_next"]), None)
    if next_event is None:
        return pd.DataFrame()
    events = list(range(next_event, min(38, next_event + horizon - 1) + 1))
    season = season_label(bootstrap["events"])

    players = [p for p in bootstrap["elements"] if p["element_type"] in POSITION]
    fixtures = pd.DataFrame(get("/fixtures/"))
    fixtures = fixtures[fixtures["event"].notna()]

    history = history_rows(players, fixtures, season)
    upcoming = upcoming_rows(players, fixtures, events, season)
    frame = features.build_features(pd.concat([history, upcoming], ignore_index=True))
    todo = frame[frame["total_points"].isna() & frame["gw"].isin(events)].copy()

    todo["points"] = np.clip(artifact["model"].predict(todo[artifact["features"]]), 0, None)
    by_id = {p["id"]: p for p in players}
    todo["availability"] = [
        availability(by_id[e]["status"], by_id[e]["chance_of_playing_next_round"], gw - next_event)
        for e, gw in zip(todo["element"], todo["gw"])
    ]
    todo["points"] *= todo["availability"]

    out = todo.groupby(["gw", "element"], as_index=False).agg(
        predicted_points=("points", "sum"), name=("name", "first"), position=("position", "first"))
    out = out.rename(columns={"gw": "event", "element": "player_id"})
    out["predicted_points"] = out["predicted_points"].round(2)
    out["model"] = MODEL_NAME
    out["model_version"] = artifact["version"]
    out["created_at"] = datetime.now(timezone.utc)
    return out.sort_values(["event", "predicted_points"], ascending=[True, False]).reset_index(drop=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--horizon", type=int, default=3, help="gameweeks ahead to predict (default 3)")
    parser.add_argument("--no-db", action="store_true", help="don't write to the database")
    args = parser.parse_args()

    predictions = predict(args.horizon)
    if predictions.empty:
        print("No upcoming gameweek (season over?). Nothing to do.")
        return
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    predictions.to_csv(OUTPUT, index=False)
    first = predictions[predictions["event"] == predictions["event"].min()]
    print(f"Predicted {len(predictions):,} player-gameweeks (GW{predictions.event.min()}-{predictions.event.max()}), "
          f"model {predictions.model_version.iloc[0]}")
    print(first.head(10)[["name", "position", "predicted_points"]].to_string(index=False))

    url = os.environ.get("DATABASE_URL_UNPOOLED") or os.environ.get("DATABASE_URL")
    if url and not args.no_db:
        written = db.write_predictions(url, predictions)
        print(f"Wrote {written:,} rows to ml_predictions")
    else:
        print(f"Saved to {OUTPUT} (no database URL set, or --no-db)")


if __name__ == "__main__":
    main()
