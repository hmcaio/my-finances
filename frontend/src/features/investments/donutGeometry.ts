/**
 * Pure SVG donut-chart geometry shared by the three allocation charts (F023): the existing
 * category chart (`InvestmentAllocationChart`, unchanged) keeps its own copy since it predates this
 * file and its behavior/tests must not move; the two new flat charts (`InvestmentSubcategory
 * AllocationChart`, `InvestmentAccountAllocationChart`) both import from here instead of a third
 * copy. No chart library (frontend `CLAUDE.md`'s "Charts" section) - hand-drawn SVG.
 */

// Categorical colors that stay distinguishable on both the light and the dark theme.
export const SLICE_COLORS = [
  '#1976d2',
  '#ed6c02',
  '#2e7d32',
  '#9c27b0',
  '#d32f2f',
  '#0288d1',
  '#795548',
  '#607d8b',
]

/** Screen-reader-only text, the same "Loading…" marker `DataTableBody`'s skeleton carries. */
export const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

export const SIZE = 200
export const RADIUS = 90
export const INNER_RADIUS = 52
export const CENTER = SIZE / 2

export function pointOnCircle(angle: number, radius: number): [number, number] {
  return [CENTER + radius * Math.sin(angle), CENTER - radius * Math.cos(angle)]
}

/**
 * The SVG path of a donut segment from `start` to `end` (radians, clockwise from 12 o'clock).
 * `outerRadius`/`innerRadius` default to the single-ring `RADIUS`/`INNER_RADIUS` constants every
 * existing caller (`FlatAllocationDonutChart`, `InvestmentAllocationChart`'s own copy) already
 * expects; a two-ring chart (`FiiNestedAllocationDonutChart`, Addendum - Nested Allocation Charts)
 * passes its own inner/outer bands for each ring instead.
 */
export function segmentPath(
  start: number,
  end: number,
  outerRadius: number = RADIUS,
  innerRadius: number = INNER_RADIUS,
): string {
  // A full circle can't be one arc: split it in two halves.
  if (end - start >= Math.PI * 2 - 1e-6) {
    return `${segmentPath(start, start + Math.PI, outerRadius, innerRadius)} ${segmentPath(start + Math.PI, start + Math.PI * 2 - 1e-4, outerRadius, innerRadius)}`
  }
  const large = end - start > Math.PI ? 1 : 0
  const [x1, y1] = pointOnCircle(start, outerRadius)
  const [x2, y2] = pointOnCircle(end, outerRadius)
  const [x3, y3] = pointOnCircle(end, innerRadius)
  const [x4, y4] = pointOnCircle(start, innerRadius)
  return [
    `M${x1},${y1}`,
    `A${outerRadius},${outerRadius} 0 ${large} 1 ${x2},${y2}`,
    `L${x3},${y3}`,
    `A${innerRadius},${innerRadius} 0 ${large} 0 ${x4},${y4}`,
    'Z',
  ].join(' ')
}
