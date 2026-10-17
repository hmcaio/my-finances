# 0030. Postgres `unaccent` for accent-insensitive text search

Status: Accepted
Date: 2026-10-17

## Context
F037 adds a free-text search box to the unified Ledger page. Descriptions and notes are typed in Brazilian Portuguese ("café", "açaí", "pão"), and a user typing "cafe" expects to find "Café da manhã". A plain case-insensitive substring match misses every accented row, which for this user is a common case, not an edge.

Accent folding cannot be done after the fact in the application layer without giving up the repository's filtering and the Ledger's row cap: the search narrows rows *before* the cap and the income/expense summary are computed, so it has to run in the SQL predicate. The database is PostgreSQL, pinned to `postgres:17.11-alpine` in `docker-compose.yml`, `docker-compose.prod.yml`, the backup sidecar's Dockerfile and the Testcontainers configuration. No migration so far has enabled a Postgres extension.

## Decision
- **Enable the `unaccent` extension** (PostgreSQL contrib, marked trusted) through a Flyway migration, `CREATE EXTENSION IF NOT EXISTS unaccent;`, and match the search text with `lower(unaccent(column)) LIKE lower(unaccent(:pattern)) ESCAPE '\'` on `description` OR `additional_notes`, built as a JPA `Specification` clause through `CriteriaBuilder.function("unaccent", ...)`. The `%`, `_` and `\` characters in the user's text are escaped in Java before the pattern is built, so they match literally.
- The same clause is added to both the transaction and the transfer repository adapters; it is the first (and for now only) place the application relies on a Postgres extension.
- **No index.** `unaccent(text)` is declared `STABLE`, not `IMMUTABLE`, so it cannot back an expression index without a wrapper function. The query always carries a date range (a month, or an explicit range) and the existing `date`-leading indexes narrow it first; an "All time" search is a sequential scan over a single user's rows, which this local app can afford. Revisit with an immutable wrapper and a `pg_trgm` index only if a measured search is slow.
- **The in-memory fakes** (`FakeTransactionRepository`, `FakeTransferRepository`) fold accents with `java.text.Normalizer` (NFD, strip combining marks) so domain and application unit tests exercise the behaviour. The fold is not identical to `unaccent`'s rule file for non-decomposable letters (for example `ß`); the real behaviour is asserted by the repository integration tests against Postgres, not by the fakes.
- **No other use** of the extension is made implicitly: a future feature that needs `unaccent` elsewhere reuses it; one that needs a different extension gets its own ADR.

## Alternatives considered
- **A stored, normalized search column** (`search_text`, maintained on every write and backfilled). Rejected: write-path coupling on both aggregates, a backfill migration, and a second copy of the text that can drift from `description`/`additional_notes`; the extension needs no data change.
- **Hand-rolled `translate(column, 'áàâã...', 'aaaa...')`.** Rejected: no extension, but the character table is ours to maintain and silently incomplete (`ç`, `ñ`, `ü`, uppercase accents), while `unaccent`'s rules are the maintained standard.
- **`pg_trgm`.** Rejected: it adds fuzzy matching and an index, which this feature does not need, and it does not fold accents by itself (it would still need `unaccent`).
- **Filtering in Java after loading the date range.** Rejected: it loads every row in the scope for every keystroke-debounced search and breaks the cap-then-summarize ordering that keeps the Ledger summary correct when the list is truncated.
- **Accent-sensitive v1, add folding later.** Rejected by the user's call during design: for a Portuguese-language user a search that misses "café" when typing "cafe" would be considered broken.

## Consequences
- **A new database prerequisite.** Any Postgres image the stack moves to must ship the contrib modules (the official `postgres` images, including the alpine variants, do). `scripts/check-versions.sh` already keeps the four pinned copies of the tag in sync; the Testcontainers tag is one of them, so integration tests run against an image with the extension.
- **The migration role must be allowed to create the extension.** `unaccent` is "trusted": the database owner can create it without being a superuser, and the compose `POSTGRES_USER` is a superuser anyway. A deployment on a managed Postgres that blocks `CREATE EXTENSION` for the app role would have to enable it out of band before the migration runs.
- **Backups and restores carry the extension by reference.** `pg_dump -Fc` records a `CREATE EXTENSION unaccent` entry rather than the extension's objects, and the restore script restores into the same image family, so the restore target already has the extension files. A restore into a Postgres without contrib would report errors for that entry.
- **The `unaccent()` call is unqualified**, so it resolves through the connection's `search_path`. The repo configures no `currentSchema` override, so the extension lives in `public` and resolves; a future custom-schema setup must qualify the call or set the path.
- **CHANGELOG:** the implementing PR's entry carries an `Upgrade:` sub-line, because a migration now enables an extension on the user's existing database.
