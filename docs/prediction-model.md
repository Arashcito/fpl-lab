# How FPL Lab predicts points

FPL Lab estimates **expected points (xPts)** for every player in every upcoming match, then uses those numbers to pick your best XI, your captain and your transfers.

The model is deliberately simple and transparent: every number on the site can be traced back to the formulas below. The code lives in `src/main/java/com/arash/fpllab/prediction/`.

---

## 1. Data

Synced from the official FPL API into Postgres every 30 minutes:

| Data | Source | Used for |
|---|---|---|
| Season stats per player (minutes, xG, xA, bonus, saves, cards, injury flag, penalty order) | `/bootstrap-static/` | per-90 rates, availability |
| Every player's stats in every gameweek | `/event/{gw}/live/` | recent minutes, recent form, defensive contribution points |
| All fixtures and results | `/fixtures/` | team strength, upcoming opponents |

---

## 2. Expected points for one match

For a player in one fixture:

```
xPts = appearance + goals + assists + clean sheet + goals conceded
     + saves + bonus + defensive contribution + cards
```

Each part is *(how likely it is)* × *(FPL points for it)*. Below, `share = expected minutes / 90`.

| Part | Formula | FPL points used |
|---|---|---|
| Appearance | `1 × P(plays) + 1 × P(60+ mins)` | 1 for playing, +1 for 60+ |
| Goals | `xG90 × share × attack multiplier × goal points` | GKP 10, DEF 6, MID 5, FWD 4 |
| Assists | `xA90 × share × attack multiplier × 3` | 3 |
| Clean sheet | `CS points × P(60+ mins) × e^(−λ)` | GKP/DEF 4, MID 1, FWD 0 |
| Goals conceded | `−1 × P(60+ mins) × E[⌊N/2⌋]`, N ~ Poisson(λ) | −1 per 2 conceded (GKP/DEF) |
| Saves | `E[⌊S/3⌋]`, S ~ Poisson(saves90 × share × opponent attack) | +1 per 3 saves (GKP) |
| Bonus | `bonus90 × share` | bonus points |
| Defensive contribution | `DC points per 90 × share` | 2 pts when the threshold is reached (DEF/MID/FWD) |
| Cards | `−1 × yellows per 90 × share` | −1 per yellow |

- **λ (lambda)** is the number of goals the player's team is expected to concede in this match (see section 4).
- `e^(−λ)` is the Poisson probability of conceding **zero** goals, i.e. the clean sheet chance.
- `E[⌊N/2⌋]` is the expected value of "goals conceded divided by 2, rounded down", computed exactly from the Poisson distribution, because FPL deducts 1 point per **2** goals.

In a **double gameweek** the points of both matches are added. In a **blank gameweek** the player gets 0.

---

## 3. Per-90 rates (what the player does when on the pitch)

### Shrinkage toward the position average

A player with 150 minutes and one lucky goal shouldn't be rated like Haaland. Every per-90 rate starts from 270 minutes (3 full matches) of the **average player at his position** (averaged over players with 450+ minutes):

```
rate90 = (player total + position average90 × 270/90) / (player minutes + 270) × 90
```

A new player stays close to the average; a regular starter with 900+ minutes is mostly his own numbers.

This is applied to xG, xA, bonus, saves, yellow cards and defensive contribution points.

### Form

Attacking rates blend the whole season with the last 6 matches:

```
xG90 = 0.6 × season xG90 + 0.4 × last-6-matches xG90      (same for xA90)
```

---

## 4. Team strength and fixture difficulty

For each team, from all finished matches:

```
attack rate  = 0.7 × xG per game          + 0.3 × goals per game
defence rate = 0.7 × xG conceded per game + 0.3 × goals conceded per game
```

xG is weighted more because it predicts future goals better than actual goals. A team's xG in a match is the sum of its players' xG that gameweek.

Each rate is shrunk toward the league average with 4 "average" games, then divided by the league average:

```
shrunk rate = (rate × games + league average × 4) / (games + 4)
attack  = shrunk attack rate  / league average     (1.2 = scores 20% more than average)
defence = shrunk defence rate / league average     (1.2 = concedes 20% more than average)
```

For team **T** playing opponent **O**:

```
expected goals for T     = league average × attack(T) × defence(O) × venue(T)
venue = 1.10 at home, 0.90 away

attack multiplier (used for goals/assists) = defence(O) × venue(T)
λ (goals T concedes)                       = league average × attack(O) × defence(T) × venue(O)
```

The player's xG90 already reflects his own team's attack against average opposition, so only the opponent and the venue adjust it.

---

## 5. Minutes: will he play?

Looks at the team's **last 6 matches**, with the most recent weighted 1, then 0.85, 0.85², … (recent matches matter more):

```
P(plays)     = weighted share of those matches where he played at all
P(60+ mins)  = weighted share where he played 60+ minutes
E[minutes]   = weighted average minutes per match
```

(In a double gameweek his minutes are split evenly across both matches.)

### Injuries and suspensions

All three numbers are multiplied by an **availability** factor from FPL's flag:

| FPL flag | Next gameweek | Later gameweeks |
|---|---|---|
| No flag | 100% | 100% |
| "75% chance of playing" | 75% | halfway back to 100% (87.5%) |
| Doubtful, no % | 75% | 87.5% |
| Injured / suspended / unavailable | 0% (or FPL's %) | unchanged |

---

## 6. Best XI and captain

For each gameweek the optimiser picks:

- the goalkeeper with the most xPts,
- the strongest legal outfield ten (DEF 3–5, MID 2–5, FWD 1–3),
- **captain** = highest xPts in the XI (counted twice), **vice** = second highest.

---

## 7. Transfers

**Squad value** over the planning window (default 3 gameweeks):

```
value = Σ over gameweeks ( best XI xPts + captain xPts + 0.1 × bench xPts )
```

The bench gets a small weight because it only scores when a starter misses out.

**Search:**

- **1 transfer:** every squad player × the top 40 players at the same position.
- **2 transfers:** every pair of squad players × the top 15 at each position.
- Only moves you can afford: `price in ≤ bank + selling price out`.
- Never more than 3 players from one club.

**Scoring a plan:**

```
net gain = value(new squad) − value(current squad) − 4 × (transfers beyond your free transfers)
```

**Roll or transfer?** Using a free transfer only makes sense if it's worth at least **1.0 expected point** over the window; otherwise the advice is to roll it (you can bank up to 5). With 5 already banked, any positive gain is taken.

---

## 8. FPL rules used

- **Selling price:** you keep half of any price rise, rounded down to £0.1m, and take any fall in full:
  `selling = now ≤ bought ? now : bought + ⌊(now − bought) / 2⌋`
- **Purchase price:** from the transfer that brought the player in, or his price when your team was created.
- **Free transfers:** 1 per gameweek after your first, roll over up to 5; Wildcard and Free Hit weeks don't use any. This is an estimate (FPL doesn't publish it), so you can override it on the page.

---

## 9. Measuring accuracy

Before every deadline (after each 30-minute sync, until the deadline passes) FPL Lab saves the next-gameweek prediction of every player from two models:

- **fpl-lab**: this model
- **fpl-official**: FPL's own `ep_next` estimate, as a benchmark

Once the gameweek is played, both are compared with the real points (`/accuracy.html`, `/api/accuracy`). Only players who played at least one minute count for error and ranking.

| Metric | Formula | Better |
|---|---|---|
| Average error (MAE) | `mean |predicted − actual|` | lower |
| Bias | `mean (predicted − actual)` | close to 0 |
| Ranking (Spearman) | correlation of the predicted order with the real order (ties share their average rank) | closer to 1 |
| Top 10 picks | average real points of the 10 highest-predicted players | higher |
| Captain | real points of the single highest-predicted player | higher |

A future ML model is added as a third model name and judged on exactly the same numbers.

Note: predictions are only saved while the app is running before a deadline, so past gameweeks can't be scored retroactively (that would use information from after the deadline).

## 10. Known limitations (and what the ML model should improve)

- Bonus and defensive contributions don't depend on the opponent.
- Home/away factors are fixed league-wide values, not per team.
- Set pieces other than penalties aren't modelled separately (they're only inside xG/xA).
- Players who change clubs mid-season are rated with their current team's history.
- Suspensions are assumed to last the whole planning window.

A machine-learning model now runs alongside this formula (see [ml/README.md](../ml/README.md)). Its predictions are saved before each deadline as a third model on the Accuracy page. If it proves better over several gameweeks, it can replace sections 2–5 while the optimiser (sections 6–7) stays the same: it only needs xPts per player per gameweek.
