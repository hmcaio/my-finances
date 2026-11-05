---
name: write-issue
description: Writes and files a GitHub issue for my-finances (bug, refactor, gap, docs fix) so someone can fix it later without the original conversation. Use when the user asks to create, file, open or write up an issue, or says "track this" or "make an issue for this". Not for a planned feature (docs/features/FXXX, use design-feature) and not for finding the problems in the first place (audit).
---

# Write issue

An issue is read cold, weeks later, by whoever fixes it (often an agent with none of this conversation). It must carry the evidence, the cause and a concrete fix, so the reader starts working instead of re-investigating.

## 1. Place it

- Planned feature → `docs/features/FXXX`, not an issue.
- Duplicate → `gh issue list --state all --search "<keywords>"` first; extend the existing issue with a comment instead of opening a second.
- Pick the type: **bug** (label `bug`), **improvement** or refactor (`enhancement`), **docs** (`documentation`). Use only labels that exist (`gh label list`); no fitting label means no label.

## 2. Gather evidence

Read the code and run the failing thing; copy real output, with the date and the command, into a `text` block. Each claim in the body is **observed** (you ran or read it) or **inferred**; label inferred ones ("observed once", "static review, tests not run"). A fix idea you have not checked against the code stays out.

Evidence comes from a finance app: replace amounts, descriptions, account/category names and balances with placeholders. Ids and counts stay (ADR 0011).

## 3. Write

**Title**: the outcome in plain words, under ~80 characters, no issue number. A refactor or tooling change takes the Conventional Commit prefix (`refactor(frontend): ...`); a bug states the symptom.

**Body**, in this order; drop a section only when it has nothing to say:

1. `## Summary`: what is wrong and what it costs (a blocked PR, a lost backup), in two or three sentences.
2. `## What happens` (bug) or `## Findings` (several items; a table of finding / evidence / proposed fix / effort, and a `## Suggested order` when they depend on each other). Name files as `path:line`.
3. `## Why nothing caught it`: the missing test, check or signal. This is the part that prevents the next one.
4. `## Fix`: concrete steps naming the files, constraints and docs to change, ending on a checkable **done when** (a test that fails before and passes after, a command that returns the expected output).
5. `## Out of scope / deliberately not changed`: what a reader might assume is included, and why it is not.

Write for a reader who knows the repo but not today's discussion: no "as discussed", no pronouns pointing at the chat.

## 4. File

1. Write the body to the scratchpad as `issue-body.md`, show title, labels and body to the user.
2. After approval: `gh issue create --title "..." --body-file <path> [--label bug]`.
3. Report the number and URL; the fix commit says `Closes #N`.
