# F017 — Action Plan

**Depends on**: F001, F002, F003, F015. **Must land before F008** (its `investment_accounts.institution_id` references this feature's table). F011 and F013 pick this up when they are built.

Suggested order: domain and services first, then the migration, then the API switch, then the frontend in the same branch (the account API change is breaking, so backend and frontend ship together). Branch `feature/f017-institutions`.

## Backend
- [ ] Write tests first for `Institution`: blank name rejected, name over `MAX_NAME_LENGTH` rejected, `rename` re-validates.
- [ ] Add `domain/institution/Institution.java` and the `InstitutionRepository` port to make them pass.
- [ ] Write tests first for `InstitutionService` against `FakeInstitutionRepository` (+ `FakeAccountRepository.existsByInstitutionId`): create; duplicate name rejected on create and on rename (a no-op rename to its own name is allowed); delete blocked while an account references it (open **and** closed); delete succeeds once unreferenced; unknown id is 404 on rename/delete. Then implement `InstitutionService` and the three exceptions.
- [ ] Change `Account` to hold `UUID institutionId` (nullable, no length check): update `AccountTest` first (create/reconstitute/edit with an id and with `null`), then `Account`, `AccountRepository.existsByInstitutionId`.
- [ ] Update `AccountServiceTest` first: a non-null unknown `institutionId` on create/edit is `InstitutionNotFoundException`; `null` is accepted; `edit` with `null` clears it; edit works on a closed account. Then update `AccountService` (inject `InstitutionRepository`).
- [ ] Add `InstitutionJpaEntity`, `InstitutionJpaRepository`, `InstitutionRepositoryAdapter`; switch `AccountJpaEntity`/`AccountRepositoryAdapter`/`AccountJpaRepository` to `institution_id`. Real-DB adapter tests (`@SpringBootTest` + Testcontainers): round-trip, FK rejects an unknown id, unique name, `existsByInstitutionId`. Fixture names use the `" Test"` suffix.
- [ ] Flyway migration `V12__institutions.sql` (re-check the highest existing `V*` first) per the spec: create table, backfill with normalization, add + populate `accounts.institution_id`, index, drop `accounts.institution`.
- [ ] Migration test against pre-existing data (`InstitutionBackfillMigrationTest`, throwaway schema, `target("11")` → 12) covering `NULL`, blank, whitespace-padded, case-variant and accented values; assert the resulting institutions, every account's `institution_id`, and the dropped column.
- [ ] `InstitutionController` + DTOs (`CreateInstitutionRequest`, `UpdateInstitutionRequest`, `InstitutionResponse`; `@NotBlank @Size(max = MAX_NAME_LENGTH)`); REST tests (hand-built `MockMvc`): 201/list/PATCH/204, 409 duplicate, 409 in use, 404 unknown, 400 blank/too long.
- [ ] Switch `CreateAccountRequest`/`UpdateAccountRequest`/`AccountResponse`/`AccountController` to `institutionId`; update `AccountControllerTest` (404 for an unknown id, `null` clears).
- [ ] `./gradlew spotlessApply`, then `spotlessCheck test`.
- [ ] With the backend running: `npm run generate-api-types` and commit the regenerated `schema.ts`.

## Frontend
- [ ] `src/api/institutions.ts` (+ `institutions.test.ts` with MSW; delete has a `conflictMessage`), `src/mocks/handlers/institutions.ts`.
- [ ] `src/features/institutions/InstitutionsPage.tsx` + test (list, add, inline rename, delete, 409 message, empty state), route `/settings/institutions` in `App.tsx`, nav entry beside categories/payment methods.
- [ ] `src/features/institutions/InstitutionSelect.tsx` + test (options load, select, clear, "Add “X”" creates then selects, create failure shows an error).
- [ ] `src/api/accounts.ts` and `src/mocks/handlers/accounts.ts`: `institution` → `institutionId`; update `accounts.test.ts`.
- [ ] `AccountsPage` (add form, inline edit, list column) and `AccountDetailPage` (header): use `InstitutionSelect` and `nameLookup` over the institutions list; update both page tests.
- [ ] `npm run lint && npm test`.

## Docs
- [ ] `CHANGELOG.md` under `[Unreleased]`: one bullet `**F017 — Institutions** — …`, with an `Upgrade:` sub-line (migration `V12` converts each account's institution text into a shared institution list and drops the old column; irreversible; the account API's `institution` field is replaced by `institutionId`). Add the `([#N](…))` link once the PR exists.
- [ ] Root `CLAUDE.md`: add `Institution` to the flat-taxonomy names that use `MAX_NAME_LENGTH`. `backend/CLAUDE.md`: nothing new expected — if the delete-guard or migration test surfaces a gotcha, record it there.
- [ ] Tick this plan; update the README "Project status" if it tracks per-feature state.

## Verification
- [ ] On a dev database that already has accounts with free-text institutions (incl. two spelled differently only by case), start the backend: `institutions` is populated as described and every account keeps its institution.
- [ ] Create an institution inline from the account form, assign it, rename it, and confirm both account screens show the new name.
- [ ] Delete an institution that an account (including a closed one) uses: rejected with the "still used" message. Re-point the account, then delete succeeds.
- [ ] Clear an account's institution: it shows "No institution".
- [ ] Malformed `institutionId` (not a uuid) is `400`; unknown uuid is `404` (issue #19 behaviour).
