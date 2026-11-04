import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  Chip,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Link as MuiLink,
  Switch,
  Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import DeleteIcon from '@mui/icons-material/Delete'
import LockIcon from '@mui/icons-material/Lock'
import type { Account } from '../../api/accounts/accounts'
import {
  useAccounts,
  useCloseAccount,
  useDeleteAccount,
  useEditAccount,
} from '../../api/accounts/accountsQueries'
import { useInstitutions } from '../../api/institutions/institutionsQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useAction } from '../../hooks/useAction'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useInlineEdit } from '../../hooks/useInlineEdit'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'
import { accountTypeLabel } from './accountTypes'
import { InstitutionSelect } from '../institutions/InstitutionSelect'
import { AccountCreateForm } from './AccountCreateForm'
import { isAccountEditValid } from './accountEdit'
import { AccountEditFields, AccountNameField } from './AccountEditFields'

/**
 * Account list/management screen (F003 spec): table with name, institution, type, running
 * balance; a toggle to show/hide closed accounts (default: hide, PRD S5.4); inline rename of
 * name/institution (the only editable fields - type and opening balance/date are immutable once
 * an account exists, so they render as plain text, never an input, in this table); an add-account
 * form; a close action gated behind a non-reversible confirmation dialog; and a delete action,
 * allowed by the backend only for an account with no history (ADR 0017).
 *
 * Responsive (F021): the header's Add button opens the create form in a `ResponsiveDialog` at
 * every size (there is no form panel below the table). The rename stays inline in the row on
 * tablet/desktop; on mobile the card's Edit opens the same fields in a full-screen dialog.
 */
export function AccountsPage() {
  const { t } = useTranslation(['accounts', 'common'])
  const { locale } = useLocale()
  const [includeClosed, setIncludeClosed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isMobile = useIsMobile()
  const accountsQuery = useAccounts(includeClosed)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const institutionsQuery = useInstitutions()
  const institutions = institutionsQuery.data
  const institutionsState = useQueryState(institutionsQuery, setError)
  const editMutation = useEditAccount()
  const closeMutation = useCloseAccount()
  const deleteMutation = useDeleteAccount()
  const institutionName = useMemo(
    () => nameLookup(institutions ?? [], (i) => translateSeedName('institutions', i.name, t)),
    [institutions, t],
  )

  const edit = useInlineEdit<{ name: string; institutionId: string | undefined }>({
    name: '',
    institutionId: undefined,
  })
  const { editingId, cancel: cancelEdit } = edit
  const editingName = edit.draft.name
  const editingInstitutionId = edit.draft.institutionId
  const saveAction = useAction(setError)
  const [addDialogOpen, setAddDialogOpen] = useState(false)

  const [closeTarget, setCloseTarget] = useState<Account | null>(null)
  const closeAction = useAction(setError)
  const [deleteTarget, setDeleteTarget] = useState<Account | null>(null)
  const deleteAction = useAction(setError)

  function startEdit(account: Account) {
    edit.start(account.id, { name: account.name, institutionId: account.institutionId })
  }

  function closeAddDialog() {
    setAddDialogOpen(false)
    setError(null)
  }

  async function saveEdit(id: string) {
    if (!isAccountEditValid(editingName, editingInstitutionId) || !editingInstitutionId) return
    const saved = await saveAction.run(() =>
      editMutation.mutateAsync({
        id,
        name: editingName.trim(),
        institutionId: editingInstitutionId,
      }),
    )
    if (saved) cancelEdit()
  }

  async function confirmClose() {
    if (!closeTarget) return
    if (await closeAction.run(() => closeMutation.mutateAsync(closeTarget.id))) {
      setCloseTarget(null)
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    await deleteAction.run(() => deleteMutation.mutateAsync(deleteTarget.id))
    setDeleteTarget(null)
  }

  // One load state for the table plus the institutions behind its Institution column: rows show
  // only once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(institutionsState, accountsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  // On mobile the edit happens in a dialog opened from the card, so the card stays plain text.
  const editDialogOpen = isMobile && editingId !== null

  const columns: ResponsiveColumn<Account>[] = [
    {
      key: 'name',
      header: t('accounts:columns.name'),
      role: 'primary',
      render: (account) =>
        editingId === account.id && !isMobile ? (
          <AccountNameField
            value={editingName}
            onChange={(name) => edit.setDraft((d) => ({ ...d, name }))}
            autoFocus
            onKeyDown={(e) => {
              if (e.key === 'Enter') void saveEdit(account.id)
              if (e.key === 'Escape') cancelEdit()
            }}
          />
        ) : (
          <MuiLink component={RouterLink} to={`/accounts/${account.id}`} underline="hover">
            {account.name}
          </MuiLink>
        ),
    },
    {
      key: 'institution',
      header: t('accounts:columns.institution'),
      role: 'secondary',
      render: (account) =>
        editingId === account.id && !isMobile ? (
          <InstitutionSelect
            value={editingInstitutionId}
            onChange={(institutionId) => edit.setDraft((d) => ({ ...d, institutionId }))}
          />
        ) : (
          institutionName(account.institutionId)
        ),
    },
    {
      key: 'type',
      header: t('accounts:columns.type'),
      role: 'secondary',
      tabletPriority: 'low',
      render: (account) => accountTypeLabel(account.type, t),
    },
    {
      key: 'balance',
      header: t('accounts:columns.balance'),
      align: 'right',
      render: (account) => formatMoney(account.balance, locale),
    },
    {
      key: 'status',
      header: t('accounts:columns.status'),
      render: (account) =>
        account.closed ? (
          <Chip label={t('accounts:status.closed')} size="small" />
        ) : (
          <Chip label={t('accounts:status.open')} size="small" color="success" />
        ),
    },
  ]

  function rowActions(account: Account) {
    const editingInline = editingId === account.id && !isMobile
    return (
      <>
        <InlineEditActions
          editing={editingInline}
          onEdit={() => startEdit(account)}
          onSave={() => void saveEdit(account.id)}
          onCancel={cancelEdit}
          editLabel={t('accounts:editAction')}
        />
        {!editingInline && (
          <IconButton
            size="small"
            aria-label={t('accounts:closeAction')}
            disabled={account.closed}
            onClick={() => setCloseTarget(account)}
          >
            <LockIcon fontSize="small" />
          </IconButton>
        )}
        {!editingInline && (
          <IconButton
            size="small"
            aria-label={t('common:actions.delete')}
            onClick={() => setDeleteTarget(account)}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        )}
      </>
    )
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 1,
        }}
      >
        <Typography variant="h4" component="h1">
          {t('accounts:title')}
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          {t('accounts:addButton')}
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('accounts:description')}
      </Typography>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert
        message={addDialogOpen || editDialogOpen ? null : error}
        onDismiss={() => setError(null)}
      />

      <FormControlLabel
        control={
          <Switch checked={includeClosed} onChange={(e) => setIncludeClosed(e.target.checked)} />
        }
        label={t('accounts:showClosed')}
        sx={{ mb: 2 }}
      />

      <Box sx={{ mb: 3 }}>
        <ResponsiveTable
          aria-label={t('accounts:title')}
          columns={columns}
          rows={accounts}
          getRowKey={(account) => account.id}
          state={tableState}
          onRetry={retry}
          actions={rowActions}
          emptyMessage={t('accounts:emptyList')}
        />
      </Box>

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog} fullWidth maxWidth="sm">
        <DialogTitle>{t('accounts:addDialogTitle')}</DialogTitle>
        <AccountCreateForm
          dialog
          banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
          onError={setError}
          onCreated={() => setAddDialogOpen(false)}
          onCancel={closeAddDialog}
        />
      </ResponsiveDialog>

      <ResponsiveDialog open={editDialogOpen} onClose={saveAction.pending ? undefined : cancelEdit}>
        <DialogTitle>{t('accounts:editDialogTitle')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <AccountEditFields
                name={editingName}
                institutionId={editingInstitutionId}
                onNameChange={(name) => edit.setDraft((d) => ({ ...d, name }))}
                onInstitutionChange={(institutionId) =>
                  edit.setDraft((d) => ({ ...d, institutionId }))
                }
                onSubmit={() => editingId && void saveEdit(editingId)}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={cancelEdit} disabled={saveAction.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={saveAction.pending || !isAccountEditValid(editingName, editingInstitutionId)}
            onClick={() => editingId && void saveEdit(editingId)}
          >
            {t('accounts:saveChanges')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ConfirmDialog
        open={closeTarget !== null}
        title={t('accounts:close.title', { name: closeTarget?.name })}
        body={t('accounts:close.body')}
        confirmLabel={t('accounts:close.confirmLabel')}
        loading={closeAction.pending}
        onConfirm={() => void confirmClose()}
        onCancel={() => setCloseTarget(null)}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        title={t('accounts:delete.title', { name: deleteTarget?.name })}
        body={t('accounts:delete.body')}
        confirmLabel={t('accounts:delete.confirmLabel')}
        loading={deleteAction.pending}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  )
}
