import { useMemo, useState } from 'react'
import { Box, Button, Skeleton, Typography } from '@mui/material'
import { getInvestmentAllocation, type AllocationRow } from '../../api/investmentAllocation'
import { fadeInSx } from '../../components/fadeIn'
import { LoadFailedNotice } from '../../components/LoadFailedNotice'
import { combineLoadState, useAsyncData } from '../../hooks/useAsyncData'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'

// Categorical colors that stay distinguishable on both the light and the dark theme.
const SLICE_COLORS = [
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
const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

const SIZE = 200
const RADIUS = 90
const INNER_RADIUS = 52
const CENTER = SIZE / 2

interface Slice {
  key: string
  label: string
  value: number
  needsSnapshot: boolean
  color: string
  /** Set for a category slice: clicking it drills into that category's sub-categories. */
  categoryId: string | null
}

function pointOnCircle(angle: number, radius: number): [number, number] {
  return [CENTER + radius * Math.sin(angle), CENTER - radius * Math.cos(angle)]
}

/** The SVG path of a donut segment from `start` to `end` (radians, clockwise from 12 o'clock). */
function segmentPath(start: number, end: number): string {
  // A full circle can't be one arc: split it in two halves.
  if (end - start >= Math.PI * 2 - 1e-6) {
    return `${segmentPath(start, start + Math.PI)} ${segmentPath(start + Math.PI, start + Math.PI * 2 - 1e-4)}`
  }
  const large = end - start > Math.PI ? 1 : 0
  const [x1, y1] = pointOnCircle(start, RADIUS)
  const [x2, y2] = pointOnCircle(end, RADIUS)
  const [x3, y3] = pointOnCircle(end, INNER_RADIUS)
  const [x4, y4] = pointOnCircle(start, INNER_RADIUS)
  return [
    `M${x1},${y1}`,
    `A${RADIUS},${RADIUS} 0 ${large} 1 ${x2},${y2}`,
    `L${x3},${y3}`,
    `A${INNER_RADIUS},${INNER_RADIUS} 0 ${large} 0 ${x4},${y4}`,
    'Z',
  ].join(' ')
}

/**
 * Allocation of investments by category, drilling into sub-categories on click (F009 spec): the
 * latest snapshot of every product, grouped. A prop-less widget that fetches its own data on mount
 * (frontend `CLAUDE.md`, "Pages that embed other pages' widgets"), so F012's dashboard can drop it
 * in. A footnote explains the asterisk on any slice with a product whose latest trade is newer
 * than its latest snapshot (`needsSnapshot`), i.e. whose value may be out of date.
 *
 * Both groupings are fetched up front - a category's total equals the sum of its sub-category rows
 * - so drilling in is instant. Hand-drawn SVG donut, no chart dependency.
 */
export function InvestmentAllocationChart() {
  const { data: byCategory, ...categoryState } = useAsyncData(
    () => getInvestmentAllocation({ groupBy: 'CATEGORY' }),
    [],
  )
  const { data: bySubcategory, ...subcategoryState } = useAsyncData(
    () => getInvestmentAllocation({ groupBy: 'SUBCATEGORY' }),
    [],
  )
  const [drilledCategoryId, setDrilledCategoryId] = useState<string | null>(null)

  const state = combineLoadState(categoryState, subcategoryState)
  const showSkeleton = useDelayedFlag(state.loading)

  const drilledCategory = byCategory?.find((row) => row.categoryId === drilledCategoryId) ?? null
  const slices = useMemo<Slice[]>(() => {
    const rows: AllocationRow[] =
      drilledCategory && bySubcategory
        ? bySubcategory.filter((row) => row.categoryId === drilledCategory.categoryId)
        : (byCategory ?? [])
    return rows.map((row, index) => ({
      key: `${row.categoryId}/${row.subcategoryId ?? ''}`,
      label: drilledCategory ? (row.subcategoryName ?? 'No sub-category') : row.categoryName,
      value: row.totalValue,
      needsSnapshot: row.needsSnapshot,
      color: SLICE_COLORS[index % SLICE_COLORS.length],
      categoryId: drilledCategory ? null : row.categoryId,
    }))
  }, [byCategory, bySubcategory, drilledCategory])

  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  const anyStale = slices.some((slice) => slice.needsSnapshot)

  // Slice angles in order; zero-value slices (a stale product with no value yet) stay in the
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
      <Box role="status" aria-label="Loading allocation">
        <Box component="span" sx={VISUALLY_HIDDEN}>
          Loading…
        </Box>
        <Skeleton variant="rounded" height={SIZE} />
      </Box>
    )
  }
  if (!byCategory || !bySubcategory) return null

  if (byCategory.length === 0) {
    return (
      <Typography color="text.secondary" sx={fadeInSx}>
        No investment values yet - record a snapshot on a product to see the allocation.
      </Typography>
    )
  }

  return (
    <Box sx={fadeInSx}>
      {drilledCategory && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <Button size="small" onClick={() => setDrilledCategoryId(null)}>
            &larr; All categories
          </Button>
          <Typography variant="subtitle1">{drilledCategory.categoryName}</Typography>
        </Box>
      )}
      <Box sx={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}>
        <Box
          component="svg"
          viewBox={`0 0 ${SIZE} ${SIZE}`}
          role="img"
          aria-label={
            drilledCategory
              ? `Allocation of ${drilledCategory.categoryName} by sub-category`
              : 'Allocation by category'
          }
          sx={{ width: SIZE, height: SIZE, flexShrink: 0 }}
        >
          {arcs.map(({ slice, start, end }) => {
            const percentage = ((slice.value / total) * 100).toFixed(1)
            return (
              <path
                key={slice.key}
                d={segmentPath(start, end)}
                fill={slice.color}
                stroke="none"
                onClick={
                  slice.categoryId ? () => setDrilledCategoryId(slice.categoryId) : undefined
                }
                style={{ cursor: slice.categoryId ? 'pointer' : 'default' }}
              >
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

        <Box component="ul" aria-label="Allocation legend" sx={{ listStyle: 'none', p: 0, m: 0 }}>
          {slices.map((slice) => {
            const percentage = total > 0 ? ((slice.value / total) * 100).toFixed(1) : '0.0'
            const content = (
              <>
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
              </>
            )
            return (
              <Box component="li" key={slice.key} sx={{ py: 0.25 }}>
                {slice.categoryId ? (
                  <Button
                    size="small"
                    color="inherit"
                    sx={{ textTransform: 'none', justifyContent: 'flex-start' }}
                    aria-label={`${slice.label}: show sub-categories`}
                    onClick={() => setDrilledCategoryId(slice.categoryId)}
                  >
                    {content}
                  </Button>
                ) : (
                  <Box sx={{ px: 1, py: 0.5 }}>{content}</Box>
                )}
              </Box>
            )
          })}
        </Box>
      </Box>
      {anyStale && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
          * Includes a product with a buy or sell newer than its latest snapshot, so this value may
          be out of date. Record a snapshot on the product to refresh it.
        </Typography>
      )}
    </Box>
  )
}
