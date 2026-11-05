---
name: audit
description: Read-only audit of the my-finances codebase for gaps and risks (schema constraints, transaction boundaries, frontend duplication, missing tests, doc drift, reuse candidates), ending in a ranked report and, on request, a GitHub issue. Use when the user asks to audit, review or check the codebase, schema, frontend or backend, or says "review X for gaps". Not for building a planned feature (implement-feature) and not for fixing (fix-issue).
---

# Audit

Cross-cutting findings (a class of bug repeated across many files, a missing constraint, duplicated UI) belong to no single `docs/features/FXXX`, so they are tracked as an issue and fixed by `fix-issue`. This skill finds and reports; it changes no files.

## 1. Read

Scope the audit from the user's request and read the actual code, never infer from filenames. Done when every finding is backed by a file you opened.

- **Verify every "missing" finding by reading the file it is supposedly missing from.** An empty grep is weak evidence (a mistyped pattern looks identical to a true absence). In a past audit, "amount positivity isn't enforced in the domain layer" came from a broken grep; the domain classes already checked it and only the DB layer was missing. A false gap costs the user's trust and the fix effort.
- **Check what already exists before proposing to add it.** Look at how sibling aggregates and pages handled the same concern (does F004's `Transaction` already have `requireValidAmount`?) so the finding is scoped to the real gap.
- When the audit concerns conventions, also read the not-yet-built `docs/features/`: fixing a spec now is cheaper than a retrofit migration later.

## 2. Report

Reply in chat with findings ranked by severity. For each: file and line, the concrete failure scenario (inputs/state → what breaks), and the proposed fix. Say what you looked at and decided *not* to change, with the reason: a deliberate non-fix (a self-healing job where a transaction would be worse) is a finding too, and it stops the user re-asking. Ask about genuine product decisions instead of assuming ("should `accounts.name` be globally unique?"), with AskUserQuestion when the answer changes the schema or behavior.

## 3. File an issue

When the user asks: invoke `write-issue`, one row per finding in its `## Findings` table. Findings live in GitHub issues; planned features stay in `docs/features/FXXX`.

Stop there. The user trims scope, and the fix runs as `fix-issue #N`. Close by giving the issue number and offering that.
