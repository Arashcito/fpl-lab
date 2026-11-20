# FPL Lab

**Your team, your move.** Enter your Fantasy Premier League team ID and FPL Lab tells you which transfers to make, who to captain and why, based on expected points for the next few gameweeks.

## Features

- **My Team:** loads your squad, bank and free transfers, then recommends 0, 1 or 2 transfers (within budget, max 3 per club, counting −4 hits). Each suggestion comes with its reasons.
- **Captain picks:** your XI ranked by expected points for the next gameweek.
- **Fixture heatmap:** expected points for every squad player in each of the next 1–6 gameweeks.
- **Top Picks:** the best players by expected points, filterable by position and price.
- **League table:** live from football-data.org, or calculated from FPL results when no API key is set.
- **Transparent model:** click any player to see exactly where their points come from. All formulas are in [docs/prediction-model.md](docs/prediction-model.md).

## Run it

With Docker (app + Postgres):

```bash
docker compose up --build
```

Then open http://localhost:8080. The first data sync from FPL takes a few seconds.

Without Docker, start Postgres (`docker compose up db`) and run:

```bash
./mvnw spring-boot:run
```

Optional live league table: get a free key at https://www.football-data.org/client/register and put `FOOTBALL_DATA_API_KEY=your-key` in a `.env` file next to `docker-compose.yml` (it is git-ignored).

## API

| Endpoint | Description |
|---|---|
| `GET /api/my-team/{teamId}?horizon=3&freeTransfers=` | Transfer and captain advice for an FPL team |
| `GET /api/projections?horizon=3&position=MID&limit=50` | Expected points per player per gameweek |
| `GET /api/standings` | Premier League table |
| `POST /api/sync` | Sync FPL data into the database now (also runs on startup and every 30 minutes) |

## Tech

Java 21, Spring Boot 3, PostgreSQL, plain HTML/CSS/JS front end, JUnit tests (`./mvnw test` runs on an in-memory database, no setup needed).

## Roadmap

- Accuracy page: predicted vs. actual points after every gameweek
- Mini-league rival comparison
- Python ML model to replace the formula-based projections
