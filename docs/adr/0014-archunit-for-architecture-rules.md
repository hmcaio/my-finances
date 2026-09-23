# 0014. Enforce architecture rules with ArchUnit, with `Transaction` depending on `CategoryType` as a documented exception

Status: Accepted
Date: 2026-09-23

## Context
[0004](0004-hexagonal-ddd-tdd.md), [0005](0005-single-point-uuid-generation.md) and `backend/CLAUDE.md` state several architecture rules in prose: domain packages do not import another aggregate, the domain does not log, ids come only from `IdGenerator`, and errors are handled by one `@RestControllerAdvice`. Prose rules erode silently. The test audit (issue #31, B15) turned them into tests. The first rule written, "no domain package depends on another aggregate's package", failed on real code: `domain.transaction.Transaction` holds a `CategoryType` (`INCOME` or `EXPENSE`) copied from the category when the transaction is created. That forced a decision on whether the rule or the code was wrong.

## Decision
- **Architecture rules that can be checked mechanically are ArchUnit tests** (`ArchitectureTest`, scanning main sources only), and prose in `CLAUDE.md` describes a rule only where it is not, or not yet, enforced. The current rules:
  - the domain never logs;
  - the domain never depends on another aggregate's package;
  - exactly one `@RestControllerAdvice` exists, and `@ExceptionHandler` appears only in `GlobalExceptionHandler`;
  - only `RandomUuidGenerator` calls `UUID.randomUUID()`;
  - every `@Entity` extends `AuditableEntity`;
  - `*JpaRepository` interfaces are package-private;
  - the domain does not depend on Spring, Jakarta or Lombok (`org.springframework.data.domain..` is allowed).
- **`Transaction` -> `CategoryType` is a named exception to the cross-aggregate rule**, coded in the rule's condition rather than by loosening it (`domain.shared` is likewise excluded). The rule stays strict for everything else, so a new cross-aggregate dependency still fails the build.
- **The denormalization stays.** A transaction stores its type at creation, and the application service validates it against the category through the category repository port. Keeping the type on the row means transaction queries, filters and reports (spend per category type, budget-versus-actual) need no join, and a transaction stays self-describing when read on its own. The type never changes for a category, so the copy cannot go stale in the normal flow.

## Alternatives considered
- **Re-derive the type from `categoryId` and drop it from `Transaction`.** The purest model, and it would remove the exception, but it costs a join or extra lookup on every read path that needs the type, a migration to drop the column, and rewriting the domain's own type invariants, for no user-visible gain. Not worth it now; if the exception starts to multiply, this is the refactor to revisit.
- **Move `CategoryType` to `domain.shared`.** Rejected: it is a category concept, and the shared package is for genuinely cross-cutting primitives such as `IdGenerator` and `TextFieldConstraints`.
- **Loosen the rule to allow any domain-to-domain dependency.** Rejected: it would turn the rule into a formality.
- **Leave the rules as prose.** Rejected: that is the state that let them go unchecked.

## Consequences
- A new architecture rule that is worth stating in `CLAUDE.md` should come with an ArchUnit test if it can be written as one.
- Adding a second cross-aggregate domain dependency fails the build; the person adding it must either avoid it or record why it is another exception (an entry in the rule and in `backend/CLAUDE.md`).
- `Transaction` depending on `CategoryType` is now discoverable in the rule itself, not just in one class's javadoc.
- The rules scan main sources only, so the test tree is free to import across aggregates.
