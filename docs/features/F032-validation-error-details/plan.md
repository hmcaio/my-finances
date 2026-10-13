# F032 — Action Plan

**Depends on**: F031 (must land first — unbuildable without its `ProblemDetail` body and slug-derivation utility), F002.

One feature branch (`feature/f032-validation-error-details`), backend first, then frontend, then docs.

## Phase 1 — Backend: violation-code derivation + `GlobalExceptionHandler`
- [x] Test first: extend the slug-derivation utility's test — constraint annotation simple names kebab-case correctly (`NotBlank` → `not-blank`, `PositiveOrZero` → `positive-or-zero`, `DecimalMin` → `decimal-min`); a class-level `@AssertTrue` error derives a code from its method/property name rather than a constraint annotation.
- [x] Extend the slug-derivation utility with `codeFor(FieldError)`/`codeFor(ObjectError)`, reusing the existing PascalCase-to-kebab transform.
- [x] Test first: extend `GlobalExceptionHandlerTest` — a `@Valid @RequestBody` request failing one field constraint returns `type: "urn:my-finances:error:method-argument-not-valid"` (F031's existing derivation, unchanged) **plus** `errors: [{field: "<name>", code: "<constraint-code>"}]`; a request failing several fields returns one entry per field; a request tripping `TradeConfirmationRequest`'s `@AssertTrue` invariant returns an entry with `field: null`; a nested/indexed field (`SetAllocationPlanRequest.entries[0].targetPercentage` or `TradeConfirmationRequest`'s `tradeConfirmation.lines[0].quantity`) reports its full bracketed path; confirm no entry anywhere carries Jakarta's interpolated message text or the rejected value.
- [x] Implement: in the existing `MethodArgumentNotValidException` path of `GlobalExceptionHandler`, walk `getBindingResult().getFieldErrors()` and `getGlobalErrors()`, build the `errors` list, `problemDetail.setProperty("errors", errors)`.
- [x] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest` (Docker running).

  **Empirically-pinned mechanic (not assumed)**: a real `LocalValidatorFactoryBean` run (reproduced exactly by `GlobalExceptionHandlerTest`'s real `MockMvc` REST calls) shows every `@AssertTrue` invariant in this codebase — `TradeConfirmationRequest.isAtMostOneResultingBalancePerProduct`, `TradeConfirmationLineRequest.isCloseHoldingOnlyOnSell`, and (unexercised by this feature's tests, but same mechanic) `CreateAccountRequest`/`CreateTransactionRequest`/`UpdateTransactionRequest`'s own `@AssertTrue`s — surfaces as a **field-level** `FieldError`, never a global/object error with an empty path. JSR-380's `@AssertTrue` can only target a method/field, never a class/record directly, so each invariant is written as an `isXxx()` getter; Hibernate Validator treats `xxx` as an ordinary bean property, and Spring's `SpringValidatorAdapter` reports it as a `FieldError` whose field is that synthetic property path (`atMostOneResultingBalancePerProduct`, or `lines[0].closeHoldingOnlyOnSell` when nested) — not `null`. `GlobalExceptionHandler` detects this case by its constraint code (`"AssertTrue"`, via `ProblemTypeSlugs.isAssertTrueViolation`) and reports it as `field: null` with `code` derived from that synthetic property name (`ProblemTypeSlugs.codeForAssertTrue`) rather than the uninformative literal `"AssertTrue"` every such invariant would otherwise share. `BindingResult.getGlobalErrors()` is still walked for forward compatibility, but is empty for every case in this codebase today.

## Phase 2 — Backend: OpenAPI
- [x] Extend F031's `@ApiResponse` 400 descriptions (already added per-operation) to note the possible `errors[].code` values for that operation's own DTO annotations.

  In practice most `@Valid @RequestBody` operations had **no** existing 400 `@ApiResponse` at all (F031 only documented a 400 where a domain exception could also produce one) - those got a brand-new 400 entry; the handful that already had one (transfers' trade-confirmation shape, the allocation plan, transactions, the recurring-template confirm endpoint) got their existing `description` text extended with a "may instead be ... method-argument-not-valid (F032)" sentence rather than a second `@ApiResponse` for the same status code.
- [x] `npm run generate-api-types` (backend running); `npm run build` to confirm `schema.ts` still compiles.

## Phase 3 — Frontend
- [x] Test first: `apiError.test.ts` — a 400 response carrying `errors` builds a joined summary string from the per-`code` message map; an unrecognized `code` falls back to existing generic text; `ApiError.fieldErrors` is populated from the raw list.
- [x] Add the `code → message` map and `errors`-reading logic to `apiError.ts`; add `fieldErrors?: { field: string | null; code: string }[]` to `ApiError`.
- [x] `npm run lint && npm test && npm run build && npm run format:check`.

## Phase 4 — Docs
- [ ] Backend `CLAUDE.md` "Errors" section: `errors` extension member, constraint-code derivation (shared utility with F031), `field: null` convention for `@AssertTrue`, `@Valid @RequestBody`-only scope.
- [ ] Frontend `CLAUDE.md`: `apiError.ts` reads `errors` for a 400; per-field UI not yet wired (pointer for later).
- [ ] `docs/features/README.md`: F032 row (already present from this design pass; verify accuracy once built). `CHANGELOG.md` entry deferred to release time (ADR 0007).

## Verification
- [ ] `./gradlew spotlessCheck test integrationTest` (Docker running) and `npm run lint && npm test && npm run build && npm run format:check` all green.
- [ ] Manual, dev: submit a transaction create with a negative amount (violates `@Positive`, not mirrored client-side) → response body has `type: "urn:my-finances:error:method-argument-not-valid"` and `errors: [{field: "amount", code: "positive"}]`; UI banner shows a readable message instead of today's nothing.
- [ ] Manual, dev: submit a request failing two fields at once → `errors` has two entries, UI summary mentions both.
- [ ] Manual, dev: submit a trade confirmation tripping its `@AssertTrue` invariant → one entry with `field: null`.
- [ ] Manual, dev: submit an allocation-plan or trade-confirmation request with an invalid nested line entry → the entry's `field` shows the full bracketed path (e.g. `entries[0].targetPercentage`).
- [ ] Confirm no response body anywhere in the above contains Jakarta's interpolated message text or the actual rejected value — only `field`/`code`.
