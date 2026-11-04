import { useTranslation } from 'react-i18next'
import { IconButton } from '@mui/material'
import StopCircleOutlinedIcon from '@mui/icons-material/StopCircleOutlined'
import type { Budget } from '../../api/budgets/budgets'
import { InlineEditActions } from '../../components/table/InlineEditActions'

interface Props {
  budget: Budget
  /** This row's cap is edited inline (never true on mobile): Save/Cancel replace Edit and Stop. */
  editingInline: boolean
  saving: boolean
  onStartEdit: (budget: Budget) => void
  onSave: (id: string) => void
  onCancel: () => void
  onStop: (budget: Budget) => void
}

/**
 * Edit / save / cancel plus Stop: the table's actions column and the card footer. A stopped budget's
 * edit reads "Resume budget" (a normal cap edit) and it has no Stop.
 */
export function BudgetRowActions({
  budget,
  editingInline,
  saving,
  onStartEdit,
  onSave,
  onCancel,
  onStop,
}: Props) {
  const { t } = useTranslation(['budgets', 'common'])
  return (
    <>
      <InlineEditActions
        editing={editingInline}
        onEdit={() => onStartEdit(budget)}
        onSave={() => onSave(budget.id)}
        onCancel={onCancel}
        editLabel={budget.stopped ? t('budgets:resumeBudgetAction') : t('budgets:editCapAction')}
        saveLabel={budget.stopped ? t('budgets:resumeWithCapAction') : t('budgets:saveCapAction')}
        saving={saving}
      />
      {!budget.stopped && !editingInline && (
        <IconButton
          size="small"
          aria-label={t('budgets:stopBudgetAria')}
          onClick={() => onStop(budget)}
        >
          <StopCircleOutlinedIcon fontSize="small" />
        </IconButton>
      )}
    </>
  )
}
