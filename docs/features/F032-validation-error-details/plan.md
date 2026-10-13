# F032 — Action Plan

**Depends on**: F031 (must land first — unbuildable without its `ProblemDetail` body and slug-derivation utility), F002.

One feature branch (`feature/f032-validation-error-details`), backend first, then frontend, then docs.

## Phase 1 — Backend: violation-code derivation + `GlobalExceptionHandler`
- [ ] Test first: extend the slug-derivation utility's test — constraint annotation simple names kebab-case correctly (`NotBlank` → `not-blank`, `PositiveOrZero` → `positive-or-zero`, `DecimalMin` → `decimal-min`); a class-level `@AssertTrue` error derives a code from its method/property name rather than a constraint annotation.
- [ ] Extend the slug-derivation utility with `codeFor(FieldError)`/`codeFor(ObjectError)`, reusing the existing PascalCase-to-kebab transform.
- [ ] Test first: extend `GlobalExceptionHandlerTest` — a `@Valid @RequestBody` request failing one field constraint returns `type: "urn:my-finances:error:method-argument-not-valid"` (F031's existing derivation, unchanged) **plus** `errors: [{field: "<name>", code: "<constraint-code>"}]`; a request failing several fields returns one entry per field; a request tripping `TradeConfirmationRequest`'s `@AssertTrue` invariant returns an entry with `field: null`; a nested/indexed field (`SetAllocationPlanRequest.entries[0].targetPercentage` or `TradeConfirmationRequest`'s `tradeConfirmation.lines[0].quantity`) reports its full bracketed path; confirm no entry anywhere carries Jakarta's interpolated message text or the rejected value.
- [ ] Implement: in the existing `MethodArgumentNotValidException` path of `GlobalExceptionHandler`, walk `getBindingResult().getFieldErrors()` and `getGlobalErrors()`, build the `errors` list, `problemDetail.setProperty("errors", errors)`.
- [ ] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest` (Docker running).

## Phase 2 — Backend: OpenAPI
- [ ] Extend F031's `@ApiResponse` 400 descriptions (already added per-operation) to note the possible `errors[].code` values for that operation's own DTO annotations.
- [ ] `npm run generate-api-types` (backend running); `npm run build` to confirm `schema.ts` still compiles.

## Phase 3 — Frontend
- [ ] Test first: `apiError.test.ts` — a 400 response carrying `errors` builds a joined summary string from the per-`code` message map; an unrecognized `code` falls back to existing generic text; `ApiError.fieldErrors` is populated from the raw list.
- [ ] Add the `code → message` map and `errors`-reading logic to `apiError.ts`; add `fieldErrors?: { field: string | null; code: string }[]` to `ApiError`.
- [ ] `npm run lint && npm test && npm run build && npm run format:check`.

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
