import { useTranslation } from 'react-i18next'
import { IconButton } from '@mui/material'
import PauseIcon from '@mui/icons-material/Pause'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import type { RecurringTemplate } from '../../api/recurringTemplates/recurringTemplates'
import { InlineEditActions } from '../../components/table/InlineEditActions'

interface RowActionsProps {
  template: RecurringTemplate
  /** This row is in inline edit (never true on mobile): Save/Cancel replace Edit and the toggle. */
  editingInline: boolean
  saving: boolean
  toggling: boolean
  onStartEdit: (template: RecurringTemplate) => void
  onSave: (id: string) => void
  onCancel: () => void
  onToggle: (template: RecurringTemplate) => void
}

/** Edit / save / cancel plus the stop-reactivate toggle: the table's actions column and the card footer. */
export function RecurringTemplateRowActions({
  template,
  editingInline,
  saving,
  toggling,
  onStartEdit,
  onSave,
  onCancel,
  onToggle,
}: RowActionsProps) {
  const { t } = useTranslation(['recurringTemplates', 'common'])
  return (
    <>
      <InlineEditActions
        editing={editingInline}
        onEdit={() => onStartEdit(template)}
        onSave={() => onSave(template.id)}
        onCancel={onCancel}
        editLabel={t('recurringTemplates:editAmountDayAction')}
        saveLabel={t('recurringTemplates:saveCapAction')}
        saving={saving}
      />
      {!editingInline && (
        <IconButton
          size="small"
          aria-label={
            template.active
              ? t('recurringTemplates:stopAria')
              : t('recurringTemplates:reactivateAria')
          }
          disabled={toggling}
          onClick={() => onToggle(template)}
        >
          {template.active ? <PauseIcon fontSize="small" /> : <PlayArrowIcon fontSize="small" />}
        </IconButton>
      )}
    </>
  )
}
