import { useMemo } from 'react'
import { Box, Skeleton, Typography } from '@mui/material'
import type { FiiAllocationBasis, FiiAllocationGroupBy } from '../../api/investments/fiiAllocation'
import { useFiiAllocation } from '../../api/investments/fiiAllocationQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import {
  CENTER,
  SIZE,
  SLICE_COLORS,
  VISUALLY_HIDDEN,
  segmentPath,
} from '../investments/donutGeometry'

interface Slice {
  key: string
  label: string
  percentage: number
  color: string
}

interface FiiAllocationDonutChartProps {
  basis: FiiAllocationBasis
  groupBy: FiiAllocationGroupBy
  ariaLabel: string
  emptyMessage: string
}

/**
 * One of the four FII allocation donuts (F026 spec, ADR 0023): actual/planned x ticker/segment.
 * Reuses `donutGeometry.ts`'s pure SVG-arc math (frontend `CLAUDE.md`'s "no third hand-rolled
 * copy" rule) and mirrors `FlatAllocationDonutChart`'s rendering shape, but its own component -
 * the metric here is always a percentage (already `0`-`100`, summing to `100`), never a money
 * total (basis `PLANNED` has none at all), so the legend/center label read differently from the
 * money-based chart and aren't a drop-in prop-for-prop reuse of it.
 */
export function FiiAllocationDonutChart({
  basis,
  groupBy,
  ariaLabel,
  emptyMessage,
}: FiiAllocationDonutChartProps) {
  const query = useFiiAllocation(basis, groupBy)
  const rows = query.data
  const state = useQueryState(query)
  const showSkeleton = useDelayedFlag(state.loading)

  const slices = useMemo<Slice[]>(
    () =>
      (rows ?? []).map((row, index) => ({
        key: row.key ?? 'none',
        label: row.label,
        percentage: row.percentage,
        color: SLICE_COLORS[index % SLICE_COLORS.length],
      })),
    [rows],
  )

  const drawn = slices.filter((slice) => slice.percentage > 0)
  const arcs = drawn.map((slice, index) => {
    const before = drawn.slice(0, index).reduce((sum, s) => sum + s.percentage, 0)
    return {
      slice,
      start: (before / 100) * Math.PI * 2,
      end: ((before + slice.percentage) / 100) * Math.PI * 2,
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
          {arcs.map(({ slice, start, end }) => (
            <path key={slice.key} d={segmentPath(start, end)} fill={slice.color} stroke="none">
              <title>{`${slice.label}: ${slice.percentage.toFixed(1)}%`}</title>
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
          {slices.map((slice) => (
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
              </Typography>
              <Typography component="span" color="text.secondary">
                {slice.percentage.toFixed(1)}%
              </Typography>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  )
}
