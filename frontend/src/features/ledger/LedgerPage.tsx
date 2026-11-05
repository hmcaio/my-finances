import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { Alert, Box, Button, DialogTitle, Menu, MenuItem, Typography } from '@mui/material'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import type { LedgerEntry } from '../../api/ledger/ledger'
import { useLedger } from '../../api/ledger/ledgerQueries'
import { useDeleteTransaction, useTransaction } from '../../api/transactions/transactionsQueries'
import { useDeleteTransfer, useTransfer } from '../../api/transfers/transfersQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useAction } from '../../hooks/useAction'
import { TransactionFormDialog } from '../transactions/TransactionFormDialog'
import { TransferForm } from '../transfers/TransferForm'
import { LedgerFilters } from './LedgerFilters'
import { LedgerSummary } from './LedgerSummary'
import { LedgerTable } from './LedgerTable'
import {
  isAllTime,
  parseLedgerView,
  serializeLedgerView,
  toLedgerFilter,
  type LedgerView,
} from './ledgerSearchParams'
import { useConsumeNavigationState } from './useConsumeNavigationState'
import { useFocusParam } from './useFocusParam'
import { useLedgerLookups } from './useLedgerLookups'
import { PageContainer } from '../../components/layout/PageContainer'
import { PageHeader } from '../../components/layout/PageHeader'

/** What a row's edit button points at; the full record is fetched by id before its dialog opens. */
interface EditTarget {
  kind: 'transaction' | 'transfer'
  id: string
}

/** The fuel category the Fuel page's "add fill-up" link passes in the navigation state, if any. */
const readPresetCategoryId = (state: unknown) =>
  (state as { presetCategoryId?: string } | null)?.presetCategoryId

/**
 * The Ledger (F037, PRD S6.16): transactions and transfers interleaved in one list, newest first,
 * scoped to a month (or a custom range / all time), with income/expense/net for the scope, a type
 * filter and accent-insensitive text search. Replaces the separate Transactions and Transfers
 * pages. The whole view (period, filters, search) lives in the URL query string so a link or
 * refresh restores it; the list loads whole, capped at 1,000 rows with a notice.
 *
 * <p>Create, edit and delete go through the existing transaction and transfer endpoints: a split
 * "Add" menu opens the transaction or the transfer dialog, and a row's edit button fetches the full
 * record by id (a ledger row carries only the list columns) and opens the matching dialog.
 * `?focus={transferId}` (the product page's "View confirmation" link) opens that transfer the same
 * way, whatever the current period; `location.state.presetCategoryId` (the Fuel page's "add
 * fill-up") opens the new-transaction dialog with that category chosen.
 */
export function LedgerPage() {
  const { t } = useTranslation(['ledger', 'transactions', 'transfers', 'common'])
  const [searchParams, setSearchParams] = useSearchParams()
  const view = useMemo(() => parseLedgerView(searchParams), [searchParams])
  const [error, setError] = useState<string | null>(null)

  const {
    lookups,
    state: lookupsState,
    accounts,
    categories,
    paymentMethods,
  } = useLedgerLookups(setError)
  const ledgerQuery = useLedger(toLedgerFilter(view))
  const ledger = ledgerQuery.data
  const ledgerState = useQueryState(ledgerQuery, setError)
  const tableState = combineLoadState(lookupsState, ledgerState)

  const deleteTransaction = useDeleteTransaction()
  const deleteTransfer = useDeleteTransfer()

  // --- view state in the URL --------------------------------------------------------------------
  function updateView(patch: Partial<LedgerView>, options?: { replace?: boolean }) {
    // `focus` is consumed on arrival (below), so it never survives a view change.
    setSearchParams(serializeLedgerView({ ...view, ...patch, focus: '' }), {
      replace: options?.replace ?? false,
    })
  }

  // --- dialogs ------------------------------------------------------------------------------------
  // The Fuel page's "add fill-up" arrives with the fuel category in the navigation state, consumed
  // once so the dialog opens straight into an add form for it (and a back-navigation does not).
  const presetCategoryId = useConsumeNavigationState(readPresetCategoryId)
  const [newTransaction, setNewTransaction] = useState<{ presetCategoryId?: string } | null>(() =>
    presetCategoryId ? { presetCategoryId } : null,
  )
  const [newTransfer, setNewTransfer] = useState(false)
  const [editTarget, setEditTarget] = useState<EditTarget | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<LedgerEntry | null>(null)
  const deleteAction = useAction(setError)
  const [addMenuAnchor, setAddMenuAnchor] = useState<HTMLElement | null>(null)

  // `?focus={transferId}` opens that transfer for editing, then drops only the `focus` key.
  useFocusParam(view.focus, (id) => setEditTarget({ kind: 'transfer', id }))

  // Edit: fetch the full record, then open its dialog. `useTransaction`/`useTransfer` are disabled
  // while their id is `null`.
  const transactionToEdit = useTransaction(
    editTarget?.kind === 'transaction' ? editTarget.id : null,
  )
  const transferToEdit = useTransfer(editTarget?.kind === 'transfer' ? editTarget.id : null)
  const editLoadFailed =
    editTarget !== null &&
    (editTarget.kind === 'transaction' ? transactionToEdit.isError : transferToEdit.isError)
  const editLoadError = editLoadFailed ? t('ledger:editNotFound') : null
  const editingTransaction = editTarget?.kind === 'transaction' ? transactionToEdit.data : undefined
  const editingTransfer = editTarget?.kind === 'transfer' ? transferToEdit.data : undefined
  const dialogOpen =
    newTransaction !== null ||
    newTransfer ||
    editingTransaction !== undefined ||
    editingTransfer !== undefined

  function closeDialogs() {
    setNewTransaction(null)
    setNewTransfer(false)
    setEditTarget(null)
    setError(null)
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    const deleted = await deleteAction.run(() =>
      deleteTarget.type === 'TRANSFER'
        ? deleteTransfer.mutateAsync(deleteTarget.id)
        : deleteTransaction.mutateAsync(deleteTarget.id),
    )
    if (deleted) setDeleteTarget(null)
  }

  function retry() {
    setError(null)
    tableState.reload()
  }

  const searching = view.q.trim() !== ''
  const showSearchAllTime =
    searching &&
    !isAllTime(view) &&
    !tableState.loading &&
    !tableState.loadError &&
    ledger?.entries.length === 0
  const bannerError = dialogOpen ? null : (error ?? editLoadError)

  return (
    <PageContainer>
      <PageHeader title={t('ledger:title')}>
        <Button
          variant="contained"
          endIcon={<ArrowDropDownIcon />}
          aria-haspopup="menu"
          aria-expanded={addMenuAnchor !== null}
          onClick={(event) => setAddMenuAnchor(event.currentTarget)}
        >
          {t('ledger:addButton')}
        </Button>
        <Menu
          anchorEl={addMenuAnchor}
          open={addMenuAnchor !== null}
          onClose={() => setAddMenuAnchor(null)}
        >
          <MenuItem
            onClick={() => {
              setAddMenuAnchor(null)
              setNewTransaction({})
            }}
          >
            {t('ledger:newTransaction')}
          </MenuItem>
          <MenuItem
            onClick={() => {
              setAddMenuAnchor(null)
              setNewTransfer(true)
            }}
          >
            {t('ledger:newTransfer')}
          </MenuItem>
        </Menu>
      </PageHeader>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('ledger:description')}
      </Typography>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert
        message={bannerError}
        onDismiss={() => {
          setError(null)
          if (editLoadError) setEditTarget(null)
        }}
      />

      <LedgerFilters
        view={view}
        onChange={updateView}
        accounts={accounts}
        categories={categories}
        paymentMethods={paymentMethods}
      />

      <LedgerSummary summary={ledger?.summary} />

      {ledger?.truncated && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('ledger:truncated', { count: ledger.entries.length })}
        </Alert>
      )}

      <Box sx={{ mb: 2 }}>
        <LedgerTable
          entries={ledger?.entries}
          state={tableState}
          onRetry={retry}
          lookups={lookups}
          onEdit={(entry) =>
            setEditTarget({
              kind: entry.type === 'TRANSFER' ? 'transfer' : 'transaction',
              id: entry.id,
            })
          }
          onDelete={setDeleteTarget}
          emptyMessage={searching ? t('ledger:empty.noMatches') : t('ledger:empty.noEntries')}
        />
        {showSearchAllTime && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1 }}>
            <Button onClick={() => updateView({ scope: 'range', from: '', to: '' })}>
              {t('ledger:empty.searchAllTime')}
            </Button>
          </Box>
        )}
      </Box>

      {newTransaction !== null && (
        <TransactionFormDialog
          editing={null}
          presetCategoryId={newTransaction.presetCategoryId}
          onClose={closeDialogs}
        />
      )}
      {editingTransaction !== undefined && (
        <TransactionFormDialog
          key={editingTransaction.id}
          editing={editingTransaction}
          onClose={closeDialogs}
        />
      )}

      {(newTransfer || editingTransfer !== undefined) && (
        <ResponsiveDialog open onClose={closeDialogs} fullWidth maxWidth="md">
          <DialogTitle>
            {editingTransfer ? t('transfers:editDialogTitle') : t('transfers:addDialogTitle')}
          </DialogTitle>
          <TransferForm
            key={editingTransfer?.id ?? 'new'}
            dialog
            banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
            accounts={accounts ?? []}
            editing={editingTransfer ?? null}
            onSaved={closeDialogs}
            onError={setError}
            onCancel={closeDialogs}
          />
        </ResponsiveDialog>
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        title={
          deleteTarget?.type === 'TRANSFER'
            ? t('transfers:deleteConfirm.title')
            : t('transactions:deleteConfirm.title')
        }
        body={
          deleteTarget?.type === 'TRANSFER'
            ? t('transfers:deleteConfirm.body')
            : t('transactions:deleteConfirm.body')
        }
        confirmLabel={
          deleteTarget?.type === 'TRANSFER'
            ? t('transfers:deleteConfirm.confirmLabel')
            : t('transactions:deleteConfirm.confirmLabel')
        }
        loading={deleteAction.pending}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </PageContainer>
  )
}
