# Post-mortem

A merged fix answers "what was broken". A post-mortem answers "how did this get here, and could the same shape of bug slip through again". Run it only when the user asks.

1. **Find where the regression was introduced**, not where it was noticed. `git log --oneline -- <file>` across the file's whole history, then `git show <candidate>` on the commits around each suspect change, reading the feature and ADR each belongs to. The issue almost never names the cause; it is usually several features earlier.
2. **Check the fix against the full old logic, not the one reported symptom.** Diff the fix against the code as it stood right before the regressing commit and list every rule that old code enforced. A refactor that dropped one invariant often dropped siblings the symptom never exercised: each must still exist somewhere today, or the fix is half a fix.
3. **Run the regression tests red against the root-cause commit.** This is sharper than the "restore the old code" habit in `SKILL.md`, which only proves the test catches the fix's own before-state. `git show <regressing-commit>^:<path> > <path>` restores the file to just before the regression while keeping the new tests; run them and confirm they fail; then `git checkout HEAD -- <path>`. Tests that pass against that older state do not guard what the post-mortem claims.
4. **Write the causal chain as an issue comment** (`gh issue comment`): which commits, what each changed, why nothing caught it in between. The narrative lives on the issue, never in `CLAUDE.md`.
5. **Add a `CLAUDE.md` or conventions line only for a rule that will bite a different feature in a different refactor**, phrased as the rule. Most post-mortems are narrative only; do not manufacture a rule.
6. **A merged PR's branch is dead.** For a doc-only follow-up after the merge, branch fresh off `main` and `git cherry-pick` the doc commit, then open a new PR.
