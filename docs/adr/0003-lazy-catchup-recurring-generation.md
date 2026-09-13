# 0003. Generate recurring occurrences lazily/catch-up instead of real-time scheduling

Status: Accepted
Date: 2026-09-12

## Context
Recurring templates ([0002](0002-versioned-budget-and-recurring-template.md)) need to produce a pending transaction occurrence each cycle (e.g. on `day_of_month`). The app is explicitly designed to run on-demand — brought up when the user wants to use it, brought down otherwise (PRD §7.3) — not kept always-on as a background service. A real-time scheduler (e.g. a cron trigger firing on the exact day) would silently miss any cycle that falls while the app is off, which is expected to be most of the time for a personal, locally-run tool.

## Decision
Recurring-occurrence generation is lazy/catch-up: on backend startup or the next relevant request, for each active template, the system generates one pending occurrence for every cycle whose `day_of_month` has passed since the last occurrence was generated — each dated correctly for its own cycle, using whichever template version was effective for that specific past month. If the app was off for two months, coming back online produces two dated pending occurrences to confirm, not zero and not a merged one.

## Consequences
- No scheduler/cron infrastructure needed — generation is just a query run opportunistically.
- Requires tracking "last generated cycle" per template (`last_generated_for`) and a lightweight `PendingRecurringOccurrence` record that survives restarts (a pending occurrence isn't a `Transaction` yet, since it might be adjusted or dismissed before confirmation).
- Confirming a pending occurrence creates a real `Transaction` linked to the specific `RecurringTemplateVersion` that generated it; the user may override amount/date/account at confirmation time without creating a new template version.
- This is the concrete reason the on-demand runtime model (PRD §7.3) had to be an explicit, stated constraint rather than an implicit assumption — it directly shapes this feature's design (F007).
