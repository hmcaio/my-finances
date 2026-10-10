import { useMemo, useState } from 'react'
import {
  Box,
  Chip,
  Collapse,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import type {
  AuditAction,
  AuditEntityType,
  AuditLogEntry,
  AuditLogFilter,
  AuditOrigin,
} from '../../api/auditLog/auditLog'
import { useAuditLog } from '../../api/auditLog/auditLogQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { PaginationControls } from '../../components/table/PaginationControls'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useQueryState } from '../../hooks/queryState'
import { fieldLabel } from './fieldLabels'
import { groupEntriesByLocalDay } from './groupByDay'

const PAGE_SIZE = 20

const ENTITY_TYPES: AuditEntityType[] = [
  'TRANSACTION',
  'TRANSFER',
  'ACCOUNT',
  'CATEGORY',
  'PAYMENT_METHOD',
  'INSTITUTION',
  'BUDGET',
  'RECURRING_TEMPLATE',
  'INVESTMENT_CATEGORY',
  'INVESTMENT_SUBCATEGORY',
  'INVESTMENT_PRODUCT',
  'INVESTMENT_HOLDING',
  'INVESTMENT_SNAPSHOT',
  'INVESTMENT_SEGMENT',
  'ALLOCATION_PLAN',
  'VEHICLE',
]

const ACTIONS: AuditAction[] = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'CLOSE',
  'REOPEN',
  'STOPPED',
  'GENERATED',
]

const ORIGINS: AuditOrigin[] = ['USER', 'SYSTEM']

/** Title-cases an enum constant for display, e.g. `PAYMENT_METHOD` -> "Payment method". */
function enumLabel(value: string): string {
  const words = value.toLowerCase().split('_')
  return words.map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w)).join(' ')
}

/** A reference field's value once the backend has resolved its label (see `AuditReferenceLabels`). */
interface ReferenceValue {
  id: string
  label: string | null
}

function isReferenceValue(value: unknown): value is ReferenceValue {
  return (
    typeof value === 'object' &&
    value !== null &&
    'id' in value &&
    'label' in value &&
    typeof (value as { id: unknown }).id === 'string'
  )
}

/**
 * Renders a diff value: `null`/`undefined` as a dash, booleans as Yes/No, a resolved reference as
 * "Label (id)" (falling back to the bare id when the backend had no label for it, e.g. an older
 * audit row predating label resolution, or a deleted referenced entity), everything else as-is.
 */
function formatDiffValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2)
  if (isReferenceValue(value)) return value.label ? `${value.label} (${value.id})` : value.id
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function originColor(origin: AuditOrigin): 'primary' | 'default' {
  return origin === 'USER' ? 'primary' : 'default'
}

interface EntryRowProps {
  entry: AuditLogEntry
  expanded: boolean
  onToggle: () => void
}

function DiffTable({ entry }: { entry: AuditLogEntry }) {
  const fields = Object.keys(entry.changes)
  if (fields.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>
        No field changes recorded.
      </Typography>
    )
  }
  return (
    <Table size="small" aria-label={`Changes for ${entry.entityType}`}>
      <TableHead>
        <TableRow>
          <TableCell>Field</TableCell>
          <TableCell>From</TableCell>
          <TableCell>To</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {fields.map((field) => (
          <TableRow key={field}>
            <TableCell>{fieldLabel(entry.entityType, field)}</TableCell>
            <TableCell sx={{ overflowWrap: 'anywhere' }}>
              {formatDiffValue(entry.changes[field].from)}
            </TableCell>
            <TableCell sx={{ overflowWrap: 'anywhere' }}>
              {formatDiffValue(entry.changes[field].to)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

/** One activity entry, as a table row (desktop/tablet) or a card (mobile) with an expand toggle. */
function EntryRow({ entry, expanded, onToggle }: EntryRowProps) {
  const isMobile = useIsMobile()
  const time = new Date(entry.occurredAt).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  })
  const expandButton = (
    <IconButton
      size="small"
      aria-label={expanded ? 'Collapse details' : 'Expand details'}
      aria-expanded={expanded}
      // The desktop row below also toggles on click; without this, a click on the button bubbles
      // up to the row's own handler and the two toggles cancel each other out.
      onClick={(e) => {
        e.stopPropagation()
        onToggle()
      }}
      sx={{ minWidth: 44, minHeight: 44 }}
    >
      {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
    </IconButton>
  )

  if (isMobile) {
    return (
      <Paper variant="outlined" sx={{ p: 1.5, mb: 1 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
              {entry.entityLabel ?? enumLabel(entry.entityType)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {time} · {enumLabel(entry.entityType)} · {enumLabel(entry.action)}
            </Typography>
            <Chip
              size="small"
              label={enumLabel(entry.origin)}
              color={originColor(entry.origin)}
              sx={{ mt: 0.5 }}
            />
          </Box>
          {expandButton}
        </Box>
        <Collapse in={expanded} unmountOnExit>
          <Box sx={{ mt: 1 }}>
            <DiffTable entry={entry} />
          </Box>
        </Collapse>
      </Paper>
    )
  }

  return (
    <>
      <TableRow hover onClick={onToggle} sx={{ cursor: 'pointer' }}>
        <TableCell>{time}</TableCell>
        <TableCell>{enumLabel(entry.action)}</TableCell>
        <TableCell>{enumLabel(entry.entityType)}</TableCell>
        <TableCell sx={{ overflowWrap: 'anywhere' }}>
          {entry.entityLabel ?? <Typography color="text.secondary">—</Typography>}
        </TableCell>
        <TableCell>
          <Chip size="small" label={enumLabel(entry.origin)} color={originColor(entry.origin)} />
        </TableCell>
        <TableCell align="right">{expandButton}</TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={6} sx={{ p: 0, border: expanded ? undefined : 'none' }}>
          <Collapse in={expanded} unmountOnExit>
            <Box sx={{ p: 2 }}>
              <DiffTable entry={entry} />
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  )
}

/**
 * Read-only Activity page (F025 spec, PRD S6.12): every logged change, newest first, grouped by
 * day in the viewer's local time zone, filterable by date range/entity type/action/origin. Each
 * row expands to its field-by-field before/after diff. There is nothing to create/edit/delete
 * here - the log can't be written to from the app.
 */
export function ActivityPage() {
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

  const groups = useMemo(() => groupEntriesByLocalDay(entries ?? []), [entries])

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

  const activeFilterCount = Object.values(filters).filter(Boolean).length

  function retry() {
    setError(null)
    state.reload()
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
        Activity
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Every committed change, newest first - what changed, when, and by what (a direct action or a
        system-generated one).
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ResponsiveFilterBar activeCount={activeFilterCount} onClear={clearFilters}>
        <TextField
          label="From"
          type="date"
          size="small"
          value={filters.from?.slice(0, 10) ?? ''}
          onChange={(e) =>
            updateFilter({ from: e.target.value ? startOfDayIso(e.target.value) : undefined })
          }
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="To"
          type="date"
          size="small"
          value={filters.to?.slice(0, 10) ?? ''}
          onChange={(e) =>
            updateFilter({ to: e.target.value ? endOfDayIso(e.target.value) : undefined })
          }
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <Select
          size="small"
          displayEmpty
          value={filters.entityType ?? ''}
          onChange={(e) =>
            updateFilter({ entityType: (e.target.value || undefined) as AuditEntityType })
          }
          aria-label="Entity type filter"
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All entity types</MenuItem>
          {ENTITY_TYPES.map((t) => (
            <MenuItem key={t} value={t}>
              {enumLabel(t)}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          displayEmpty
          value={filters.action ?? ''}
          onChange={(e) => updateFilter({ action: (e.target.value || undefined) as AuditAction })}
          aria-label="Action filter"
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">All actions</MenuItem>
          {ACTIONS.map((a) => (
            <MenuItem key={a} value={a}>
              {enumLabel(a)}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          displayEmpty
          value={filters.origin ?? ''}
          onChange={(e) => updateFilter({ origin: (e.target.value || undefined) as AuditOrigin })}
          aria-label="Origin filter"
          sx={{ minWidth: 120 }}
        >
          <MenuItem value="">All origins</MenuItem>
          {ORIGINS.map((o) => (
            <MenuItem key={o} value={o}>
              {enumLabel(o)}
            </MenuItem>
          ))}
        </Select>
      </ResponsiveFilterBar>

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
          No activity found.
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
                  <EntryRow
                    key={entry.id}
                    entry={entry}
                    expanded={expandedId === entry.id}
                    onToggle={() => toggle(entry.id)}
                  />
                ))
              ) : (
                <Table size="small" aria-label={`Activity on ${group.label}`}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Time</TableCell>
                      <TableCell>Action</TableCell>
                      <TableCell>Entity type</TableCell>
                      <TableCell>Label</TableCell>
                      <TableCell>Origin</TableCell>
                      <TableCell align="right" />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {group.entries.map((entry) => (
                      <EntryRow
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
    </Box>
  )
}

/** Start of `dateStr` (`YYYY-MM-DD`) in the viewer's local time zone, as a UTC instant string. */
function startOfDayIso(dateStr: string): string {
  return new Date(`${dateStr}T00:00:00`).toISOString()
}

/** End of `dateStr` (`YYYY-MM-DD`) in the viewer's local time zone, as a UTC instant string. */
function endOfDayIso(dateStr: string): string {
  return new Date(`${dateStr}T23:59:59.999`).toISOString()
}
