# Put FPL Lab online for free

Three free services, no credit card:

| Piece | Service | Job |
|---|---|---|
| Database + files | [Neon](https://neon.com) | Postgres that never expires, plus object storage for archived predictions |
| App | [Render](https://render.com) | Runs the Docker image; sleeps after 15 min without visitors |
| Alarm clock | [cron-job.org](https://cron-job.org) | Wakes the app every 30 min so it syncs FPL data and saves predictions before each deadline |

The Neon project is in AWS US East 1, so `render.yaml` puts the app in Render's Virginia region next to it.

---

## 1. Neon: database + file storage (already set up)

The Neon project is linked in this repo (`neon.ts` declares the services; `.neon` points at project `sweet-lake-09465108`, branch `production`). It provides:

- **Postgres** (the app's database)
- **Object Storage** bucket `assets` (private): each gameweek's saved predictions are archived there as `predictions/gwN.json`

To get the connection values on your machine:

```bash
npm install
```

```bash
neon env pull
```

This writes `.env.local` (git-ignored). The app reads it automatically when you run it locally, so `./mvnw spring-boot:run` connects to Neon with no extra setup. **Never commit `.env.local` or paste its values anywhere public.**

To change the Neon services, edit `neon.ts` and run `neon deploy`.

---

## 2. Render: deploy the app (5 min, then ~5 min build)

1. Sign up at https://render.com with your GitHub account and allow access to the `fpl-lab` repository.
2. **New → Blueprint**, pick the `fpl-lab` repo, branch `main`. Render reads `render.yaml`.
3. It asks for the secret values. Copy each one from your `.env.local`:

   | Render setting | From `.env.local` |
   |---|---|
   | `DATABASE_URL_UNPOOLED` | `DATABASE_URL_UNPOOLED` (the direct connection; the app converts it to JDBC itself) |
   | `AWS_ENDPOINT_URL_S3` | `AWS_ENDPOINT_URL_S3` |
   | `AWS_ACCESS_KEY_ID` | `AWS_ACCESS_KEY_ID` |
   | `AWS_SECRET_ACCESS_KEY` | `AWS_SECRET_ACCESS_KEY` |
   | `FOOTBALL_DATA_API_KEY` | optional, leave empty if you don't have one |

   `AWS_REGION` (us-east-1) and `SYNC_TOKEN` are filled in automatically.
4. **Apply**. The first build takes about 5 minutes. When the log shows `FPL sync done`, open the URL Render gives you, e.g. `https://fpl-lab-xxxx.onrender.com`.
5. Go to the service's **Environment** tab and copy the generated `SYNC_TOKEN` value for step 3.

Every push to `main` now redeploys automatically.

---

## 3. Keep the app awake and syncing: GitHub Actions (recommended, 2 min)

The workflow `.github/workflows/sync.yml` calls the app's sync every 30 minutes. It only needs one secret:

1. In Render, open the fpl-lab service → **Environment** → copy the value of `SYNC_TOKEN`.
2. In GitHub, open the repository → **Settings → Secrets and variables → Actions → New repository secret**:
   name `SYNC_TOKEN`, value = what you copied.
3. Test it: **Actions → Sync live app → Run workflow**. A green check means it works.

GitHub may delay scheduled runs by a few minutes, which is fine here. (GitHub pauses scheduled workflows in a
repository with no activity for 60 days; pushing any commit, or re-enabling the workflow, restarts them.)

## 3b. Alternative: cron-job.org (3 min)

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
- `neon bucket object list assets --recursive` shows `predictions/gwN.json` for the next gameweek.

## Free plan limits (and why they're fine here)

- **Render:** 750 free hours a month; the app sleeps when idle, so it stays well under. First visit after a quiet period waits about a minute.
- **Neon:** 1 GB storage (a whole season is a few MB) and 100 compute hours a month; the database also sleeps between syncs.
- **Memory:** the app uses about 300 MB of the 512 MB Render allows.

Check each provider's pricing page from time to time; free plans change.
