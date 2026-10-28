# Measuring performance

Read when a change claims a speed-up or a scaling property: a benchmark, a statements-per-request count, a time to first content, or a baseline-versus-current comparison (issue #189 is the worked example). The per-test statement pin that guards an N+1 in the suite is a different tool: [backend-testing.md](backend-testing.md).

## Run it

```
scripts/bench/run.sh [--sizes "1000 5000 25000"] [--ttfc] [--baseline <git-ref>] [--keep]
```

It starts a disposable Postgres (the image tag read from `docker-compose.yml`, on a tmpfs), builds and starts the backend with the `prod` profile on free ports, seeds the ledger at each size, measures, and tears everything down by PID. Nothing of the dev or prod stack is touched, so it runs beside them. `--ttfc` also builds the frontend and times first content in headless Chromium (needs `frontend/node_modules` and `npx playwright install chromium`); `--baseline <ref>` builds that commit as a second backend on the same database, times it the same way and compares every response byte for byte. A 5,000-row run with the baseline takes about ten minutes, mostly Gradle.

Paste the output into the PR's Evidence and name what differs from the reference setup below: the machine, the Postgres version, the seed.

| Tool | Measures |
|---|---|
| `bench.mjs` | one warm-up plus five timed calls per endpoint (accounts list, net worth, account detail, the trend modes, three reports, all 11 reports as a zip): median and max ms |
| `statements.mjs` | SQL statements Postgres runs for ONE request, to show a read does not grow with the ledger |
| `ttfc.mjs` | ms from navigation start to the marker content (dashboard balances, accounts row, Generate button), fresh browser context per run, next to the data-free login page as the floor |
| `compare.mjs` | identical requests to the baseline and the current backend; a zip entry by entry, a PDF with its `/CreationDate` and `/ID` blanked (the writer stamps the generation time: even one backend differs from itself between two identical requests) |
| `seed-base.sql`, `seed-ledger.sql` | five ledger accounts, an `INVESTMENT` account with 5 holdings and 285 snapshots, then N ledger rows: 80% transactions, 20% transfers (35% card payments, 2% buys with a trade line), accented text, 30% notes, spread over about five years ending today. No `audit_log` rows: no benchmarked read touches them |

## Reference results (after #189)

Medians in ms from `scripts/bench/run.sh --sizes "5000 25000" --ttfc`: Linux on WSL2 (12 CPUs), Postgres 18.6, headless Chromium, the production frontend build behind a same-origin proxy (not nginx). Two runs of the same setup differ by a few ms.

| | 5k rows | 25k rows |
|---|---|---|
| accounts list / net worth now / account detail | 18 / 15 / 9 | 20 / 17 / 7 |
| trend CHANGE_DATE, 5 years | 24 | 37 |
| annual-summary report | 18 | 35 |
| all 11 reports (zip) | 163 | 553 |
| SQL statements: accounts list / net worth now / trend 5y | 15 / 14 / 26 | 15 / 15 / 27 |
| time to first content: dashboard / accounts page / reports form | 436 / 434 / 399 | 439 / 442 / 399 |

The statement counts are the same at 1k, 5k and 25k rows up to one statement of connection setup; a count that grows with the ledger is the regression to look for. The login page, which loads no data, takes about 376 ms on that machine: that is the floor under every page, so judge a page against it. Before #189 (commit `202805f`) the accounts list took about 400 ms at 5k rows and 2 s at 25k, with 3,992 statements at 5k.

## Gotchas

Each of these produced a plausible but wrong number before it was caught.

- **Count statements through a per-database setting, then reconnect the pool.** A `-c log_min_duration_statement` on the server's command line beats `ALTER SYSTEM`, so the log stays silent and the count reads 0. `ALTER DATABASE ... SET` beats the command line but only for new sessions: terminate the pooled connections (`statements.mjs` does both). A count of 0 is a broken setup, not a fast request.
- **Record browser timings inside the page.** Playwright's `locator.waitFor` polls with a backoff, which rounds every result up to about 820 ms: every page read the same, the login page included. `ttfc.mjs` stamps `performance.now()` from a `MutationObserver` the moment the marker exists.
- **Refresh the cookie jar from every response.** The backend rotates the CSRF token at login, so the first POST after it is answered 403 unless the client re-reads `XSRF-TOKEN` (`lib.mjs` does).
- **Seed a trade transfer with `taxes = 0`.** The domain rejects a transfer with a trade confirmation and no taxes, and every report that loads all transfers then answers 500.
- **Seed with dates relative to today** (`current_date - n`), so the trend ranges and the as-of dates keep covering the data as the calendar moves.
- **Stop processes by PID.** `pkill -f '<pattern>'` can match the shell that runs it and abort the rest of a cleanup; `run.sh` records each PID.
- **The baseline shares the database, so seed once.** It runs against the schema the current code migrated; Flyway ignores future migrations by default, so an older baseline starts, but a baseline from before a destructive migration will not. Pick a baseline with the same schema.
- **Never seed a database you care about.** The 25k seed replaces the whole ledger; the harness only ever uses its own container.
