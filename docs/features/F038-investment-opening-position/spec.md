# F038 — Investment Opening Position

**Status**: built.

## Summary
Lets a user record a holding's starting quantity and average cost without moving cash: an `InvestmentOpeningPosition` (as-of date, quantity, average unit price, optional current value) that feeds cotas held, amount contributed, the monthly `units`/`contributed` series, the needs-snapshot flag and the Investments report exactly as a `BUY` line dated on its as-of date would. Today a holding's quantity and cost basis can only come from a trade confirmation, which always moves cash (ADR 0012, ADR 0024), so a user registering an existing portfolio has to bend a cash account whose opening balance is immutable (F003). See [ADR 0033](../../adr/0033-opening-position-as-cashless-holding-start.md), which this spec implements, and PRD §5.15, §6.6, §6.9.

The one-step "add FII" flow (ticker, segment, cotas, average price, current value) is the optional "Opening position" section of the create-product form; an existing holding gets the same data from a card on the product detail page. Net worth, cash accounts, dividends and the holding close guard are unchanged.

## Scope
- Backend: `InvestmentOpeningPosition` aggregate (`domain/investmentopeningposition`), `InvestmentOpeningPositionService`, migration `V27__investment_opening_positions.sql`, `PUT`/`GET`/`DELETE /api/investment-holdings/{holdingId}/opening-position`, an optional `openingPosition` on `POST /api/investment-products`, the date-order guard in `TransferService`, and the extra source in `FiiPortfolioQuery`, `InvestmentValueSeriesQuery`, `InvestmentSnapshotFreshnessQuery`, `RealHasHoldingHistoryChecker` and `InvestmentsReportGenerator`.
- Frontend: the optional "Opening position" section of `InvestmentProductForm`, an `OpeningPositionCard` (add/edit/delete) on `InvestmentProductDetailPage`, the API module, query hooks and MSW handlers, Activity-page labels, problem-type messages; English and Brazilian Portuguese.
- Docs: ADR 0033 and PRD §3/§5.4/§5.5/§5.8/§5.13/§5.14/§5.15/§6.6/§6.9/§9 (written with this spec), F028's spec and plan (the opening position is a read site of the split adjustment), `docs/features/README.md`.
- Out of scope: see [Non-goals](#non-goals).

## Decisions
See ADR 0033 for the full rationale. Implementation-level decisions not already in the ADR:

| Decision | Why |
|---|---|
| `PUT /api/investment-holdings/{holdingId}/opening-position` creates (`201`) or replaces (`200`) the position, keyed by the holding. There is no position id in any URL. | At most one per holding, so the holding is the natural key and a replace is the edit. Rejected: `POST` + `PUT /{id}`, which would need a second "already exists" `409` for a case that a replace already handles. |
| The current value is never stored on the position. When the request carries `currentValue`, `InvestmentSnapshotService.record(holdingId, asOfDate, currentValue)` runs in the same transaction (replacing a same-day snapshot). When it is absent, no snapshot is written or touched, including on an edit that moves `asOfDate`. | Keeps the snapshot the sole source of value (ADR 0012) and reuses the exact path a trade's `resultingBalance` uses (`TransferService.applyLineEffects`). Rejected: storing it and deriving the snapshot, which would let two figures disagree. |
| A future `asOfDate` (and a negative `currentValue`, which the snapshot domain would otherwise reject as a bare `IllegalArgumentException`) is a `400` raised in `InvestmentOpeningPositionService` against the injected `Clock`, not in the domain constructor. | The domain has no clock; same split as F028's `effectiveDate`. |
| Domain invariant violations (non-positive `quantity`/`unitPrice`, null date) surface as `InvalidOpeningPositionException` (`400`), translated from the domain's `IllegalArgumentException` in the service. | F027/F028 precedent: a bare `IllegalArgumentException` would reach `GlobalExceptionHandler`'s catch-all as a `500`. |
| Create, replace and delete all require the holding to be open (`InvestmentHoldingClosedException`, `409`, existing). | Same as a trade on a closed holding; a closed holding's history is frozen. |
| The date-order guard lives in `TransferService.requireValidHoldings` (create and edit of a trade confirmation; it resolves the holdings and is now also passed the transfer date) and in `InvestmentOpeningPositionService` (the other direction). It is not a database constraint. | The rule spans two tables, and the app is single-user, so a race between the two writes is not a case worth a trigger. Both directions use the same comparison: a position's `asOfDate` must be `<=` every trade date on its holding (same day allowed). |
| Absence of a position is `404` `InvestmentOpeningPositionNotFoundException`; the frontend query maps exactly that problem type to `null` (the empty state). | The holding response stays unchanged. Rejected: embedding a nullable `openingPosition` in `InvestmentHoldingResponse`, which would change a DTO several pages and tests already use. |
| In `InvestmentValueSeriesQuery`, the position counts for `units` from its as-of month on, and its `quantity × unitPrice` is added to `contributed` for its as-of month only. `units` is `null` only when the product has neither trade lines nor positions. | The monthly `contributed` figures then sum to `FiiPortfolioQuery.amountContributed`. |
| In `InvestmentSnapshotFreshnessQuery` the position is treated like a trade line dated `asOfDate` for its holding: stale when there is no snapshot on or before the date, or the latest one is dated before `asOfDate`. A snapshot on `asOfDate` is fresh. | Same rule as for lines (a snapshot on the line's own date is fresh), so recording a position with a current value is fresh. |
| The Investments report gets a third section, `OPENING POSITIONS`, columns `product_name`, `account_name`, `as_of_date`, `quantity`, `unit_price`, `current_value`, filtered by `dateFrom`/`dateTo` on `as_of_date`. `current_value` is the holding's snapshot dated exactly `as_of_date`, empty when there is none. | The position stores no value, so the column reports the snapshot the same step wrote, if still there. Rejected: rows in `TRADE ACTIVITY` with an `OPENING` side, which would fill from/to-account columns that do not apply. |
| `POST /api/investment-products` with `openingPosition` runs in the existing `InvestmentProductService.create` transaction: the returned holding of `holdingService.create(...)` (currently discarded) is passed to the position service. Any failure rolls back product, holding, position and snapshot. | The transaction is already a multi-write use case (backend `CLAUDE.md`). The failures are `RuntimeException`s, which roll back by default. |
| The create-product form shows the "Opening position" section for every product, collapsed by default, with copy that speaks of cotas. | The backend is generic (ADR 0033), and a per-sub-category gate would repeat the work `relevantInvestmentFields` already does for ticker and segment. |

## Backend

### Domain
- `domain/investmentopeningposition/InvestmentOpeningPosition.java`: aggregate (`id`, `holdingId`, `asOfDate`, `quantity`, `unitPrice`), factory `create(id, holdingId, asOfDate, quantity, unitPrice)`, `reconstitute(...)`, `edit(asOfDate, quantity, unitPrice)`. Invariants: all fields non-null (`id`/`holdingId` fail with `NullPointerException`; `asOfDate`/`quantity`/`unitPrice` with `IllegalArgumentException`, so the service translates every value violation), `quantity > 0`, `unitPrice > 0` (repeated at the DTO as `@NotNull @Positive @Digits(integer = 11, fraction = 8)` and in the DB as `CHECK`). `amountContributed()` returns `quantity × unitPrice` at full precision, never rounded here. `quantity` and `unitPrice` are padded (never rounded) to the column scale of 8 decimals, so the value in memory equals the one read back and replacing a position with the same figures is a no-op for the audit diff, which compares with `equals`. `toAuditSnapshot()` returns a `LinkedHashMap` of `holdingId` (string), `asOfDate`, `quantity`, `unitPrice`.
- `domain/investmentopeningposition/InvestmentOpeningPositionRepository.java`: port: `save`, `findByHoldingId`, `findByHoldingIds(Collection)`, `findAll`, `deleteById`, `existsByHoldingId`.
- `TransferTradeLineRepository` gains `Optional<LocalDate> findEarliestDateByHolding(UUID productId, UUID investmentAccountId)` (the earliest `transfers.date` of a line for that product whose transfer has the account on either side).

### Application
- `application/investmentopeningposition/InvestmentOpeningPositionService`:
  - `@Transactional upsert(holdingId, asOfDate, quantity, unitPrice, currentValue)` returning a `RecordedOpeningPosition(position, created)`: `404` `InvestmentHoldingNotFoundException` if the holding is unknown; `409` `InvestmentHoldingClosedException` if closed; `400` `InvalidOpeningPositionException` for a future `asOfDate` or a domain violation; `409` `OpeningPositionAfterTradesException` if `asOfDate` is after `findEarliestDateByHolding`. Creates or replaces, then, if `currentValue != null`, `InvestmentSnapshotService.record(holdingId, asOfDate, currentValue)`. Audit: `AuditRecorder.recordCreate/recordUpdate(AuditEntityType.INVESTMENT_OPENING_POSITION, id, null, snapshot)` in the same method.
  - `@Transactional delete(holdingId)`: `404` `InvestmentHoldingNotFoundException`, `404` `InvestmentOpeningPositionNotFoundException`, `409` `InvestmentHoldingClosedException`; `recordDelete`; snapshots untouched.
  - `findByHoldingId(holdingId)`: read-only; `404` as above.
- New exceptions (each `@ResponseStatus`, problem type derived from the class name): `InvestmentOpeningPositionNotFoundException` (`404`), `InvalidOpeningPositionException` (`400`), `OpeningPositionAfterTradesException` (`409`), `TradeBeforeOpeningPositionException` (`409`, in `application/transfer`, where it is thrown).
- `TransferService.requireValidHoldings` (create and edit of a trade confirmation): for every line's resolved holding, if it has a position with `asOfDate` after the transfer `date`, throw `TradeBeforeOpeningPositionException`.
- `InvestmentProductService.create(...)`: gains an optional `OpeningPositionInput` (a record in `application/investmentproduct`) on a new 8-argument overload, the one the controller calls; after the holding is created it calls `InvestmentOpeningPositionService.upsert(...)` inside the same `@Transactional` method.
- `FiiPortfolioQuery.rowFor`: inject the position repository; for the product's holdings, each position with `asOfDate <= asOf` adds `quantity` to `cotasHeld` and `quantity × unitPrice` to `amountContributed`.
- `InvestmentValueSeriesQuery`: as in the Decisions row above.
- `InvestmentSnapshotFreshnessQuery.staleHoldingIds`: positions are a second source beside lines, per the Decisions row above.
- `infrastructure/investmentholding/RealHasHoldingHistoryChecker`: `hasHistory` is also true when `existsByHoldingId` finds a position (its javadoc requires a new FK table to be added here, else a hard delete would fail on the foreign key as a `500` instead of the intended `409`).
- `application/report/InvestmentsReportGenerator`: the `OPENING POSITIONS` section, nullable cells via `Arrays.asList` and typed cells (`docs/conventions/csv-output.md`).
- `application/auditlog/AuditEntityType`: new `INVESTMENT_OPENING_POSITION` (the `audit_log.entity_type` column is `varchar(40)`, no migration for the enum).
- Not touched here: the split adjustment (see F028's amended spec), `AccountBalanceQuery`, net worth, allocation, the close guard.

### Persistence
- Migration `V27__investment_opening_positions.sql`: `investment_opening_positions` (`id uuid primary key`, `holding_id uuid not null references investment_holdings (id)` with `CONSTRAINT uq_investment_opening_positions_holding UNIQUE (holding_id)`, `as_of_date date not null`, `quantity numeric(19,8) not null check (quantity > 0)`, `unit_price numeric(19,8) not null check (unit_price > 0)`, `created_at timestamptz not null`, `last_modified_at timestamptz not null`). No seed, no backfill, no `ON DELETE CASCADE` (the checker blocks a holding delete while a position exists).
- `infrastructure/persistence/investmentopeningposition/InvestmentOpeningPositionJpaEntity` (extends `AuditableEntity`), a package-private Spring Data repository, `InvestmentOpeningPositionRepositoryAdapter`.
- `TransferTradeLineRepositoryAdapter`: the `findEarliestDateByHolding` query (`transfer_trade_lines` joined to `transfers`).
- Migration test (`docs/conventions/backend-testing.md` pattern, as `InvestmentHoldingsMigrationTest`): the unique constraint rejects a second position for a holding; the two `CHECK`s reject zero and negative values.

### API
- `PUT /api/investment-holdings/{holdingId}/opening-position`: body `{asOfDate, quantity, unitPrice, currentValue?}` (`currentValue` `@PositiveOrZero @Digits(integer = 17, fraction = 2)`); `201` created, `200` replaced; `400` (validation, future date), `404` (holding), `409` (closed holding, position after trades). Response `{id, holdingId, asOfDate, quantity, unitPrice, amountContributed}`.
- `GET /api/investment-holdings/{holdingId}/opening-position`: `200` the response above, `404` unknown holding or no position (distinct problem types).
- `DELETE /api/investment-holdings/{holdingId}/opening-position`: `204`; `404`, `409` as above.
- `POST /api/investment-products`: `CreateInvestmentProductRequest` gains an optional `@Valid OpeningPositionRequest openingPosition` (the same fields as the `PUT` body). `201` as today; any failure of the position rolls back the product.
- `POST`/`PUT /api/transfers` trade confirmations: new `409` `TradeBeforeOpeningPositionException`. `@ApiResponse` entries on every new status.
- Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types -- --offline`).

## Frontend
- `src/api/investments/investmentOpeningPositions.ts` and `investmentOpeningPositionsQueries.ts`: `get`/`put`/`delete` functions, `useOpeningPosition(holdingId)` (the `investment-opening-position-not-found` problem type is mapped to `null` in `getOpeningPosition`, which the hook calls, so the empty state is data; no retry), `useSaveOpeningPosition`, `useDeleteOpeningPosition`, the key factory; mutations invalidate the holding's position, the product's holdings, snapshots, trade-line, value-series and FII-portfolio queries (coarse invalidation, ADR 0016). Hand-written types only where `schema.ts` has no generated one.
- `src/api/investments/investmentProducts.ts`: the create request type gains `openingPosition?`.
- `src/mocks/handlers/investmentOpeningPositions.ts` (registered with the other handlers; its store starts empty and `seedOpeningPosition` is exported standalone, `docs/conventions/frontend-testing.md`), and the products handler accepts an `openingPosition` on create.
- `src/features/investmentProducts/InvestmentProductForm.tsx` and `InvestmentProductsSection.tsx`: a collapsible "Opening position" section (as-of date defaulting to today, cotas, average price, current value; the four inputs are `OpeningPositionFields`, shared with the dialog, and the draft helpers live in `openingPositionDraft.ts`) shown only on create, for every product; once anything is entered beyond the default date (a different as-of date, cotas, average price or current value), cotas and average price are required, and current value stays optional with helper text saying that skipping it leaves the holding flagged as needing a snapshot. The payload builder (~`InvestmentProductsSection.tsx:115`) adds `openingPosition`.
- `src/features/investmentProducts/OpeningPositionCard.tsx` and `OpeningPositionDialog.tsx`: on `InvestmentProductDetailPage`, directly below the current-value card of the selected holding, show as-of date, cotas, average price and contributed amount with Edit and Delete (confirm dialog), or an "Add opening position" action when there is none; actions hidden for a closed holding. Loading, empty and error states follow the surrounding cards.
- `src/api/core/apiError.ts` (`PROBLEM_TYPE_MESSAGES`) and `src/i18n/locales/{en,pt-BR}/errors.json`: messages for the four new problem types; the existing `investment-holding-closed` message is reworded to cover opening-position changes as well as trades (it used to speak only of a transfer).
- `src/i18n/format.ts`: `formatQuantity` (0 to 8 decimals, none padded) and `formatUnitPrice` (2 to 8) for the card, since `formatNumber`/`formatMoney` fix the decimals.
- `src/features/activity/ActivityPage.tsx`, `activityEnumLabels.ts`, `fieldLabels.ts` and `activity.json` (both locales): the new entity type and its fields.
- `src/i18n/locales/{en,pt-BR}/investmentProducts.json`: all new strings in both locales (`localeParity.test.ts` enforces parity).

## Docs
- ADR 0033 and its index row; ADR 0012 and 0024 status lines (done with this spec).
- `docs/PRD.md`: §3, §5.4, §5.5, §5.8, §5.13, §5.14, §5.15 (new), §6.6, §6.9, §9 (done with this spec); the §5.15 "Specified, not built" line is removed by the implementing PR.
- `docs/features/F028-investment-splits-backend/{spec,plan}.md`: the position is a read site of `InvestmentSplitAdjustment`, wired by whichever of F038 and F028 ships second.
- `docs/conventions/backend-structure.md` (new aggregate), `docs/conventions/accounts-and-investments.md` (a position counts as holding history and is a read site of the split adjustment).
- `CHANGELOG.md`: the `[Unreleased]` entry ships with the implementing PR (root `CLAUDE.md`).

## Non-goals
- More than one opening position per holding, or a re-basing/"reset" of a position: a later change in cotas is a trade.
- Storing the current value on the position: the snapshot is the only source of value.
- A guided multi-holding import wizard, an editable account opening balance and a calculator: the other options considered in ADR 0033's Context; none is part of this feature.
- Taxes or fees on a position, and any gain/loss or return math: record-only, as for trades (PRD §3).
- Cash-in-lieu and any split handling beyond the read-site wiring (F028/ADR 0025).
- Opening positions in the data export beyond the Investments report section (F013 is superseded by F030).

## Open questions
None.

## Dependencies
F008, F009, F022 (holdings and snapshots), F025 (audit), F026 (`FiiPortfolioQuery`), F027 (trade lines and `TransferService`), F030 (the Investments report), F031 (problem types), F033 (i18n), F019 (query hooks). Touches without changing their behaviour: F028/F029 (spec amended: the position is a read site of the split adjustment, wired by whichever ships second), F010 (net worth is unchanged).
