import type { FiiAllocationRow } from '../../../api/investments/fii/fiiAllocation'
import { useFiiAllocation } from '../../../api/investments/fii/fiiAllocationQueries'
import { SLICE_COLORS } from '../overview/lib/donutGeometry'

/** The key of the "No segment" bucket, whose backend row has a `null` key. */
export const NO_SEGMENT_KEY = 'none'

export interface TickerSlice {
  key: string
  label: string
  percentage: number
  color: string
}

export interface SegmentSlice {
  key: string
  label: string
  /** Sum of this segment's own ticker percentages, not the backend's independently-rounded
   * segment-row percentage - so the inner arc's width always exactly matches what its outer-ring
   * children add up to, with no rounding seam between the two rings. */
  percentage: number
  color: string
  tickers: TickerSlice[]
}

/** `#rrggbb` at a given opacity, so a segment's tickers read as shades of their parent's color. */
export function withOpacity(hex: string, opacity: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${opacity})`
}

/** The shade of the ticker at `index` within its segment (the first is the segment's own color). */
export function tickerColor(segmentColor: string, index: number): string {
  return withOpacity(segmentColor, Math.max(1 - index * 0.22, 0.35))
}

function groupTickersBySegment(rows: FiiAllocationRow[]): Map<string, FiiAllocationRow[]> {
  const bySegment = new Map<string, FiiAllocationRow[]>()
  for (const row of rows) {
    const key = row.segmentId ?? NO_SEGMENT_KEY
    const list = bySegment.get(key)
    if (list) {
      list.push(row)
    } else {
      bySegment.set(key, [row])
    }
  }
  return bySegment
}

/**
 * The segments of one basis with their tickers nested under them (the `segmentId` each ticker row
 * carries), colored through `colorOf` so the Actual and Planned donuts and the comparison table
 * agree on a segment's color.
 */
export function buildSegmentSlices(
  segmentRows: FiiAllocationRow[],
  tickerRows: FiiAllocationRow[],
  colorOf: (segmentKey: string) => string,
): SegmentSlice[] {
  const tickersBySegment = groupTickersBySegment(tickerRows)
  return segmentRows.map((segmentRow) => {
    const segmentKey = segmentRow.key ?? NO_SEGMENT_KEY
    const color = colorOf(segmentKey)
    const tickers = tickersBySegment.get(segmentKey) ?? []
    return {
      key: segmentKey,
      label: segmentRow.label,
      percentage: tickers.reduce((sum, t) => sum + t.percentage, 0),
      color,
      tickers: tickers.map((ticker, tickerIndex) => ({
        key: ticker.key ?? NO_SEGMENT_KEY,
        label: ticker.label,
        percentage: ticker.percentage,
        color: tickerColor(color, tickerIndex),
      })),
    }
  })
}

/**
 * One color per segment, shared by both donuts and the comparison table. The backend orders each
 * basis by its own sizes, so coloring by position would give one segment two colors; here the
 * segments are numbered once, Actual's order first and then any segment only the plan has.
 */
export function useFiiSegmentPalette(month: string): {
  /** False until both segment queries have settled (a failure counts as settled). */
  ready: boolean
  colorOf: (segmentKey: string) => string
} {
  const actual = useFiiAllocation('ACTUAL', 'SEGMENT', month)
  const planned = useFiiAllocation('PLANNED', 'SEGMENT', month)
  const keys = [...new Set([...(actual.data ?? []), ...(planned.data ?? [])].map(segmentKeyOf))]
  return {
    ready: !actual.isPending && !planned.isPending,
    colorOf: (segmentKey) =>
      SLICE_COLORS[Math.max(0, keys.indexOf(segmentKey)) % SLICE_COLORS.length],
  }
}

function segmentKeyOf(row: FiiAllocationRow): string {
  return row.key ?? NO_SEGMENT_KEY
}

/** One row of the comparison table: a segment or one of its tickers, on both bases. */
export interface ComparisonRow {
  key: string
  label: string
  color: string
  isSegment: boolean
  /** The share on each basis, or `null` where the row does not exist on that basis. */
  actual: number | null
  planned: number | null
}

/**
 * Segments (Actual's order first, then those only the plan has), each followed by its tickers
 * (again Actual's first). Only slices that are drawn (above 0%) count, as in the donuts. A ticker
 * takes the shade it has in the first basis that lists it.
 */
export function buildComparisonRows(
  actual: SegmentSlice[],
  planned: SegmentSlice[],
): ComparisonRow[] {
  const drawn = (slices: SegmentSlice[]) => slices.filter((s) => s.percentage > 0)
  const actualBySegment = new Map(drawn(actual).map((s) => [s.key, s]))
  const plannedBySegment = new Map(drawn(planned).map((s) => [s.key, s]))
  const segmentKeys = [...new Set([...actualBySegment.keys(), ...plannedBySegment.keys()])]
  const tickersOf = (slice: SegmentSlice | undefined) =>
    new Map((slice?.tickers ?? []).filter((t) => t.percentage > 0).map((t) => [t.key, t]))

  return segmentKeys.flatMap((segmentKey) => {
    const a = actualBySegment.get(segmentKey)
    const p = plannedBySegment.get(segmentKey)
    const segment = (a ?? p) as SegmentSlice
    const actualTickers = tickersOf(a)
    const plannedTickers = tickersOf(p)
    const tickerKeys = [...new Set([...actualTickers.keys(), ...plannedTickers.keys()])]
    const segmentRow: ComparisonRow = {
      key: `segment-${segment.key}`,
      label: segment.label,
      color: segment.color,
      isSegment: true,
      actual: a?.percentage ?? null,
      planned: p?.percentage ?? null,
    }
    const tickerRows = tickerKeys.map((tickerKey): ComparisonRow => {
      const at = actualTickers.get(tickerKey)
      const pt = plannedTickers.get(tickerKey)
      const ticker = (at ?? pt) as TickerSlice
      return {
        key: `ticker-${segment.key}-${ticker.key}`,
        label: ticker.label,
        color: ticker.color,
        isSegment: false,
        actual: at?.percentage ?? null,
        planned: pt?.percentage ?? null,
      }
    })
    return [segmentRow, ...tickerRows]
  })
}
