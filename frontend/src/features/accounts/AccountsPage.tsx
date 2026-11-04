import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  DialogActions,
  DialogTitle,
  FormControlLabel,
  Switch,
  Typography,
} from '@mui/material'
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
import { ResponsiveTable } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useAction } from '../../hooks/useAction'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useInlineEdit } from '../../hooks/useInlineEdit'
import { useLocale } from '../../hooks/locale/useLocale'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'
import { AccountCreateForm } from './AccountCreateForm'
import { isAccountEditValid } from './accountEdit'
import { buildAccountColumns } from './accountColumns'
import { AccountEditFields } from './AccountEditFields'
import { AccountRowActions } from './AccountRowActions'
import { PageContainer } from '../../components/layout/PageContainer'
import { PageHeader } from '../../components/layout/PageHeader'
import { FormDialogContent } from '../../components/feedback/FormDialogContent'

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

  const columns = buildAccountColumns({
    t,
    locale,
    institutionName,
    inlineEdit:
      editingId !== null && !isMobile
        ? {
            rowId: editingId,
            name: editingName,
            institutionId: editingInstitutionId,
            onNameChange: (name) => edit.setDraft((d) => ({ ...d, name })),
            onInstitutionChange: (institutionId) => edit.setDraft((d) => ({ ...d, institutionId })),
            onSave: (id) => void saveEdit(id),
            onCancel: cancelEdit,
          }
        : null,
  })

  function rowActions(account: Account) {
    return (
      <AccountRowActions
        account={account}
        editingInline={editingId === account.id && !isMobile}
        onStartEdit={startEdit}
        onSave={(id) => void saveEdit(id)}
        onCancel={cancelEdit}
        onClose={setCloseTarget}
        onDelete={setDeleteTarget}
      />
    )
  }

  return (
    <PageContainer>
      <PageHeader title={t('accounts:title')}>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          {t('accounts:addButton')}
        </Button>
      </PageHeader>
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
        <FormDialogContent>
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
        </FormDialogContent>
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
    </PageContainer>
  )
}
