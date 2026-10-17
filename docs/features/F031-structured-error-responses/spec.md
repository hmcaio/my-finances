# F031 — Structured Error Responses

## Summary
Replaces the backend's status-code-only error contract with an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details body on every error response ([ADR 0026](../../adr/0026-structured-error-responses-rfc9457.md)). Today a client gets a status code and nothing else for any `@ResponseStatus` exception or Spring's own built-in exceptions (ADR 0011's security-audit fix), which means several endpoints that can fail for more than one distinct reason at the same status code are indistinguishable to the frontend. The frontend papers over this with a hardcoded message per API-client call site; this feature replaces that with a stable `type` identifier the frontend can switch on, with no exception text or entity data ever added to the response (`detail` stays null everywhere — the ADR 0011 rule is unchanged, just now sitting next to a safe identifier instead of an empty body).

Infrastructure/cross-cutting only — no product or data-model change, no new migration.

## Scope
- Backend: `GlobalExceptionHandler` rewritten to build a `ProblemDetail` body for every error path it previously rethrew to Spring's default resolver (its own `@ResponseStatus` exceptions, Spring's `ErrorResponse` exceptions, `HttpMessageNotReadableException`/`TypeMismatchException`) plus the generic unexpected-exception fallback. A shared slug-derivation utility used throughout.
- Backend: `@ApiResponse` added to every controller operation that can throw a mapped exception, documenting the possible `type` values in the response description.
- Frontend: `apiError.ts`'s per-call-site `conflictMessage` mechanism replaced by a central `type → message` lookup; all 38 call sites across 20 API-client files updated.
- Docs: ADR 0026 (already written), this spec/plan, root/backend/frontend `CLAUDE.md` updates, `docs/features/README.md` row.
- Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| Uniform `ProblemDetail` body for **every** error response — all 69 `@ResponseStatus` exceptions, Spring's own built-in exceptions, and the generic 500 — not just the ambiguous multi-cause cases. | One mechanism is simpler than "ProblemDetail for some exceptions, empty body for others," and a 404 benefits too (a composite endpoint can't distinguish which entity wasn't found either). |
| `type` auto-derived from the exception's simple class name (kebab-cased, `Exception` suffix stripped), namespaced `urn:my-finances:error:<slug>`. | Zero per-exception authoring across ~69 classes; the class name is already the identifier `GlobalExceptionHandler` logs at INFO as safe (ADR 0011) — one source of truth, not two. A URN rather than an `https://` URI because RFC 9457 doesn't require `type` to be dereferenceable, and this app has no public doc site (PRD §7.3) to host one. |
| Classes duplicated across aggregate packages for the no-cross-aggregate-import ArchUnit rule (ADR 0014) — `AccountClosedException` in `transfer`/`transaction`/`recurringtemplate`, `AccountTypeNotAllowedException` in `transaction`/`recurringtemplate` — **collapse to the same `type`**. | Same real-world condition; the duplication is a code-organization artifact, not a semantic difference. |
| Generic unexpected-exception (500) case gets a **fixed** slug, `urn:my-finances:error:internal-error`, not one derived from the actual exception class. | That set is unbounded (any uncaught exception, including third-party library types never vetted for safety) — one stable identifier avoids a long tail of ad hoc internal class-name slugs. |
| `detail` is **always omitted/null**, on every response, with no exception. | This is the field the RFC expects to carry specifics — and exactly the field ADR 0011 forbids populating with exception text or entity data. Unchanged rule, now explicit in the new contract. |
| `GlobalExceptionHandler` **no longer rethrows** to Spring's default resolver for any case — it builds the body itself everywhere, including for Spring's own `ErrorResponse` exceptions and the two 400-mapped non-`ErrorResponse` ones. | Removes the three-way special-case list from ADR 0011/issue #19 instead of growing it; one body-building path is simpler than one path that builds and one that rethrows. |
| Big-bang migration — one `GlobalExceptionHandler` rewrite, no per-exception opt-in. | Auto-derivation means no per-exception authoring gates rollout; an opt-in flag would be a second code path with no real transition benefit. |
| OpenAPI gets a shared `ProblemDetail` response schema referenced via `@ApiResponse` per operation, with possible `type` values listed in the response **description text**, not a compile-time enum/union in `schema.ts`. | A custom springdoc extension generating a literal-string union per operation is disproportionate tooling for a local single-user app where the frontend already does a runtime string check. |
| Frontend: one central `type → message` map in `apiError.ts`, **no per-call-site override** parameter. | The point of this change is killing the 38-call-site duplication; a screen needing different phrasing for the same `type` branches on it at the UI layer instead. |
| Bean-validation (`@Valid` field-level) 400s are **out of scope**. | Different shape problem — needs a list of violations, not one `type` per response. Intended as the immediate next design once this ships. |

## Backend

### Slug derivation
New `infrastructure/web/ProblemTypeSlugs` (or similar name settled during implementation), a small pure utility:
- `slugFor(Class<?> exceptionClass)`: simple name, strip trailing `Exception`, insert a hyphen before each internal uppercase letter, lowercase. `AccountHasHistoryException` → `account-has-history`. `HttpMessageNotReadableException` → `http-message-not-readable`.
- `typeUriFor(Class<?> exceptionClass)`: `URI.create("urn:my-finances:error:" + slugFor(exceptionClass))`.
- `titleFor(Class<?> exceptionClass)`: humanized slug with the first letter capitalized (`"Account has history"`).
- A fixed constant for the unexpected-exception case: `urn:my-finances:error:internal-error` / title `"An unexpected error occurred"` — not derived via the above.

### `GlobalExceptionHandler`
Still the single `@RestControllerAdvice` (`docs/conventions/errors.md` rule unchanged). Its one `@ExceptionHandler(Exception.class)`:
- Resolves the HTTP status the same way it does today (merged `@ResponseStatus` value; `ErrorResponse.getStatusCode()`; a hardcoded `BAD_REQUEST` for `HttpMessageNotReadableException`/`TypeMismatchException`, matching `DefaultHandlerExceptionResolver`'s existing mapping; `INTERNAL_SERVER_ERROR` for everything else).
- Builds `ProblemDetail.forStatus(status)`, sets `type` and `title` via the slug utility (fixed internal-error constants for the unexpected-exception branch), sets `instance` to `URI.create(request.getRequestURI())`, leaves `detail` untouched (stays `null`).
- Returns `ResponseEntity.status(status).body(problemDetail)` for every branch — no more rethrow.
- Logging is unchanged in substance: an unexpected exception still logs ERROR with the full stack trace; an expected `@ResponseStatus` exception still logs INFO with the class simple name only (now also the `type` it emits, which is derived from that same name — no new leak surface). Spring's own `ErrorResponse` exceptions still aren't logged by the handler.
- Content type: `ProblemDetail`'s default Spring MVC message conversion serializes as `application/problem+json` — confirm this doesn't need an explicit `produces` anywhere given the app has no existing content-negotiation customization.

### OpenAPI (`OpenApiConfig` / per-controller)
- Define the shared `ProblemDetail` response schema once (springdoc already has partial support for `ProblemDetail` as a response type; confirm during implementation whether it needs an explicit `@Schema`-annotated wrapper or Spring's class is sufficient as-is).
- Add `@ApiResponse(responseCode = "409", description = "<human list of possible type values for this operation>", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))`-style annotations to every controller method that can throw a mapped exception — grouped by status code where an operation has several same-status causes (the budgets/category/institution/holding/allocation-plan/transfer cases from ADR 0026's Context).
- Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types`, backend running) once the annotations are in place.

## Frontend

### `apiError.ts`
- Add a `type → message` record (e.g. `PROBLEM_TYPE_MESSAGES: Record<string, string>`) covering every `type` the backend can now emit for a conflict/validation case that previously had a `conflictMessage` override.
- `toApiError`/`unwrap` read `error.response?.data?.type` and look it up in that map; fall back to the existing generic `"Request failed with status {status}"` string when the type is absent or unrecognized (keeps old callers safe during the transition and covers any case not yet in the map).
- Remove the `conflictMessage` parameter from `unwrap()`'s signature and every one of the 38 call sites across the 20 `frontend/src/api/**/*.ts` files that pass one today (`categories.ts`, `vehicles.ts`, `institutions.ts`, and the rest) — delete each file's now-unused `CONFLICT_MESSAGE`/`DUPLICATE_NAME_MESSAGE`-style constant along with it.
- `ApiError` itself gains a `type?: string` field (already present as `data.type` on the raw response; surface it on the typed error) so a UI layer that genuinely needs to branch per-`type` for one screen still can, per the ADR's own escape hatch.

## Docs
- **ADR 0026** (already written) — the structured-error-response decision, slug derivation, URN namespace, `detail`-always-null rule, big-bang migration, OpenAPI documentation granularity, frontend central-map decision, and the bean-validation follow-up scope boundary.
- Root `CLAUDE.md`: update the existing "backend never sends exception text" cross-stack bullet — `detail` (not the whole body) carries that prohibition now; the body otherwise carries a stable `type`/`title`/`status`/`instance`.
- Backend `CLAUDE.md`'s "Errors" section: document the `ProblemDetail` body contract, the slug-derivation rule (including the deliberate collapse for cross-aggregate-duplicated exception classes), the fixed `internal-error` slug for the 500 case, and that `GlobalExceptionHandler` no longer rethrows anything.
- Frontend `CLAUDE.md`: document the central `type → message` map replacing per-call-site `conflictMessage`, and that `unwrap()` no longer takes that parameter.
- `docs/features/README.md`: F031 row. `CHANGELOG.md`'s `[Unreleased]` entry is written at release time (ADR 0007), not on the feature branch.
- No `docs/PRD.md` change — this has no product-facing surface (same precedent as F016/F019/F020), and §7 Technical Design makes no claim about error response shape that this contradicts.

## Non-goals
- Bean-validation (`@Valid` field-level) 400 responses — different shape (a violation list, not one `type`), designed as the immediate next feature once this ships.
- A compile-time enum/union of `type` values in generated `schema.ts` — OpenAPI documents them as human-readable description text only (see Decisions).
- A dereferenceable `/errors/*` documentation endpoint for `type` URNs — not needed per RFC 9457, and this app has no public doc site to host one.
- Any change to what counts as "exception text" that may reach the client — `detail` stays null everywhere; this feature adds a safe identifier alongside the existing prohibition, it does not loosen it.
- A per-call-site message override mechanism on the frontend — deliberately removed, not replaced with a scoped variant (see Decisions).

## Dependencies
F001 (backend skeleton), F016 (`GlobalExceptionHandler`'s current logging behavior, which this preserves), the existing OpenAPI generation pipeline (root `CLAUDE.md`'s "API types are generated" rule) and the existing `apiError.ts`/`unwrap()` convention from F002. Touches every controller with a mapped exception, i.e. effectively every feature built so far (F002–F030) — none of their business logic changes, only the response body shape and the frontend's consumption of it.
