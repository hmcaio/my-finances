import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { AuditLogFilter } from '../../api/auditLog/auditLog'
import { useAuditLog } from '../../api/auditLog/auditLogQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { PaginationControls } from '../../components/table/PaginationControls'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useQueryState } from '../../hooks/queryState'
import { useLocale } from '../../providers/locale/useLocale'
import { ActivityEntryRow } from './ActivityEntryRow'
import { ActivityFilterBar } from './ActivityFilterBar'
import { groupEntriesByLocalDay } from './groupByDay'
import { PageContainer } from '../../components/layout/PageContainer'

const PAGE_SIZE = 20

/**
 * Read-only Activity page (F025 spec, PRD S6.12): every logged change, newest first, grouped by
 * day in the viewer's local time zone, filterable by date range/entity type/action/origin. Each
 * row expands to its field-by-field before/after diff. There is nothing to create/edit/delete
 * here - the log can't be written to from the app.
 */
export function ActivityPage() {
  const { t } = useTranslation('activity')
  const { locale } = useLocale()
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<AuditLogFilter>({})
  const [error, setError] = useState<string | null>(null)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const isMobile = useIsMobile()

  const query = useAuditLog(filters, page, PAGE_SIZE)
  const entries = query.data?.content
  const pageInfo = query.data
    ? { number: query.data.page.number, totalPages: query.data.page.totalPages }
    : null
  const state = useQueryState(query, setError)
  const showSkeleton = useDelayedFlag(state.loading)

  const groups = useMemo(() => groupEntriesByLocalDay(entries ?? [], locale), [entries, locale])

  function updateFilter(patch: Partial<AuditLogFilter>) {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  function clearFilters() {
    setFilters({})
    setPage(0)
  }

  function toggle(id: string) {
    setExpandedId((current) => (current === id ? null : id))
  }

  function retry() {
    setError(null)
    state.reload()
  }

  return (
    <PageContainer>
      <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
        {t('title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('subtitle')}
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ActivityFilterBar filters={filters} onChange={updateFilter} onClear={clearFilters} />

      {state.loadError ? (
        <LoadFailedNotice message={state.loadError} onRetry={retry} />
      ) : showSkeleton ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {[0, 1, 2, 3].map((row) => (
            <Skeleton key={row} variant="rounded" height={56} />
          ))}
        </Box>
      ) : entries && entries.length === 0 ? (
        <Typography color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
          {t('noActivity')}
        </Typography>
      ) : (
        <Box sx={fadeInSx}>
          {groups.map((group) => (
            <Box key={group.dateKey} sx={{ mb: 3 }}>
              <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 600 }}>
                {group.label}
              </Typography>
              {isMobile ? (
                group.entries.map((entry) => (
                  <ActivityEntryRow
                    key={entry.id}
                    entry={entry}
                    expanded={expandedId === entry.id}
                    onToggle={() => toggle(entry.id)}
                  />
                ))
              ) : (
                <Table size="small" aria-label={t('activityOnAria', { date: group.label })}>
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('columns.time')}</TableCell>
                      <TableCell>{t('columns.action')}</TableCell>
                      <TableCell>{t('columns.entityType')}</TableCell>
                      <TableCell>{t('columns.label')}</TableCell>
                      <TableCell>{t('columns.origin')}</TableCell>
                      <TableCell align="right" />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {group.entries.map((entry) => (
                      <ActivityEntryRow
                        key={entry.id}
                        entry={entry}
                        expanded={expandedId === entry.id}
                        onToggle={() => toggle(entry.id)}
                      />
                    ))}
                  </TableBody>
                </Table>
              )}
            </Box>
          ))}
        </Box>
      )}

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} sx={{ mb: 3 }} />
    </PageContainer>
  )
}
