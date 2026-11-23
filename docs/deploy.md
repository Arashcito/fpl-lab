# Put FPL Lab online for free

Three free services, no credit card:

| Piece | Service | Job |
|---|---|---|
| Database | [Neon](https://neon.com) | Postgres that never expires |
| App | [Render](https://render.com) | Runs the Docker image; sleeps after 15 min without visitors |
| Alarm clock | [cron-job.org](https://cron-job.org) | Wakes the app every 30 min so it syncs FPL data and saves predictions before each deadline |

Pick the **same region** for Neon and Render so the app talks to the database quickly. The steps below use US East (Virginia); Frankfurt works too if most of your users are in Europe.

---

## 1. Neon: create the database (5 min)

1. Sign up at https://neon.com with your GitHub account.
2. **Create project**: name `fpl-lab`, Postgres version 16 or newer, region **AWS US East 1 (N. Virginia)**.
3. Open **Connect** on the project dashboard.
   - Turn **Connection pooling off** (Hibernate works best with a direct connection).
   - You'll see a connection string like:
     ```
     postgresql://neondb_owner:AbC123xyz@ep-cool-name-123456.us-east-1.aws.neon.tech/neondb?sslmode=require
     ```
4. Split it into the three values Render needs:

   | Render setting | Value from the example above |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://ep-cool-name-123456.us-east-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USER` | `neondb_owner` |
   | `DB_PASSWORD` | `AbC123xyz` |

   `DB_URL` = `jdbc:postgresql://` + everything after the `@`, including `?sslmode=require`.

Keep these somewhere private for step 2. **Never commit them or paste them in chat.**

---

## 2. Render: deploy the app (5 min, then ~5 min build)

1. Sign up at https://render.com with your GitHub account and allow access to the `fpl-lab` repository.
2. **New → Blueprint**, pick the `fpl-lab` repo, branch `main`. Render reads `render.yaml`.
3. It asks for the values marked secret. Fill in:
   - `DB_URL`, `DB_USER`, `DB_PASSWORD` from step 1
   - `FOOTBALL_DATA_API_KEY`: optional, leave empty if you don't have one
   - `SYNC_TOKEN` is generated automatically.
4. **Apply**. The first build takes about 5 minutes. When the log shows `FPL sync done`, open the URL Render gives you, e.g. `https://fpl-lab-xxxx.onrender.com`.
5. Go to the service's **Environment** tab and copy the generated `SYNC_TOKEN` value for step 3.

Every push to `main` now redeploys automatically.

---

## 3. cron-job.org: wake the app every 30 minutes (3 min)

A sleeping app can't run its own timer, so an outside service wakes it.

1. Sign up at https://cron-job.org (free).
2. **Create cronjob**:
   - **Title:** `FPL Lab sync`
   - **URL:** `https://fpl-lab-xxxx.onrender.com/api/sync?token=YOUR_SYNC_TOKEN`
   - **Schedule:** every 30 minutes
   - **Advanced → Request method:** `POST`
3. Save.

If the app was asleep, the first call can time out while it wakes up (up to a minute). That's fine: waking up triggers a sync on its own, and the next call succeeds.

---

## 4. Check it works

- `https://fpl-lab-xxxx.onrender.com/api/sync/status` shows the last sync time.
- The **Accuracy** page says "Tracking has started" with the next gameweek.
- Neon's dashboard shows tables such as `players` and `prediction_snapshots`.

## Free plan limits (and why they're fine here)

- **Render:** 750 free hours a month; the app sleeps when idle, so it stays well under. First visit after a quiet period waits about a minute.
- **Neon:** 1 GB storage (a whole season is a few MB) and 100 compute hours a month; the database also sleeps between syncs.
- **Memory:** the app uses about 300 MB of the 512 MB Render allows.

Check each provider's pricing page from time to time; free plans change.
