import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { InvestmentSnapshot } from '../../api/investments/investmentSnapshots'
import {
  useDeleteInvestmentSnapshot,
  useRecordInvestmentSnapshot,
  useUpdateInvestmentSnapshot,
} from '../../api/investments/investmentSnapshotsQueries'
import { useAction } from '../../hooks/useAction'
import { today } from '../../utils/localDate'
import type { SnapshotEdit } from './SnapshotsPanel'

/** A snapshot needs a date and a non-negative balance (zero is a liquidated position). */
export function isValidSnapshotInput(date: string, balance: string): boolean {
  return date !== '' && balance !== '' && Number(balance) >= 0
}

/**
 * State and handlers of the selected holding's snapshot writes: the "Record snapshot" dialog, the
 * inline edit of a history row and the delete confirmation. Each write goes through its own
 * `useAction`, so its `pending` flag is independent of the others. `holdingId` is the selected
 * holding (the handlers do nothing without one).
 */
export function useSnapshotActions({
  holdingId,
  setError,
}: {
  holdingId: string | undefined
  setError: (message: string | null) => void
}) {
  const { t } = useTranslation('investmentProducts')
  const recordSnapshot = useRecordInvestmentSnapshot()
  const updateSnapshot = useUpdateInvestmentSnapshot()
  const deleteSnapshot = useDeleteInvestmentSnapshot()

  const [form, setForm] = useState(() => ({ date: today(), balance: '' }))
  const [dialogOpen, setDialogOpen] = useState(false)
  const recordAction = useAction(setError)
  const [editing, setEditing] = useState<SnapshotEdit | null>(null)
  const saveEditAction = useAction(setError)
  const [deleteTarget, setDeleteTarget] = useState<InvestmentSnapshot | null>(null)
  const deleteAction = useAction(setError)
  const notFound = { 404: t('detail.snapshotNotFound') }

  function openDialog() {
    setError(null)
    setForm({ date: today(), balance: '' })
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
    setError(null)
  }

  async function submitRecord() {
    if (!holdingId) return
    const balance = Number(form.balance)
    if (!isValidSnapshotInput(form.date, form.balance)) return
    // A same-day entry replaces the earlier one; the refetch after the write reorders the list.
    const recorded = await recordAction.run(() =>
      recordSnapshot.mutateAsync({ holdingId, date: form.date, balance }),
    )
    if (recorded) setDialogOpen(false)
  }

  async function saveEdit() {
    if (!holdingId || !editing) return
    const balance = Number(editing.balance)
    if (!isValidSnapshotInput(editing.date, editing.balance)) return
    const saved = await saveEditAction.run(
      () =>
        updateSnapshot.mutateAsync({
          holdingId,
          snapshotId: editing.id,
          date: editing.date,
          balance,
        }),
      notFound,
    )
    if (saved) setEditing(null)
  }

  async function confirmDelete() {
    if (!holdingId || !deleteTarget) return
    await deleteAction.run(
      () => deleteSnapshot.mutateAsync({ holdingId, snapshotId: deleteTarget.id }),
      notFound,
    )
    setDeleteTarget(null)
  }

  return {
    record: {
      open: dialogOpen,
      form,
      setForm,
      pending: recordAction.pending,
      openDialog,
      closeDialog,
      submit: submitRecord,
    },
    edit: { editing, setEditing, pending: saveEditAction.pending, save: saveEdit },
    remove: {
      target: deleteTarget,
      setTarget: setDeleteTarget,
      pending: deleteAction.pending,
      confirm: confirmDelete,
    },
  }
}

export type SnapshotActions = ReturnType<typeof useSnapshotActions>
