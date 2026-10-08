import { useMemo } from 'react'
import { Box, Skeleton, Typography } from '@mui/material'
import type { FiiAllocationBasis, FiiAllocationRow } from '../../api/investments/fiiAllocation'
import { useFiiAllocation } from '../../api/investments/fiiAllocationQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import {
  CENTER,
  SIZE,
  SLICE_COLORS,
  VISUALLY_HIDDEN,
  segmentPath,
} from '../investments/donutGeometry'

const NO_SEGMENT_KEY = 'none'

// Two radius bands sharing the chart's SIZE=200/CENTER=100 canvas, a 4px gap between them.
const OUTER_RING_OUTER_RADIUS = 90
const OUTER_RING_INNER_RADIUS = 68
const INNER_RING_OUTER_RADIUS = 64
const INNER_RING_INNER_RADIUS = 40

interface TickerSlice {
  key: string
  label: string
  percentage: number
  color: string
}

interface SegmentSlice {
  key: string
  label: string
  /** Sum of this segment's own ticker percentages, not the backend's independently-rounded
   * segment-row percentage - so the inner arc's width always exactly matches what its outer-ring
   * children add up to, with no rounding seam between the two rings. */
  percentage: number
  color: string
  tickers: TickerSlice[]
}

interface FiiNestedAllocationDonutChartProps {
  basis: FiiAllocationBasis
  /** `YYYY-MM`, defaults to the current month (Addendum: Month Selector). */
  month: string
  ariaLabel: string
  emptyMessage: string
}

/** `#rrggbb` at a given opacity, so a segment's tickers read as shades of their parent's color. */
function withOpacity(hex: string, opacity: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${opacity})`
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
 * One of the two FII nested allocation donuts (Addendum - Nested Allocation Charts): the inner
 * ring is a segment chart, the outer ring the tickers within each segment, replacing the four flat
 * single-ring charts F026 originally shipped. Fetches both the `SEGMENT` and `TICKER` groupings for
 * `basis` (the same two queries the four old charts made between them - no new network calls) and
 * nests the ticker rows under their segment via the `segmentId` each ticker row now carries. A
 * segment's inner arc and its tickers' outer arcs are computed from the exact same cumulative
 * percentages (the segment's "size" here is the sum of its own tickers, not the backend's
 * independently-rounded segment-row percentage), so the two rings tile with no visible seam.
 */
export function FiiNestedAllocationDonutChart({
  basis,
  month,
  ariaLabel,
  emptyMessage,
}: FiiNestedAllocationDonutChartProps) {
  const segmentQuery = useFiiAllocation(basis, 'SEGMENT', month)
  const tickerQuery = useFiiAllocation(basis, 'TICKER', month)
  const segmentRows = segmentQuery.data
  const tickerRows = tickerQuery.data
  const state = combineLoadState(useQueryState(segmentQuery), useQueryState(tickerQuery))
  const showSkeleton = useDelayedFlag(state.loading)

  const segments = useMemo<SegmentSlice[]>(() => {
    if (!segmentRows || !tickerRows) return []
    const tickersBySegment = groupTickersBySegment(tickerRows)
    return segmentRows.map((segmentRow, index) => {
      const segmentKey = segmentRow.key ?? NO_SEGMENT_KEY
      const color = SLICE_COLORS[index % SLICE_COLORS.length]
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
          color: withOpacity(color, Math.max(1 - tickerIndex * 0.22, 0.35)),
        })),
      }
    })
  }, [segmentRows, tickerRows])

  const drawnSegments = segments.filter((s) => s.percentage > 0)
  const segmentArcs = drawnSegments.map((segment, index) => {
    const before = drawnSegments.slice(0, index).reduce((sum, s) => sum + s.percentage, 0)
    return {
      segment,
      start: (before / 100) * Math.PI * 2,
      end: ((before + segment.percentage) / 100) * Math.PI * 2,
    }
  })
  const flatTickers = drawnSegments.flatMap((s) => s.tickers.filter((t) => t.percentage > 0))
  const tickerArcs = flatTickers.map((ticker, index) => {
    const before = flatTickers.slice(0, index).reduce((sum, t) => sum + t.percentage, 0)
    return {
      ticker,
      start: (before / 100) * Math.PI * 2,
      end: ((before + ticker.percentage) / 100) * Math.PI * 2,
    }
  })

  if (state.loadError) {
    return (
      <LoadFailedNotice
        message={state.loadError}
        onRetry={() => {
          state.reload()
        }}
      />
    )
  }
  if (showSkeleton) {
    return (
      <Box role="status" aria-label={`Loading ${ariaLabel.toLowerCase()}`}>
        <Box component="span" sx={VISUALLY_HIDDEN}>
          Loading…
        </Box>
        <Skeleton variant="rounded" height={SIZE} />
      </Box>
    )
  }
  if (!segmentRows || !tickerRows) return null

  if (drawnSegments.length === 0) {
    return (
      <Typography color="text.secondary" sx={fadeInSx}>
        {emptyMessage}
      </Typography>
    )
  }

  return (
    <Box sx={fadeInSx}>
      <Box sx={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}>
        <Box
          component="svg"
          viewBox={`0 0 ${SIZE} ${SIZE}`}
          role="img"
          aria-label={ariaLabel}
          sx={{ width: SIZE, height: SIZE, flexShrink: 0 }}
        >
          {segmentArcs.map(({ segment, start, end }) => (
            <path
              key={`segment-${segment.key}`}
              d={segmentPath(start, end, INNER_RING_OUTER_RADIUS, INNER_RING_INNER_RADIUS)}
              fill={segment.color}
              stroke="none"
            >
              <title>{`${segment.label}: ${segment.percentage.toFixed(1)}%`}</title>
            </path>
          ))}
          {tickerArcs.map(({ ticker, start, end }) => (
            <path
              key={`ticker-${ticker.key}`}
              d={segmentPath(start, end, OUTER_RING_OUTER_RADIUS, OUTER_RING_INNER_RADIUS)}
              fill={ticker.color}
              stroke="none"
            >
              <title>{`${ticker.label}: ${ticker.percentage.toFixed(1)}%`}</title>
            </path>
          ))}
          <text
            x={CENTER}
            y={CENTER}
            textAnchor="middle"
            dominantBaseline="middle"
            fontSize="14"
            fill="currentColor"
          >
            100%
          </text>
        </Box>

        <Box
          component="ul"
          aria-label={`${ariaLabel} legend`}
          sx={{ listStyle: 'none', p: 0, m: 0 }}
        >
          {drawnSegments.map((segment) => (
            <Box component="li" key={segment.key} sx={{ py: 0.25 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', px: 1 }}>
                <Box
                  component="span"
                  aria-hidden="true"
                  sx={{
                    display: 'inline-block',
                    width: 12,
                    height: 12,
                    borderRadius: '50%',
                    bgcolor: segment.color,
                    mr: 1,
                  }}
                />
                <Typography component="span" sx={{ fontWeight: 'bold', mr: 2 }}>
                  {segment.label}
                </Typography>
                <Typography component="span" color="text.secondary">
                  {segment.percentage.toFixed(1)}%
                </Typography>
              </Box>
              <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0, pl: 3 }}>
                {segment.tickers.map((ticker) => (
                  <Box component="li" key={ticker.key} sx={{ py: 0.25, px: 1 }}>
                    <Box
                      component="span"
                      aria-hidden="true"
                      sx={{
                        display: 'inline-block',
                        width: 12,
                        height: 12,
                        borderRadius: '50%',
                        bgcolor: ticker.color,
                        mr: 1,
                      }}
                    />
                    <Typography component="span" sx={{ mr: 2 }}>
                      {ticker.label}
                    </Typography>
                    <Typography component="span" color="text.secondary">
                      {ticker.percentage.toFixed(1)}%
                    </Typography>
                  </Box>
                ))}
              </Box>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  )
}
