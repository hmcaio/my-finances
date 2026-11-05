import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { UseQueryResult } from '@tanstack/react-query'
import { ErrorAlert } from '../feedback/ErrorAlert'
import { useQueryState } from '../../hooks/queryState'
import { DataTableBody } from '../table/DataTableBody'
import { PageContainer } from './PageContainer'

interface NamedRow {
  id: string
  name: string
}

interface ReadOnlyNameListPageProps {
  title: string
  description: string
  emptyText: string
  query: Pick<
    UseQueryResult<NamedRow[]>,
    'isPending' | 'isError' | 'error' | 'errorUpdatedAt' | 'data' | 'refetch'
  >
  /** Turns a stored name into the text shown (the seed-name translation of that taxonomy). */
  displayName: (name: string) => string
}

/**
 * A settings page for a fixed, name-only taxonomy (payment methods, investment segments; F034/
 * ADR 0028): a title, a description and one read-only "Name" column. There is no add/rename/delete
 * affordance, because every row is migration-seeded content. Name is the only data column, so per
 * the responsive spec's 1-2-column rule this stays a plain table at every size (F021).
 *
 * The caller passes already-translated copy and the query, so each page keeps its own i18n
 * namespace and fixture.
 */
export function ReadOnlyNameListPage({
  title,
  description,
  emptyText,
  query,
  displayName,
}: ReadOnlyNameListPageProps) {
  const { t } = useTranslation('common')
  const [error, setError] = useState<string | null>(null)
  const state = useQueryState(query, setError)
  const rows = query.data

  // Clears the stale banner before retrying, so it doesn't outlive a successful retry.
  function retry() {
    setError(null)
    state.reload()
  }

  return (
    <PageContainer>
      <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
        {title}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {description}
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('actions.name')}</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={state} onRetry={retry} columns={1}>
              {rows?.length === 0 && (
                <TableRow>
                  <TableCell align="center">
                    <Typography color="text.secondary">{emptyText}</Typography>
                  </TableCell>
                </TableRow>
              )}
              {rows?.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>{displayName(row.name)}</TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>
    </PageContainer>
  )
}
