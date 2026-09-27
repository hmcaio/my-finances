import { Fragment, useId, useState, type ReactNode } from 'react'
import {
  Box,
  Collapse,
  IconButton,
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  type SxProps,
  type Theme,
} from '@mui/material'
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp'
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
  /** On tablet (`sm` to below `lg`) `low` columns are hidden to fit the table (a chevron on each
   * row expands them as label/value pairs); default `high`. */
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
  /** For a list inside a section that already has an outline (the account detail page): the table
   * drops its own outlined frame so borders don't nest. Cards keep theirs. */
  embedded?: boolean
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
  embedded = false,
  'aria-label': ariaLabel,
}: ResponsiveTableProps<T>) {
  const band = useBreakpointBand()
  const idPrefix = useId()
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set())
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
  const hidden = band === 'tablet' ? columns.filter((c) => c.tabletPriority === 'low') : []
  const expandable = hidden.length > 0
  // Mobile card mode reaches here only while loading or failed: a single placeholder column.
  const bodyColumns = asCards ? 1 : visible.length + (actions ? 1 : 0) + (expandable ? 1 : 0)

  function toggle(key: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (!next.delete(key)) next.add(key)
      return next
    })
  }

  return (
    <TableContainer
      component={embedded ? 'div' : Paper}
      {...(embedded ? {} : { variant: 'outlined' as const })}
    >
      <Table size="small" aria-label={ariaLabel}>
        {!asCards && (
          <TableHead>
            <TableRow>
              {expandable && (
                <TableCell padding="checkbox">
                  <Box component="span" sx={VISUALLY_HIDDEN}>
                    Details
                  </Box>
                </TableCell>
              )}
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
          {rows?.map((row) => {
            const key = getRowKey(row)
            const open = expandable && expanded.has(key)
            const detailId = `${idPrefix}-details-${key}`
            return (
              <Fragment key={key}>
                <TableRow>
                  {expandable && (
                    <TableCell padding="checkbox">
                      <IconButton
                        size="small"
                        aria-label={open ? 'Hide details' : 'Show details'}
                        aria-expanded={open}
                        aria-controls={detailId}
                        onClick={() => toggle(key)}
                      >
                        {open ? (
                          <KeyboardArrowUpIcon fontSize="small" />
                        ) : (
                          <KeyboardArrowDownIcon fontSize="small" />
                        )}
                      </IconButton>
                    </TableCell>
                  )}
                  {visible.map((column) => (
                    <TableCell key={column.key} align={column.align}>
                      {column.render(row)}
                    </TableCell>
                  ))}
                  {actions && <TableCell align="right">{actions(row)}</TableCell>}
                </TableRow>
                {expandable && (
                  <TableRow id={detailId}>
                    {/* Padding lives inside the Collapse so a closed row has no height or border. */}
                    <TableCell
                      colSpan={bodyColumns}
                      sx={{ p: 0, borderBottom: open ? undefined : 'none' }}
                    >
                      <Collapse in={open} timeout="auto" unmountOnExit>
                        <Box sx={{ px: 2, py: 1.5 }}>
                          <FieldList columns={hidden} row={row} />
                        </Box>
                      </Collapse>
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            )
          })}
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
      {fields.length > 0 && <FieldList columns={fields} row={row} sx={{ mt: 1 }} />}
      {actions && <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1 }}>{actions}</Box>}
    </Paper>
  )
}

/** Screen-reader-only text (MUI's `visuallyHidden` lives in `@mui/utils`, not a direct dependency). */
const VISUALLY_HIDDEN = {
  position: 'absolute',
  // Pixel strings: in `sx`, a bare `1` means 100%, which made the clipped span as wide as the page.
  width: '1px',
  height: '1px',
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

/** "Header: value" pairs: the generic card's labelled fields and a tablet row's expanded details. */
function FieldList<T>({
  columns,
  row,
  sx,
}: {
  columns: ResponsiveColumn<T>[]
  row: T
  sx?: SxProps<Theme>
}) {
  return (
    <Box
      component="dl"
      sx={[
        {
          m: 0,
          display: 'grid',
          gridTemplateColumns: 'auto minmax(0, 1fr)',
          columnGap: 2,
          rowGap: 0.5,
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      {columns.map((c) => (
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
  )
}
