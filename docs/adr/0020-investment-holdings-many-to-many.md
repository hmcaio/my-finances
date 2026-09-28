# 0020. Model investment products and accounts as many-to-many holdings, not a 1:1 link

Status: Accepted
Date: 2026-09-28

## Context
ADR 0012 modeled `InvestmentProduct` as belonging to exactly one `INVESTMENT` account (`product.account_id`), with product names unique **per account** specifically so the same real-world instrument bought at two brokers ("Tesouro Selic 2029" at both XP and Nubank) could exist as two separate product rows.

Designing the investments page refactor (a cross-account product list, allocation by account) surfaced the cost of that choice: the same instrument held at two brokers is genuinely one thing — one category, one sub-category, one set of notes — not two independently maintained rows that happen to share a name. Every taxonomy edit, rename or note has to be repeated on both rows and can drift, and the existing category/sub-category allocation already summed across such duplicates without the user ever asking it to.

## Decision
- **`InvestmentProduct` becomes pure taxonomy**: `id`, `name` (globally unique, not per-account), `investmentCategoryId`, `investmentSubcategoryId` (optional), `additionalNotes` (optional, `MAX_ADDITIONAL_NOTES_LENGTH`). It drops `accountId` and `closedDate`.
- **A new `InvestmentHolding` aggregate** is the many-to-many link with its own lifecycle: `id`, `productId`, `accountId`, `closedDate` (nullable), `additionalNotes` (optional). Unique on `(productId, accountId)` — a product has at most one holding per account. A holding is created explicitly (an "add this product to this account" action), never implicitly by recording a trade.
- **`InvestmentSnapshot` is keyed by `holdingId`, not `productId`.** Value is fundamentally per product-per-account now: two holdings of the same product can carry different balances (different lots, different brokers). A product's total value (used by the category/sub-category allocation) is the sum of its holdings' latest snapshots; an account's `INVESTMENT` balance is the sum of the latest snapshots of the holdings pointing at it.
- **`closedDate`, the close guard (latest snapshot `0`/absent) and `needsSnapshot` all move from product to holding.** A product can be sold out at one broker while still held at another — there is no longer a single "is this product closed" fact.
- **Trade validation changes from an equality check to an existence check.** ADR 0012's `TransferService` rule "the product must belong to that account" becomes "a holding must already exist for (product, that account)" — `404`/`409` if not, rather than creating one on the fly.
- **The "move a product to another account" guard is removed.** ADR 0012 blocked moving a product with history because a single `accountId` couldn't safely change once trades pointed at it. With holdings, "moving" isn't a thing — add a new holding at the new account and, optionally, close the old one. Both keep their own history.
- **Product creation is unchanged in shape**: still asks for one account up front, creating the product and its first holding together in one step (a two-write use case — `@Transactional`, backend CLAUDE.md).

## Consequences
- Migration backfills each existing product's `account_id`/`closed_date` into its one initial `InvestmentHolding` (unambiguous today, since every product currently has exactly one account), and each snapshot's `product_id` into that same holding's id.
- Product name uniqueness moves from per-account to global — the scenario ADR 0012 built per-account uniqueness for (the same instrument at two brokers) is now one product with two holdings, not two products sharing a name.
- Every reader of `product.accountId`/`product.closedDate` changes: `AccountBalanceQuery`'s `INVESTMENT` branch, the close/delete guards, `TransferService`'s validation, `needsSnapshot`, the product and account detail pages, and the frontend's Buy/Sell direction check (`trade.toAccountId === product.accountId` becomes a holding lookup).
- Product management moves off the account detail page (which listed "this account's products") onto a standalone product detail page listing all of a product's holdings; the account detail page becomes a read-only "holdings in this account" view linking out to it.
- Enables allocation by account (sum of holdings' latest snapshots per account) with no new modeling — the grouping key already exists on `InvestmentHolding`.
- Changes F008/F009 specs, PRD §5.8, §5.9, §6.6. Amends [ADR 0012](0012-investments-as-accounts-and-transfers.md) (its per-account product uniqueness and 1:1 product/account link); the rest of 0012 stands (`INVESTMENT` as an `Account` type, buys/sells as transfers, snapshots as the sole source of value).
