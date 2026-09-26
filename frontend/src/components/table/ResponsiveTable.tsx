import type { ReactNode } from 'react'
import {
  Box,
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { LoadState } from '../../hooks/queryState'
import { useBreakpointBand } from '../../hooks/useBreakpointBand'
import { fadeInSx } from '../feedback/fadeIn'
import { DataTableBody } from './DataTableBody'

export interface ResponsiveColumn<T> {
  /** Stable id (React key). */
  key: string
  /** Header cell content; also the label of the field in the generic card. */
  header: ReactNode
  /** The cell / card-field content. Inline-edit inputs go here, driven by the page's edit state. */
  render: (row: T) => ReactNode
  /**
   * How the generic card shows this column. `primary`: the card title. `secondary`: a muted line
   * under the title. `hideOnCard`: omitted from the card (still in the table). Unset: a labelled
   * "header: value" line.
   */
  role?: 'primary' | 'secondary' | 'hideOnCard'
  /** On tablet (`sm` to below `lg`) `low` columns are hidden to fit the table; default `high`. */
  tabletPriority?: 'high' | 'low'
  align?: 'left' | 'right' | 'center'
}

export interface ResponsiveTableProps<T> {
  columns: ResponsiveColumn<T>[]
  /** `undefined` until loaded, like query data. */
  rows: T[] | undefined
  getRowKey: (row: T) => string
  /** Same `LoadState` (or `combineLoadState`) `DataTableBody` takes: skeleton, failure + Retry. */
  state: LoadState
  onRetry?: () => void
  /** Row actions (edit/delete icon buttons, `InlineEditActions`): the last table column, and the
   * footer of the generic card, so both modes offer the same buttons. */
  actions?: (row: T) => ReactNode
  /** Header text of the actions column. */
  actionsLabel?: string
  emptyMessage?: string
  /** Replaces the generic card for screens it can't serve; it receives the rendered `actions`. */
  renderCard?: (row: T, actions: ReactNode) => ReactNode
  /** Placeholder rows while the first fetch is in flight. */
  skeletonRows?: number
  'aria-label'?: string
}

/**
 * Table on tablet and desktop, cards below `sm` (F021). Columns are declared once. Tablet keeps the
 * table and drops `tabletPriority: 'low'` columns; desktop shows all. A table with one or two data
 * columns stays a table at every size, since a card adds nothing. Loading, first-load failure and
 * the empty state reuse `DataTableBody`/`LoadFailedNotice` in both modes (on mobile the skeleton and
 * failure row are a one-column table), so first-load and refetch behaviour is identical.
 */
export function ResponsiveTable<T>({
  columns,
  rows,
  getRowKey,
  state,
  onRetry,
  actions,
  actionsLabel = 'Actions',
  emptyMessage = 'No items found.',
  renderCard,
  skeletonRows,
  'aria-label': ariaLabel,
}: ResponsiveTableProps<T>) {
  const band = useBreakpointBand()
  const asCards = band === 'mobile' && columns.length >= 3

  if (asCards && !state.loading && !state.loadError) {
    return (
      <Box sx={fadeInSx}>
        {rows?.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ py: 2 }}>
            {emptyMessage}
          </Typography>
        ) : (
          <Box
            component="ul"
            aria-label={ariaLabel}
            sx={{
              listStyle: 'none',
              m: 0,
              p: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: 1.5,
            }}
          >
            {rows?.map((row) => {
              const rowActions = actions?.(row)
              return (
                <li key={getRowKey(row)}>
                  {renderCard ? (
                    renderCard(row, rowActions)
                  ) : (
                    <GenericCard columns={columns} row={row} actions={rowActions} />
                  )}
                </li>
              )
            })}
          </Box>
        )}
      </Box>
    )
  }

  const visible = band === 'tablet' ? columns.filter((c) => c.tabletPriority !== 'low') : columns
  // Mobile card mode reaches here only while loading or failed: a single placeholder column.
  const bodyColumns = asCards ? 1 : visible.length + (actions ? 1 : 0)

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small" aria-label={ariaLabel}>
        {!asCards && (
          <TableHead>
            <TableRow>
              {visible.map((column) => (
                <TableCell key={column.key} align={column.align}>
                  {column.header}
                </TableCell>
              ))}
              {actions && <TableCell align="right">{actionsLabel}</TableCell>}
            </TableRow>
          </TableHead>
        )}
        <DataTableBody
          state={state}
          onRetry={onRetry}
          columns={bodyColumns}
          rows={skeletonRows}
          actionsColumn={!asCards && actions !== undefined}
        >
          {rows?.length === 0 && (
            <TableRow>
              <TableCell colSpan={bodyColumns} align="center">
                <Typography color="text.secondary">{emptyMessage}</Typography>
              </TableCell>
            </TableRow>
          )}
          {rows?.map((row) => (
            <TableRow key={getRowKey(row)}>
              {visible.map((column) => (
                <TableCell key={column.key} align={column.align}>
                  {column.render(row)}
                </TableCell>
              ))}
              {actions && <TableCell align="right">{actions(row)}</TableCell>}
            </TableRow>
          ))}
        </DataTableBody>
      </Table>
    </TableContainer>
  )
}

function GenericCard<T>({
  columns,
  row,
  actions,
}: {
  columns: ResponsiveColumn<T>[]
  row: T
  actions: ReactNode
}) {
  const primary = columns.filter((c) => c.role === 'primary')
  const secondary = columns.filter((c) => c.role === 'secondary')
  const fields = columns.filter((c) => c.role === undefined)
  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      {primary.map((c) => (
        <Typography
          key={c.key}
          variant="subtitle1"
          component="div"
          sx={{ overflowWrap: 'anywhere' }}
        >
          {c.render(row)}
        </Typography>
      ))}
      {secondary.map((c) => (
        <Typography
          key={c.key}
          variant="body2"
          color="text.secondary"
          component="div"
          sx={{ overflowWrap: 'anywhere' }}
        >
          {c.render(row)}
        </Typography>
      ))}
      {fields.length > 0 && (
        <Box
          component="dl"
          sx={{
            m: 0,
            mt: 1,
            display: 'grid',
            gridTemplateColumns: 'auto minmax(0, 1fr)',
            columnGap: 2,
            rowGap: 0.5,
          }}
        >
          {fields.map((c) => (
            <Box key={c.key} sx={{ display: 'contents' }}>
              <Typography component="dt" variant="body2" color="text.secondary">
                {c.header}
              </Typography>
              <Typography
                component="dd"
                variant="body2"
                sx={{ m: 0, textAlign: 'right', overflowWrap: 'anywhere' }}
              >
                {c.render(row)}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
      {actions && <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1 }}>{actions}</Box>}
    </Paper>
  )
}
