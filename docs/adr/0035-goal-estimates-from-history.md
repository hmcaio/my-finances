# 0035. Add history-based estimates to goals: a current-pace line and a contribution suggestion

Status: Accepted (amends [0034](0034-goals-as-allocation-and-deterministic-projection.md))
Date: 2026-11-09

## Context
[ADR 0034](0034-goals-as-allocation-and-deterministic-projection.md) projects a goal from numbers the user types: a planned monthly contribution and three real return rates. It deferred anything derived from what actually happened, because deriving a *return* from history is the computed gain/loss PRD §3 forbids, and because tracking actual contributions per goal was out of scope.

The user still wants the app to answer "what does my own history say?": at the pace the goal's value has really grown, when would I reach the target, and what do I actually put in each month? Both are visible in data the app already has (the goal's monthly value history and each product's monthly `contributed` series, F009/F038), without ever computing a return.

## Decision
- **A "current pace" line**, shown as a fourth line beside the three scenarios and labeled "based on history". It is a straight line from the goal's value now, rising by the **average monthly increase of the goal's value over the last 12 months of history**: `(value now − value at the start of the window) ÷ months between`. It includes contributions and market moves together, because the history cannot tell them apart; it is a pace, not a return. Rejected: a regression slope or a compounding rate (harder to explain, and a compounded rate is a return estimate), and a pace on the dashboard card (the card keeps the base scenario).
- **The window starts at the first month that has a value, and after the latest opening position** among the allocated products' holdings ([ADR 0033](0033-opening-position-as-cashless-holding-start.md)). An opening position is existing money recorded in one step, so counting its jump as growth would show a huge fake pace. With fewer than **4 monthly points** left the pace is not drawn ("Not enough history for a pace yet").
- **A contribution suggestion**, one click beside "Planned monthly contribution" in the goal form: for each allocated product, its monthly `contributed` (buys minus sells, taxes included) **minus opening-position costs**, times the goal's percentage, summed, then averaged over the **last 12 full months** starting at the first month with a contribution, and floored at 0 (the planned contribution cannot be negative). It fills the field and stores nothing, like the target helpers.
- **Both are computed on read and never stored.** They reuse the history and series queries; there is no migration and no new table.
- **Nominal, not deflated.** The history is in recorded (nominal) money, and the app has no inflation data. The pace is shown as it is, with a note "Pace is based on recorded values, not adjusted for inflation"; the three scenarios keep their "today's money" caption.
- **Percentages are today's.** Past splits are not stored (ADR 0034), so the pace and the suggestion apply the current split to the past, with the same "as of the current allocation" basis as the history chart.
- **Still out of scope:** deriving a return from history (PRD §3; PRD §9 keeps "computed investment tracking"), a stored actual contribution per goal, an inflation adjustment, Monte Carlo.

## Consequences
- ADR 0034's "projections use only user-entered assumptions" now has one deliberate exception: the pace line and the suggestion are derived from recorded values. The three scenarios still use only typed numbers, and the pace is drawn apart from them, labeled and in a different style.
- The pace is only as good as the snapshots: a stale month reads as zero growth followed by a jump. The 4-point minimum and the opening-position rule limit the worst cases, but a user who updates snapshots rarely will get a rough line.
- A nominal straight line over many years overstates the real result; the note says so, and the three real-return scenarios remain the primary projection.
- The pace and the suggestion move whenever allocation percentages change, since the past is re-split with today's numbers.
- A future feature storing allocation history or actual contributions per goal would supersede the "current split applied to the past" labels.
