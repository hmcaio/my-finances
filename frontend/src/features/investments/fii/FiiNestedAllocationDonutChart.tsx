import { useTranslation } from 'react-i18next'
import { Box, Skeleton, Typography } from '@mui/material'
import type { FiiAllocationBasis } from '../../../api/investments/fii/fiiAllocation'
import { useFiiAllocation } from '../../../api/investments/fii/fiiAllocationQueries'
import { fadeInSx } from '../../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../../components/feedback/LoadFailedNotice'
import { combineLoadState, useQueryState } from '../../../hooks/queryState'
import { useDelayedFlag } from '../../../hooks/useDelayedFlag'
import { useLocale } from '../../../providers/locale/useLocale'
import { formatNumber } from '../../../i18n/format'
import { CENTER, SIZE, VISUALLY_HIDDEN, segmentPath } from '../overview/lib/donutGeometry'
import { buildSegmentSlices, useFiiSegmentPalette } from './fiiAllocationSlices'

// Two radius bands sharing the chart's SIZE=200/CENTER=100 canvas, a 4px gap between them.
const OUTER_RING_OUTER_RADIUS = 90
const OUTER_RING_INNER_RADIUS = 68
const INNER_RING_OUTER_RADIUS = 64
const INNER_RING_INNER_RADIUS = 40

interface FiiNestedAllocationDonutChartProps {
  basis: FiiAllocationBasis
  /** `YYYY-MM`, defaults to the current month (Addendum: Month Selector). */
  month: string
  ariaLabel: string
  emptyMessage: string
  /**
   * Phones (F040): the donut shrinks to fit a half-width column and its legend is left out,
   * because the page's `FiiAllocationComparison` table is the shared legend there.
   */
  compact?: boolean
}

/**
 * One of the two FII nested allocation donuts (Addendum - Nested Allocation Charts): the inner
 * ring is a segment chart, the outer ring the tickers within each segment, replacing the four flat
 * single-ring charts F026 originally shipped. Fetches both the `SEGMENT` and `TICKER` groupings for
 * `basis` (the same two queries the four old charts made between them - no new network calls) and
 * nests the ticker rows under their segment via the `segmentId` each ticker row now carries. A
 * segment's inner arc and its tickers' outer arcs are computed from the exact same cumulative
 * percentages (the segment's "size" here is the sum of its own tickers, not the backend's
 * independently-rounded segment-row percentage), so the two rings tile with no visible seam. A
 * segment has the same color in the Actual and the Planned donut and in `FiiAllocationComparison`
 * (`useFiiSegmentPalette`).
 */
export function FiiNestedAllocationDonutChart({
  basis,
  month,
  ariaLabel,
  emptyMessage,
  compact = false,
}: FiiNestedAllocationDonutChartProps) {
  const { t } = useTranslation('fii')
  const { locale } = useLocale()
  const segmentQuery = useFiiAllocation(basis, 'SEGMENT', month)
  const tickerQuery = useFiiAllocation(basis, 'TICKER', month)
  const segmentRows = segmentQuery.data
  const tickerRows = tickerQuery.data
  const state = combineLoadState(useQueryState(segmentQuery), useQueryState(tickerQuery))
  // Segment colors come from both bases (see `useFiiSegmentPalette`), so wait for the other one too.
  const palette = useFiiSegmentPalette(month)
  const showSkeleton = useDelayedFlag(state.loading || !palette.ready)

  const segments =
    segmentRows && tickerRows && palette.ready
      ? buildSegmentSlices(segmentRows, tickerRows, palette.colorOf)
      : []

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
      <Box role="status" aria-label={t('donut.loadingAria', { label: ariaLabel.toLowerCase() })}>
        <Box component="span" sx={VISUALLY_HIDDEN}>
          {t('donut.loading')}
        </Box>
        <Skeleton variant="rounded" height={SIZE} />
      </Box>
    )
  }
  if (!segmentRows || !tickerRows || !palette.ready) return null

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
          sx={
            compact
              ? { width: '100%', maxWidth: SIZE, height: 'auto', display: 'block' }
              : { width: SIZE, height: SIZE, flexShrink: 0 }
          }
        >
          {segmentArcs.map(({ segment, start, end }) => (
            <path
              key={`segment-${segment.key}`}
              d={segmentPath(start, end, INNER_RING_OUTER_RADIUS, INNER_RING_INNER_RADIUS)}
              fill={segment.color}
              stroke="none"
            >
              <title>
                {t('donut.sliceTooltip', {
                  label: segment.label,
                  percentage: formatNumber(segment.percentage, locale, 1),
                })}
              </title>
            </path>
          ))}
          {tickerArcs.map(({ ticker, start, end }) => (
            <path
              key={`ticker-${ticker.key}`}
              d={segmentPath(start, end, OUTER_RING_OUTER_RADIUS, OUTER_RING_INNER_RADIUS)}
              fill={ticker.color}
              stroke="none"
            >
              <title>
                {t('donut.sliceTooltip', {
                  label: ticker.label,
                  percentage: formatNumber(ticker.percentage, locale, 1),
                })}
              </title>
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

        {!compact && (
          <Box
            component="ul"
            aria-label={`${ariaLabel}${t('donut.legendAriaSuffix')}`}
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
                    {formatNumber(segment.percentage, locale, 1)}%
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
                        {formatNumber(ticker.percentage, locale, 1)}%
                      </Typography>
                    </Box>
                  ))}
                </Box>
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  )
}
