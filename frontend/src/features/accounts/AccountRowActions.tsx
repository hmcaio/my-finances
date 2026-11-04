import { useTranslation } from 'react-i18next'
import { IconButton } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import LockIcon from '@mui/icons-material/Lock'
import type { Account } from '../../api/accounts/accounts'
import { InlineEditActions } from '../../components/table/InlineEditActions'

interface Props {
  account: Account
  /** This row is renamed inline (never true on mobile): Save/Cancel replace Edit, Close and Delete. */
  editingInline: boolean
  onStartEdit: (account: Account) => void
  onSave: (id: string) => void
  onCancel: () => void
  onClose: (account: Account) => void
  onDelete: (account: Account) => void
}

/** Edit / save / cancel plus Close and Delete: the table's actions column and the card footer. */
export function AccountRowActions({
  account,
  editingInline,
  onStartEdit,
  onSave,
  onCancel,
  onClose,
  onDelete,
}: Props) {
  const { t } = useTranslation(['accounts', 'common'])
  return (
    <>
      <InlineEditActions
        editing={editingInline}
        onEdit={() => onStartEdit(account)}
        onSave={() => onSave(account.id)}
        onCancel={onCancel}
        editLabel={t('accounts:editAction')}
      />
      {!editingInline && (
        <IconButton
          size="small"
          aria-label={t('accounts:closeAction')}
          disabled={account.closed}
          onClick={() => onClose(account)}
        >
          <LockIcon fontSize="small" />
        </IconButton>
      )}
      {!editingInline && (
        <IconButton
          size="small"
          aria-label={t('common:actions.delete')}
          onClick={() => onDelete(account)}
        >
          <DeleteIcon fontSize="small" />
        </IconButton>
      )}
    </>
  )
}
