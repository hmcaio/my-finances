import { useMemo } from 'react'
import { Box, Skeleton, Typography } from '@mui/material'
import type { AllocationGrouping, AllocationRow } from '../../api/investments/investmentAllocation'
import { useInvestmentAllocation } from '../../api/investments/investmentAllocationQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { CENTER, SIZE, SLICE_COLORS, VISUALLY_HIDDEN, segmentPath } from './donutGeometry'

interface Slice {
  key: string
  label: string
  value: number
  needsSnapshot: boolean
  color: string
}

interface FlatAllocationDonutChartProps {
  /** Which allocation grouping this chart shows - always rendered flat, no drill-down (F023). */
  groupBy: AllocationGrouping
  ariaLabel: string
  getKey: (row: AllocationRow) => string
  getLabel: (row: AllocationRow) => string
  emptyMessage: string
  staleFootnote: string
}

/**
 * The shared rendering for the two new flat allocation donuts (F023 - sub-category and account),
 * both "each group's true share of the whole portfolio" with no drill-down: unlike
 * `InvestmentAllocationChart`'s category view, there's no second, filtered fetch and no clickable
 * legend. Kept as its own component (rather than folded into `InvestmentAllocationChart`, which
 * stays unchanged) so that chart's tests and behavior are untouched.
 *
 * Hand-drawn SVG donut sharing `donutGeometry`'s pure geometry helpers - no chart library (frontend
 * `CLAUDE.md`'s "Charts" section).
 */
export function FlatAllocationDonutChart({
  groupBy,
  ariaLabel,
  getKey,
  getLabel,
  emptyMessage,
  staleFootnote,
}: FlatAllocationDonutChartProps) {
  const query = useInvestmentAllocation({ groupBy })
  const rows = query.data
  const state = useQueryState(query)
  const showSkeleton = useDelayedFlag(state.loading)

  const slices = useMemo<Slice[]>(
    () =>
      (rows ?? []).map((row, index) => ({
        key: getKey(row),
        label: getLabel(row),
        value: row.totalValue,
        needsSnapshot: row.needsSnapshot,
        color: SLICE_COLORS[index % SLICE_COLORS.length],
      })),
    [rows, getKey, getLabel],
  )

  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  const anyStale = slices.some((slice) => slice.needsSnapshot)

  // Slice angles in order; zero-value slices (a stale holding with no value yet) stay in the
  // legend only.
  const drawn = slices.filter((slice) => slice.value > 0)
  const arcs = drawn.map((slice, index) => {
    const before = drawn.slice(0, index).reduce((sum, s) => sum + s.value, 0)
    return {
      slice,
      start: (before / total) * Math.PI * 2,
      end: ((before + slice.value) / total) * Math.PI * 2,
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
  if (!rows) return null

  if (rows.length === 0) {
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
          {arcs.map(({ slice, start, end }) => {
            const percentage = ((slice.value / total) * 100).toFixed(1)
            return (
              <path key={slice.key} d={segmentPath(start, end)} fill={slice.color} stroke="none">
                <title>{`${slice.label}: ${slice.value.toFixed(2)} (${percentage}%)`}</title>
              </path>
            )
          })}
          <text
            x={CENTER}
            y={CENTER}
            textAnchor="middle"
            dominantBaseline="middle"
            fontSize="14"
            fill="currentColor"
          >
            {total.toFixed(2)}
          </text>
        </Box>

        <Box
          component="ul"
          aria-label={`${ariaLabel} legend`}
          sx={{ listStyle: 'none', p: 0, m: 0 }}
        >
          {slices.map((slice) => {
            const percentage = total > 0 ? ((slice.value / total) * 100).toFixed(1) : '0.0'
            return (
              <Box component="li" key={slice.key} sx={{ py: 0.25, px: 1 }}>
                <Box
                  component="span"
                  aria-hidden="true"
                  sx={{
                    display: 'inline-block',
                    width: 12,
                    height: 12,
                    borderRadius: '50%',
                    bgcolor: slice.color,
                    mr: 1,
                  }}
                />
                <Typography component="span" sx={{ mr: 2 }}>
                  {slice.label}
                  {slice.needsSnapshot ? '*' : ''}
                </Typography>
                <Typography component="span" color="text.secondary">
                  {slice.value.toFixed(2)} ({percentage}%)
                </Typography>
              </Box>
            )
          })}
        </Box>
      </Box>
      {anyStale && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
          {staleFootnote}
        </Typography>
      )}
    </Box>
  )
}
