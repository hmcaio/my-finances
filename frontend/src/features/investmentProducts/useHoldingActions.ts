import { useState } from 'react'
import type { Account } from '../../api/accounts/accounts'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import {
  useCloseInvestmentHolding,
  useCreateInvestmentHolding,
  useDeleteInvestmentHolding,
  useEditInvestmentHoldingNotes,
} from '../../api/investments/investmentHoldingsQueries'
import { useAction } from '../../hooks/useAction'

interface HoldingActionsInput {
  productId: string | undefined
  accounts: Account[] | undefined
  holdings: InvestmentHolding[] | undefined
  /** The page's selection: a new holding becomes selected, a deleted selected one is cleared. */
  selectedHoldingId: string
  setSelectedHoldingId: (id: string) => void
  setError: (message: string | null) => void
}

/**
 * State and handlers of the product's holding writes: the add dialog, the notes edit dialog and
 * the close / delete confirmations. Closing and deleting share one confirmation spinner
 * (`confirmPending`).
 */
export function useHoldingActions({
  productId,
  accounts,
  holdings,
  selectedHoldingId,
  setSelectedHoldingId,
  setError,
}: HoldingActionsInput) {
  const createHolding = useCreateInvestmentHolding()
  const closeHolding = useCloseInvestmentHolding()
  const deleteHolding = useDeleteInvestmentHolding()
  const editHoldingNotes = useEditInvestmentHoldingNotes()

  const [addOpen, setAddOpen] = useState(false)
  const [addAccountId, setAddAccountId] = useState('')
  const [addNotes, setAddNotes] = useState('')
  const addAction = useAction(setError)
  const [closeTarget, setCloseTarget] = useState<InvestmentHolding | null>(null)
  const confirmAction = useAction(setError)
  const [deleteTarget, setDeleteTarget] = useState<InvestmentHolding | null>(null)
  const [notesEdit, setNotesEdit] = useState<{ id: string; value: string } | null>(null)
  const notesAction = useAction(setError)

  // An INVESTMENT account that is open and does not hold this product yet.
  const holdingAccountIds = new Set((holdings ?? []).map((h) => h.accountId))
  const availableAccounts = (accounts ?? []).filter(
    (a) => a.type === 'INVESTMENT' && !a.closed && !holdingAccountIds.has(a.id),
  )

  function openAdd() {
    setError(null)
    setAddAccountId('')
    setAddNotes('')
    setAddOpen(true)
  }

  async function submitAdd() {
    if (!productId || addAccountId === '') return
    const added = await addAction.run(async () => {
      const created = await createHolding.mutateAsync({
        productId,
        accountId: addAccountId,
        additionalNotes: addNotes.trim() || undefined,
      })
      setSelectedHoldingId(created.id)
    })
    if (added) setAddOpen(false)
  }

  async function confirmClose() {
    if (!closeTarget) return
    if (await confirmAction.run(() => closeHolding.mutateAsync(closeTarget.id))) {
      setCloseTarget(null)
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    const deleted = await confirmAction.run(() => deleteHolding.mutateAsync(deleteTarget.id))
    if (deleted && selectedHoldingId === deleteTarget.id) setSelectedHoldingId('')
    setDeleteTarget(null)
  }

  async function saveNotes() {
    if (!notesEdit) return
    const saved = await notesAction.run(() =>
      editHoldingNotes.mutateAsync({
        id: notesEdit.id,
        additionalNotes: notesEdit.value || undefined,
      }),
    )
    if (saved) setNotesEdit(null)
  }

  return {
    add: {
      open: addOpen,
      accountId: addAccountId,
      setAccountId: setAddAccountId,
      notes: addNotes,
      setNotes: setAddNotes,
      pending: addAction.pending,
      availableAccounts,
      openDialog: openAdd,
      close: () => setAddOpen(false),
      submit: submitAdd,
    },
    notes: {
      edit: notesEdit,
      setEdit: setNotesEdit,
      pending: notesAction.pending,
      start: (h: InvestmentHolding) => setNotesEdit({ id: h.id, value: h.additionalNotes ?? '' }),
      save: saveNotes,
    },
    closing: { target: closeTarget, setTarget: setCloseTarget, confirm: confirmClose },
    removing: { target: deleteTarget, setTarget: setDeleteTarget, confirm: confirmDelete },
    confirmPending: confirmAction.pending,
  }
}

export type HoldingActions = ReturnType<typeof useHoldingActions>
