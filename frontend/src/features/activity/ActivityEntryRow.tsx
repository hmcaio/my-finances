import { useTranslation } from 'react-i18next'
import {
  Box,
  Chip,
  Collapse,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import type { AuditLogEntry, AuditOrigin } from '../../api/auditLog/auditLog'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useLocale } from '../../providers/locale/useLocale'
import { formatDate } from '../../i18n/format'
import { actionLabel, entityTypeLabel, originLabel } from './activityEnumLabels'
import { formatDiffValue } from './activityDiff'
import { fieldLabel } from './fieldLabels'
import { WRAP_ANYWHERE } from '../../components/layout/layoutSx'

function originColor(origin: AuditOrigin): 'primary' | 'default' {
  return origin === 'USER' ? 'primary' : 'default'
}

function DiffTable({ entry }: { entry: AuditLogEntry }) {
  const { t } = useTranslation('activity')
  const { locale } = useLocale()
  const fields = Object.keys(entry.changes)
  if (fields.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>
        {t('noFieldChanges')}
      </Typography>
    )
  }
  return (
    <Table
      size="small"
      aria-label={t('changesForAria', { entityType: entityTypeLabel(entry.entityType, t) })}
    >
      <TableHead>
        <TableRow>
          <TableCell>{t('diffTable.field')}</TableCell>
          <TableCell>{t('diffTable.from')}</TableCell>
          <TableCell>{t('diffTable.to')}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {fields.map((field) => (
          <TableRow key={field}>
            <TableCell>{fieldLabel(entry.entityType, field, t)}</TableCell>
            <TableCell sx={WRAP_ANYWHERE}>
              {formatDiffValue(entry.changes[field].from, locale, t)}
            </TableCell>
            <TableCell sx={WRAP_ANYWHERE}>
              {formatDiffValue(entry.changes[field].to, locale, t)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

interface ActivityEntryRowProps {
  entry: AuditLogEntry
  expanded: boolean
  onToggle: () => void
}

/**
 * One activity entry, as a table row (desktop/tablet, to render inside a `TableBody`) or a card
 * (mobile) with an expand toggle that reveals its field-by-field diff.
 */
export function ActivityEntryRow({ entry, expanded, onToggle }: ActivityEntryRowProps) {
  const { t } = useTranslation('activity')
  const { locale } = useLocale()
  const isMobile = useIsMobile()
  const time = formatDate(new Date(entry.occurredAt), locale, {
    hour: '2-digit',
    minute: '2-digit',
  })
  const expandButton = (
    <IconButton
      size="small"
      aria-label={expanded ? t('collapseDetails') : t('expandDetails')}
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
            <Typography variant="subtitle2" sx={WRAP_ANYWHERE}>
              {entry.entityLabel ?? entityTypeLabel(entry.entityType, t)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {time} · {entityTypeLabel(entry.entityType, t)} · {actionLabel(entry.action, t)}
            </Typography>
            <Chip
              size="small"
              label={originLabel(entry.origin, t)}
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
        <TableCell>{actionLabel(entry.action, t)}</TableCell>
        <TableCell>{entityTypeLabel(entry.entityType, t)}</TableCell>
        <TableCell sx={WRAP_ANYWHERE}>
          {entry.entityLabel ?? <Typography color="text.secondary">—</Typography>}
        </TableCell>
        <TableCell>
          <Chip
            size="small"
            label={originLabel(entry.origin, t)}
            color={originColor(entry.origin)}
          />
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
