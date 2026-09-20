# F017 — Action Plan

**Depends on**: F001, F002, F003, F015. **Must land before F008** (its `investment_accounts.institution_id` references this feature's table). F011 and F013 pick this up when they are built.

Suggested order: domain and services first, then the migration, then the API switch, then the frontend in the same branch (the account API change is breaking, so backend and frontend ship together). Branch `feature/f017-institutions`.

## Backend
- [x] Write tests first for `Institution`: blank name rejected, name over `MAX_NAME_LENGTH` rejected, `rename` re-validates (also on a built-in instance), `create` always yields `isBuiltIn() == false`, `reconstitute` preserves the flag.
- [x] Add `domain/institution/Institution.java` and the `InstitutionRepository` port to make them pass.
- [x] Write tests first for `InstitutionService` against `FakeInstitutionRepository` (+ `FakeAccountRepository.existsByInstitutionId`): create; duplicate name rejected on create and on rename (a no-op rename to its own name is allowed); rename of the built-in row allowed; delete of the built-in row is `BuiltInInstitutionException` even with zero references; delete blocked while an account references it (open **and** closed); delete succeeds once unreferenced; unknown id is 404 on rename/delete. Then implement `InstitutionService` and the four exceptions.
- [x] Change `Account` to hold a required `UUID institutionId` (no length check): update `AccountTest` first (create/reconstitute/edit with an id; `null` rejected in the constructor and in `edit`), then `Account`, `AccountRepository.existsByInstitutionId`.
- [x] Update `AccountServiceTest` first: a non-null unknown `institutionId` on create/edit is `InstitutionNotFoundException`; `edit` moves an account to another institution; edit works on a closed account. Then update `AccountService` (inject `InstitutionRepository`).
- [x] Add `InstitutionJpaEntity` (with the read-only `builtIn` flag), `InstitutionJpaRepository`, `InstitutionRepositoryAdapter`; switch `AccountJpaEntity`/`AccountRepositoryAdapter`/`AccountJpaRepository` to a non-null `institution_id`. Real-DB adapter tests (`@SpringBootTest` + Testcontainers): round-trip, FK rejects an unknown id, unique name, `existsByInstitutionId`, the built-in row is present after migrate and is the only one, a second built-in row is rejected by the partial unique index. Fixture names use the `" Test"` suffix; nothing asserts an exact `findAll()` size (the seed row is always there).
- [x] Fix every existing real-DB test that builds an `Account` (adapter tests, controller tests, transaction/transfer/budget/recurring fixtures that create accounts): they now need a valid `institutionId` — add a small shared test helper that returns the built-in institution's id.
- [x] Flyway migration `V12__institutions.sql` (re-check the highest existing `V*` first) per the spec: create table + single-built-in partial unique index, seed "No institution", backfill with normalization, add + populate `accounts.institution_id` (unmatched rows → built-in), `SET NOT NULL`, index, drop `accounts.institution`.
- [x] Migration test against pre-existing data (`InstitutionBackfillMigrationTest`, throwaway schema, `target("11")` → 12) covering `NULL`, blank, whitespace-padded, case-variant, accented and literal "No institution" (any case) values; assert the resulting institutions, every account's `institution_id`, the `NOT NULL`, and the dropped column.
- [x] `InstitutionController` + DTOs (`CreateInstitutionRequest`, `UpdateInstitutionRequest`, `InstitutionResponse { id, name, builtIn }`; `@NotBlank @Size(max = MAX_NAME_LENGTH)`); REST tests (hand-built `MockMvc`): 201/list (includes the built-in row)/PATCH/204, 409 duplicate, 409 in use, 409 delete built-in, 404 unknown, 400 blank/too long.
- [x] Switch `CreateAccountRequest`/`UpdateAccountRequest`/`AccountResponse`/`AccountController` to a required `institutionId` (`@NotNull`); update `AccountControllerTest` (400 when missing or null, 400 for a malformed uuid, 404 for an unknown id).
- [x] `./gradlew spotlessApply`, then `spotlessCheck test`.
- [x] With the backend running: `npm run generate-api-types` and commit the regenerated `schema.ts`.

## Frontend
- [x] `src/api/institutions.ts` (+ `institutions.test.ts` with MSW; delete has a `conflictMessage`), `src/mocks/handlers/institutions.ts` (includes the built-in row).
- [x] `src/features/institutions/InstitutionsPage.tsx` + test (list with the built-in row first and no delete action, add, inline rename incl. the built-in row, delete, 409 message), route `/settings/institutions` in `App.tsx`, nav entry beside categories/payment methods.
- [x] `src/features/institutions/InstitutionSelect.tsx` + test (options load with the built-in row first, defaults to it when no value is passed, select, not clearable, "Add “X”" creates then selects, create failure shows an error).
- [ ] `src/api/accounts.ts` and `src/mocks/handlers/accounts.ts`: `institution` → required `institutionId`; update `accounts.test.ts`.
- [ ] `AccountsPage` (add form, inline edit, list column) and `AccountDetailPage` (header): use `InstitutionSelect` and `nameLookup` over the institutions list; update both page tests.
- [ ] `npm run lint && npm test`.

## Docs
- [ ] `CHANGELOG.md` under `[Unreleased]`: one bullet `**F017 — Institutions** — …`, with an `Upgrade:` sub-line (migration `V12` creates the shared institution list with a built-in "No institution" row, converts each account's institution text into it, assigns accounts without one to "No institution" and drops the old column — irreversible; the account API's `institution` field is replaced by a required `institutionId`). Add the `([#N](…))` link once the PR exists.
- [ ] Root `CLAUDE.md`: add `Institution` to the flat-taxonomy names that use `MAX_NAME_LENGTH`. `backend/CLAUDE.md` Testing section: extend the "seed rows collide" note with the built-in institution and the "accounts in real-DB tests need an `institutionId`" gotcha.
- [ ] Tick this plan; update the README "Project status" if it tracks per-feature state.

## Verification
- [ ] On a dev database that already has accounts with free-text institutions (incl. two spelled differently only by case, and some with none), start the backend: `institutions` holds "No institution" plus one row per distinct name, every account has an institution, and the accounts that had none point at "No institution".
- [ ] On a fresh database, `GET /api/institutions` returns exactly the built-in row.
- [ ] Create an institution inline from the account form, assign it, rename it, and confirm both account screens show the new name.
- [ ] The add-account form preselects "No institution"; the field can't be cleared.
- [ ] Delete "No institution": no button in the UI, `409` from the API. Delete an institution that an account (including a closed one) uses: rejected with the "still used" message; re-point the account, then delete succeeds.
- [ ] Rename "No institution" to another label: everything keeps working and the row still can't be deleted.
- [ ] Missing/`null` `institutionId` on account create/edit is `400`; a malformed uuid is `400`; an unknown uuid is `404`.
