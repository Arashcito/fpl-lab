"""Write predictions to Postgres, where the Java app reads them.

Table ml_predictions: one row per model, gameweek and player. Re-running overwrites
the same rows, so the latest prediction before a deadline is what the app keeps.
"""
from __future__ import annotations

import pandas as pd
import psycopg

CREATE = """
CREATE TABLE IF NOT EXISTS ml_predictions (
    id               text PRIMARY KEY,          -- "<model>-<event>-<player_id>"
    model            text NOT NULL,
    model_version    text NOT NULL,
    event            integer NOT NULL,
    player_id        integer NOT NULL,
    predicted_points double precision NOT NULL,
    created_at       timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ml_predictions_event ON ml_predictions (event);
"""

UPSERT = """
INSERT INTO ml_predictions (id, model, model_version, event, player_id, predicted_points, created_at)
VALUES (%s, %s, %s, %s, %s, %s, %s)
ON CONFLICT (id) DO UPDATE SET
    model_version = EXCLUDED.model_version,
    predicted_points = EXCLUDED.predicted_points,
    created_at = EXCLUDED.created_at
"""


def write_predictions(url: str, predictions: pd.DataFrame) -> int:
    rows = [
        (f"{r.model}-{r.event}-{r.player_id}", r.model, r.model_version, int(r.event), int(r.player_id),
         float(r.predicted_points), r.created_at.to_pydatetime())
        for r in predictions.itertuples()
    ]
    with psycopg.connect(url) as conn, conn.cursor() as cur:
        cur.execute(CREATE)
        cur.executemany(UPSERT, rows)
    return len(rows)
