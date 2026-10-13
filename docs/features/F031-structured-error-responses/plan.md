# F031 — Action Plan

**Depends on**: F001, F016 (touches every feature's controllers, no business-logic change).

One feature branch (`feature/f031-structured-error-responses`), backend first (the body shape must exist before the frontend has anything to consume), then OpenAPI, then frontend, then docs.

## Phase 0 — Decision record
- [x] Write `docs/adr/0026-structured-error-responses-rfc9457.md` and add its row to `docs/adr/README.md`.

## Phase 1 — Backend: slug derivation + `GlobalExceptionHandler`
- [x] Test first: a slug-derivation unit test — `AccountHasHistoryException` → `account-has-history` / `urn:my-finances:error:account-has-history`; multi-word classes (`CategoryNameAlreadyExistsException`); the three `AccountClosedException` classes (different packages) all produce the identical slug; the fixed internal-error constant is not derived from any class.
- [x] Implement the slug-derivation utility (`infrastructure/web`, final name TBD during implementation) — `slugFor`, `typeUriFor`, `titleFor`, plus the fixed `INTERNAL_ERROR` type/title constants.
- [x] Test first: extend `GlobalExceptionHandlerTest` — unexpected exception now returns a `ProblemDetail` body with `type: "urn:my-finances:error:internal-error"`, `title: "An unexpected error occurred"`, `status: 500`, `instance` set, `detail` null (replacing the old exact-string body assertion); a representative `@ResponseStatus(NOT_FOUND)` exception returns `type`/`title` derived from its class, `detail` null; a representative `@ResponseStatus(CONFLICT)` exception likewise; `HttpMessageNotReadableException` and a `TypeMismatchException`-triggering request each get `status: 400` and a derived `type` instead of the previous empty body; confirm no case still produces an empty body. Keep the existing "no leaked exception message" assertions — they must still pass against the new body.
- [x] Rewrite `GlobalExceptionHandler`: resolve status (merged `@ResponseStatus` value / `ErrorResponse.getStatusCode()` / hardcoded 400 for the two non-`ErrorResponse` Spring exceptions / 500 otherwise), build and return `ProblemDetail` for every branch, remove the rethrow entirely. Keep existing INFO/ERROR logging unchanged in substance.
- [x] Grep every existing REST integration test asserting `status().isNotFound()`/`isConflict()`/`isBadRequest()` with no body check on a path this handler covers; confirm none now fail from an unexpected body (they shouldn't — only genuinely new assertions are additive) and add a `type` assertion to a representative sample per aggregate rather than every single test.
- [x] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest` (Docker running).

## Phase 2 — Backend: OpenAPI
- [ ] Define the shared `ProblemDetail` response schema once in `OpenApiConfig` (or confirm springdoc's built-in handling is sufficient without one — check the generated `/v3/api-docs` output either way).
- [ ] Add `@ApiResponse` to every controller operation that can throw a mapped exception, grouped by status code, description text listing the operation's actual possible `type` values. Prioritize the multi-cause operations from ADR 0026's Context (budgets create, category rename/delete, institution delete, holding close, allocation plan `setAllocation`, transfer request-shape validation) first, then sweep the rest.
- [ ] `npm run generate-api-types` (backend running) to regenerate `frontend/src/api/generated/schema.ts`; `npm run build` to confirm it compiles.

## Phase 3 — Frontend
- [x] Test first: `apiError.test.ts` — a response with a known `type` maps to the matching message; an unrecognized/absent `type` falls back to the existing generic `"Request failed with status {status}"` string; `ApiError.type` is populated from the response body when present.
- [x] Add the `PROBLEM_TYPE_MESSAGES` map and the `type`-based lookup to `apiError.ts`; add `type?: string` to `ApiError`.
- [x] Remove the `conflictMessage` parameter from `unwrap()` and update all 38 call sites across the 20 `frontend/src/api/**/*.ts` files, deleting each file's now-unused message constant. Populate `PROBLEM_TYPE_MESSAGES` with every `type` those removed constants covered.
- [x] Grep the diff to confirm zero remaining `conflictMessage` references (call sites and the parameter itself).
- [x] `npm run lint && npm test && npm run build && npm run format:check`.

## Phase 4 — Docs
- [x] Root `CLAUDE.md`: update the "backend never sends exception text" bullet to scope it to `detail`, note the new body shape otherwise.
- [x] Backend `CLAUDE.md` "Errors" section: `ProblemDetail` contract, slug derivation + the deliberate cross-aggregate-duplicate collapse, fixed `internal-error` slug, no more rethrow in `GlobalExceptionHandler`.
- [x] Frontend `CLAUDE.md`: central `type → message` map replacing per-call-site `conflictMessage`; `unwrap()` signature change.
- [x] `docs/features/README.md`: F031 row (already present from this design pass; verify accuracy once built). `CHANGELOG.md` entry deferred to release time (ADR 0007).

## Verification
- [ ] `./gradlew spotlessCheck test integrationTest` (Docker running) and `npm run lint && npm test && npm run build && npm run format:check` all green.
- [ ] Manual, dev: trigger a known multi-cause 409 (e.g. `POST /api/budgets` for a non-expense category) → response body has `type: "urn:my-finances:error:category-not-expense"`, `detail` absent/null; the same endpoint's other cause (duplicate budget) returns a different `type`.
- [ ] Manual, dev: trigger the generic 500 path (e.g. stop the dev Postgres container mid-request) → body is `type: "urn:my-finances:error:internal-error"`, not the old `{"message": ...}` shape; backend log still has the full stack trace.
- [ ] Manual, dev: trigger `HttpMessageNotReadableException` (malformed JSON body) and a `TypeMismatchException` (bad UUID in a path param) → both now return a derived `type` instead of an empty body.
- [ ] Manual, dev (frontend): trigger a 409 the central map covers → UI shows the mapped message; trigger one deliberately left unmapped (if any remain) → UI falls back to the generic string, no crash.
- [ ] Confirm no response body anywhere in the above contains an exception message, stack trace, or entity name/amount/description — only `type`/`title`/`status`/`instance`.
