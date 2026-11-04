import { useTranslation } from 'react-i18next'
import { MenuItem, Select, TextField } from '@mui/material'
import type {
  AuditAction,
  AuditEntityType,
  AuditLogFilter,
  AuditOrigin,
} from '../../api/auditLog/auditLog'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { actionLabel, entityTypeLabel, originLabel } from './activityEnumLabels'
import { ACTIONS, ENTITY_TYPES, ORIGINS, endOfDayIso, startOfDayIso } from './activityFilters'

interface ActivityFilterBarProps {
  filters: AuditLogFilter
  /** Merges `patch` into the filters (the page also resets to the first page). */
  onChange: (patch: Partial<AuditLogFilter>) => void
  onClear: () => void
}

/** The Activity page's date range / entity type / action / origin filters. */
export function ActivityFilterBar({ filters, onChange, onClear }: ActivityFilterBarProps) {
  const { t } = useTranslation('activity')
  const activeFilterCount = Object.values(filters).filter(Boolean).length
  return (
    <ResponsiveFilterBar activeCount={activeFilterCount} onClear={onClear}>
      <TextField
        label={t('filters.from')}
        type="date"
        size="small"
        value={filters.from?.slice(0, 10) ?? ''}
        onChange={(e) =>
          onChange({ from: e.target.value ? startOfDayIso(e.target.value) : undefined })
        }
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label={t('filters.to')}
        type="date"
        size="small"
        value={filters.to?.slice(0, 10) ?? ''}
        onChange={(e) => onChange({ to: e.target.value ? endOfDayIso(e.target.value) : undefined })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <Select
        size="small"
        displayEmpty
        value={filters.entityType ?? ''}
        onChange={(e) => onChange({ entityType: (e.target.value || undefined) as AuditEntityType })}
        aria-label={t('filters.entityTypeAria')}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="">{t('filters.allEntityTypes')}</MenuItem>
        {ENTITY_TYPES.map((entityType) => (
          <MenuItem key={entityType} value={entityType}>
            {entityTypeLabel(entityType, t)}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={filters.action ?? ''}
        onChange={(e) => onChange({ action: (e.target.value || undefined) as AuditAction })}
        aria-label={t('filters.actionAria')}
        sx={{ minWidth: 140 }}
      >
        <MenuItem value="">{t('filters.allActions')}</MenuItem>
        {ACTIONS.map((action) => (
          <MenuItem key={action} value={action}>
            {actionLabel(action, t)}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={filters.origin ?? ''}
        onChange={(e) => onChange({ origin: (e.target.value || undefined) as AuditOrigin })}
        aria-label={t('filters.originAria')}
        sx={{ minWidth: 120 }}
      >
        <MenuItem value="">{t('filters.allOrigins')}</MenuItem>
        {ORIGINS.map((origin) => (
          <MenuItem key={origin} value={origin}>
            {originLabel(origin, t)}
          </MenuItem>
        ))}
      </Select>
    </ResponsiveFilterBar>
  )
}
