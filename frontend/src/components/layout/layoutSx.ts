/** Shared `sx` values that several features repeated inline. Use a constant for a value that is the
 * same everywhere; a one-off spacing prop (`mb: 3`) stays inline. */

/** Padding of an outlined page section: tighter on phones (F021). `PageSection` applies it. */
export const SECTION_PADDING = { xs: 2, sm: 3 }

/** Long unbroken text (a description, a name) wraps at any point instead of widening its container,
 * which would overflow a 360px viewport. Spread it to add more: `{ ...WRAP_ANYWHERE, mb: 2 }`. */
export const WRAP_ANYWHERE = { overflowWrap: 'anywhere' } as const

/** Minimum width of a compact `Select` in a filter row or a form, so its label stays readable. */
export const SELECT_MIN_WIDTH = { minWidth: 160 } as const

/** A `FormGrid` child that spans every column (a long field). */
export const FORM_GRID_FULL_ROW = { gridColumn: '1 / -1' } as const

/** Width of `Layout`'s navigation drawer; the permanent one (from `lg`) takes this much of the
 * left edge, so anything anchored to the start of the viewport clears it (the success toast). */
export const DRAWER_WIDTH = 240
