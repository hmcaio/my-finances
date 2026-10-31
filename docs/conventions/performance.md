# Measuring performance

Read when a change claims a speed-up or a scaling property: a benchmark, a statements-per-request count, a time to first content, or a baseline-versus-current comparison (issue #189 is the worked example). The per-test statement pin that guards an N+1 in the suite is a different tool: [backend-testing.md](backend-testing.md).

## Run it

```
scripts/bench/run.sh [--sizes "1000 5000 25000"] [--ttfc] [--baseline <git-ref>]
                     [--image-tag <tag>] [--cpus <n>] [--keep]
```

By default it runs the real `docker-compose.prod.yml` (nginx, the production images, Postgres on a named volume, the backup sidecar) as its own Compose project `mf-bench-<pid>` with `STACK_NAME` set to it, seeds the ledger at each size, measures, and removes everything it created. It builds the three images from the checkout under a unique tag (`--image-tag vX.Y.Z` measures published images instead, pulled not built). `--ttfc` times first content in headless Chromium against the same nginx (needs `frontend/node_modules` and `npx playwright install chromium`); `--baseline <ref>` builds that commit's backend as a second container on the stack's network and database, times it the same way and compares every response byte for byte. A 5,000-row run takes about five minutes with the images cached, longer with `--baseline` (a fourth image) or cold.

**It can run beside a real prod stack.** `STACK_NAME` builds the four container names and the logs and backups volume names the prod file pins under the project's own prefix (`docs/conventions/tooling.md`), so a `down -v` can only delete the run's own logs. The backups volume stays `external: true` on purpose: the run sets `BACKUP_DIR` to a scratch folder, so nothing mounts it. Before anything is created, `guard.mjs` reads the RESOLVED config (`docker compose config`) and aborts unless: every `container_name` starts with `<project>-`; every volume and network name starts with the project name and none is external; the only published port is the frontend's, on `127.0.0.1:<free port>`; every bind mount lies in the scratch directory; and every image of ours carries the unique tag. A name newly pinned in the prod file therefore fails the run instead of reaching prod (and `scripts/stack/isolation.test.mjs` fails CI first); `node --test scripts/bench/guard.test.mjs` pins the refusals. Cleanup runs `down -v` only after the guard passed, and removes only the image tags this run created.

The run still shares the machine's CPU and disk with a running prod stack: it slows it for a few minutes, and the prod stack's load adds noise to the timings. `--cpus <n>` caps the benchmark's backend, frontend and Postgres to protect prod; prod itself has no limit, so a capped run departs from it. Run it when prod is idle where you can.

Paste the output into the PR's Evidence and name what differs from the reference setup below: the machine, the seed, `--cpus`.

| Tool | Measures |
|---|---|
| `run.sh` | the whole setup, measurement and teardown (above) |
| `guard.mjs` | the pre-flight check of the resolved Compose config |
| `bench.mjs` | one warm-up plus five timed calls per endpoint (accounts list, net worth, account detail, the trend modes, three reports, all 11 reports as a zip): median and max ms |
| `statements.mjs` | SQL statements Postgres runs for ONE request, to show a read does not grow with the ledger |
| `ttfc.mjs` | ms from navigation start to the marker content (dashboard balances, accounts row, Generate button), fresh browser context per run, next to the data-free login page as the floor |
| `compare.mjs` | identical requests to the baseline and the current backend; a zip entry by entry, a PDF with its `/CreationDate` and `/ID` blanked (the writer stamps the generation time: even one backend differs from itself between two identical requests) |
| `seed-base.sql`, `seed-ledger.sql` | five ledger accounts, an `INVESTMENT` account with 5 holdings and 285 snapshots, then N ledger rows: 80% transactions, 20% transfers (35% card payments, 2% buys with a trade line), accented text, 30% notes, spread over about five years ending today. No `audit_log` rows: no benchmarked read touches them |
| `lib.mjs` | shared client plumbing: sign-in, `psql` and the Postgres log of the stack's container |

## What still differs from prod

The application code, schema, images, JDK, nginx, Postgres version and volume, Docker network and the backup sidecar are prod's. These are not:

- **The machine**: the client, the stack and headless Chromium share one machine, and Chromium renders in software. Judge browser timings against the login-page floor.
- **The data**: the seed, not real data.
- **TLS and a real network**: none, as in a local prod stack.
- **`--cpus`**: an opt-in departure, named in the output you paste.

## Reference results (after #189)

Medians in ms from `scripts/bench/run.sh --sizes "5000 25000" --ttfc`: Linux on WSL2 (12 CPUs), the production images behind nginx, Postgres 18.6 on a named volume, headless Chromium. Two runs of the same setup differ by a few ms.

| | 5k rows | 25k rows |
|---|---|---|
| accounts list / net worth now / account detail | 19 / 14 / 8 | 18 / 17 / 8 |
| trend CHANGE_DATE, 5 years | 26 | 36 |
| annual-summary report | 18 | 33 |
| all 11 reports (zip) | 151 | 519 |
| SQL statements: accounts list / net worth now / trend 5y | 15 / 14 / 26 | 15 / 16 / 27 |
| time to first content: dashboard / accounts page / reports form | 443 / 428 / 401 | 446 / 436 / 399 |

The statement counts are the same at 1k, 5k and 25k rows up to a couple of statements of connection setup; a count that grows with the ledger is the regression to look for. The login page, which loads no data, takes about 377 ms on that machine: that is the floor under every page, so judge a page against it. Before #189 (commit `202805f`) the accounts list took about 320 ms at 5k rows through the same stack, and 2 s at 25k in an earlier host-JVM run, with 3,992 statements at 5k.

## Gotchas

Each of these produced a plausible but wrong number before it was caught.

- **Count statements through a per-database setting, then reconnect the pool.** A `-c log_min_duration_statement` on the server's command line beats `ALTER SYSTEM`, so the log stays silent and the count reads 0. `ALTER DATABASE ... SET` beats the command line but only for new sessions: terminate the pooled connections (`statements.mjs` does both). A count of 0 is a broken setup, not a fast request.
- **Record browser timings inside the page.** Playwright's `locator.waitFor` polls with a backoff, which rounds every result up to about 820 ms: every page read the same, the login page included. `ttfc.mjs` stamps `performance.now()` from a `MutationObserver` the moment the marker exists.
- **Refresh the cookie jar from every response.** The backend rotates the CSRF token at login, so the first POST after it is answered 403 unless the client re-reads `XSRF-TOKEN` (`lib.mjs` does).
- **Un-escape `$$` before reusing a value from `docker compose config`.** It prints a literal `$` as `$$`, so its output can be fed back to Compose; an `--env-file` takes values as they are. The baseline backend got `AUTH_PASSWORD_HASH=$$argon2id$$...` and refused to start, because the hash is full of `$` and the app validates its format (`run.sh` un-escapes it).
- **Seed a trade transfer with `taxes = 0`.** The domain rejects a transfer with a trade confirmation and no taxes, and every report that loads all transfers then answers 500.
- **Seed with dates relative to today** (`current_date - n`), so the trend ranges and the as-of dates keep covering the data as the calendar moves.
- **The baseline shares the database, so seed once.** It runs against the schema the current code migrated; Flyway ignores future migrations by default, so an older baseline starts, but a baseline from before a destructive migration will not. Pick a baseline with the same schema.
- **Never seed a database you care about.** The 25k seed replaces the whole ledger; the harness only ever uses its own container.
