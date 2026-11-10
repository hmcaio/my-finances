import { useTranslation } from 'react-i18next'
import { Box, Skeleton, Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material'
import { Typography } from '@mui/material'
import { useFiiAllocation } from '../../../api/investments/fii/fiiAllocationQueries'
import { fadeInSx } from '../../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../../components/feedback/LoadFailedNotice'
import { WRAP_ANYWHERE } from '../../../components/layout/layoutSx'
import { combineLoadState, useQueryState } from '../../../hooks/queryState'
import { useDelayedFlag } from '../../../hooks/useDelayedFlag'
import { formatNumber } from '../../../i18n/format'
import { useLocale, type Locale } from '../../../providers/locale/useLocale'
import { VISUALLY_HIDDEN } from '../overview/lib/donutGeometry'
import {
  buildComparisonRows,
  buildSegmentSlices,
  useFiiSegmentPalette,
} from './fiiAllocationSlices'

/** Shown where a segment or ticker has no value on that basis. */
const NO_VALUE = '—'

interface FiiAllocationComparisonProps {
  /** `YYYY-MM`, the same month the donuts above show. */
  month: string
}

/** A percentage with one decimal, as the donut tooltips show it. */
function percent(value: number | null, locale: Locale): string {
  return value === null ? NO_VALUE : formatNumber(value, locale, 1)
}

/** Actual minus Planned in percentage points; a side with no value counts as 0, and a zero at the
 * one decimal shown has no sign. */
function difference(actual: number | null, planned: number | null, locale: Locale): string {
  const rounded = Math.round(((actual ?? 0) - (planned ?? 0)) * 10) / 10
  return `${rounded > 0 ? '+' : ''}${formatNumber(rounded === 0 ? 0 : rounded, locale, 1)}`
}

/**
 * The phone-sized replacement for the two donut legends (F040): one table with a row per segment,
 * its tickers beneath, and Actual %, Planned % and the difference side by side, so the two small
 * donuts above it can be read against each other. It makes no request of its own that the donuts
 * do not make (the same four `useFiiAllocation` queries, shared through the query cache), and its
 * color dots are the donuts' slice colors (`fiiAllocationSlices`).
 */
export function FiiAllocationComparison({ month }: FiiAllocationComparisonProps) {
  const { t } = useTranslation('fii')
  const { locale } = useLocale()
  const actualSegments = useFiiAllocation('ACTUAL', 'SEGMENT', month)
  const actualTickers = useFiiAllocation('ACTUAL', 'TICKER', month)
  const plannedSegments = useFiiAllocation('PLANNED', 'SEGMENT', month)
  const plannedTickers = useFiiAllocation('PLANNED', 'TICKER', month)
  const state = combineLoadState(
    useQueryState(actualSegments),
    useQueryState(actualTickers),
    useQueryState(plannedSegments),
    useQueryState(plannedTickers),
  )
  const palette = useFiiSegmentPalette(month)
  const showSkeleton = useDelayedFlag(state.loading || !palette.ready)

  if (state.loadError) {
    return <LoadFailedNotice message={state.loadError} onRetry={state.reload} />
  }
  if (showSkeleton) {
    return (
      <Box role="status" aria-label={t('comparison.loadingAria')}>
        <Box component="span" sx={VISUALLY_HIDDEN}>
          {t('comparison.loading')}
        </Box>
        <Skeleton variant="rounded" height={160} />
      </Box>
    )
  }
  if (
    !actualSegments.data ||
    !actualTickers.data ||
    !plannedSegments.data ||
    !plannedTickers.data ||
    !palette.ready
  ) {
    return null
  }

  const rows = buildComparisonRows(
    buildSegmentSlices(actualSegments.data, actualTickers.data, palette.colorOf),
    buildSegmentSlices(plannedSegments.data, plannedTickers.data, palette.colorOf),
  )
  if (rows.length === 0) {
    return (
      <Typography color="text.secondary" sx={fadeInSx}>
        {t('comparison.empty')}
      </Typography>
    )
  }

  return (
    <Table size="small" aria-label={t('comparison.aria')} sx={fadeInSx}>
      <TableHead>
        <TableRow>
          <TableCell>{t('comparison.segmentColumn')}</TableCell>
          <TableCell align="right">{t('comparison.actualColumn')}</TableCell>
          <TableCell align="right">{t('comparison.plannedColumn')}</TableCell>
          <TableCell align="right">{t('comparison.differenceColumn')}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.key}>
            <TableCell
              component={row.isSegment ? 'th' : 'td'}
              scope={row.isSegment ? 'row' : undefined}
              sx={{
                ...WRAP_ANYWHERE,
                pl: row.isSegment ? 2 : 4,
                fontWeight: row.isSegment ? 700 : 400,
              }}
            >
              <Box
                component="span"
                aria-hidden="true"
                sx={{
                  display: 'inline-block',
                  width: 12,
                  height: 12,
                  borderRadius: '50%',
                  bgcolor: row.color,
                  mr: 1,
                }}
              />
              {row.label}
            </TableCell>
            <TableCell align="right">{percent(row.actual, locale)}</TableCell>
            <TableCell align="right">{percent(row.planned, locale)}</TableCell>
            <TableCell align="right">{difference(row.actual, row.planned, locale)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
