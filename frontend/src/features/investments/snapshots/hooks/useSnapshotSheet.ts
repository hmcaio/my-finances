import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError, validationCodeMessage } from '../../../../api/core/apiError'
import {
  useRecordSnapshotSheet,
  useSnapshotSheet as useSnapshotSheetQuery,
} from '../../../../api/investments/products/investmentSnapshotSheetQueries'
import { useQueryState } from '../../../../hooks/queryState'
import { useAction } from '../../../../hooks/useAction'
import { today } from '../../../../utils/localDate'
import { buildRequest, rowCodesFromErrors, rowIssues, type SheetInputs } from '../lib/snapshotSheet'

/**
 * State and handlers of the snapshot sheet page (F040): the sheet's rows, the one date, the
 * balances as typed (by holding id, blank until typed), the rows the server rejected, the banner
 * and the batch save.
 *
 * Nothing is cleared on a failed save, whatever the status: a `400`/`404` with `errors[]` marks the
 * offending rows (resolved through the entries that were actually sent) and a `409` only words the
 * banner, so the cost of a failure is one more click, never retyping. A successful save clears the
 * inputs (and the marks); the sheet refetches through the global invalidation and the Previous
 * column shows the new values. Editing a marked row clears its mark.
 */
export function useSnapshotSheet() {
  const { t } = useTranslation('investmentSnapshots')
  const query = useSnapshotSheetQuery()
  const loadState = useQueryState(query)
  const record = useRecordSnapshotSheet()
  const [date, setDate] = useState(() => today())
  const [inputs, setInputs] = useState<SheetInputs>({})
  const [serverCodes, setServerCodes] = useState<Record<string, string[]>>({})
  const [error, setError] = useState<string | null>(null)
  const action = useAction(setError)

  const rows = query.data
  const request = useMemo(() => buildRequest(date, rows ?? [], inputs), [date, rows, inputs])
  const issues = useMemo(() => rowIssues(rows ?? [], inputs), [rows, inputs])

  function setInput(holdingId: string, value: string) {
    setInputs((prev) => ({ ...prev, [holdingId]: value }))
    setServerCodes((prev) => {
      if (!(holdingId in prev)) return prev
      const next = { ...prev }
      delete next[holdingId]
      return next
    })
  }

  /** Why a row is flagged, if it is: its own bad input first, else what the server said. */
  function rowError(holdingId: string): string | undefined {
    const issue = issues[holdingId]
    if (issue) return validationCodeMessage(issue)
    const messages = (serverCodes[holdingId] ?? [])
      .map((code) => validationCodeMessage(code))
      .filter((message): message is string => message !== undefined)
    return messages.length > 0 ? messages.join(' ') : undefined
  }

  async function save() {
    if (!request) return
    const sent = request.entries
    const saved = await action.run(
      async () => {
        try {
          await record.mutateAsync(request)
        } catch (err) {
          if (err instanceof ApiError) setServerCodes(rowCodesFromErrors(err.fieldErrors, sent))
          throw err
        }
      },
      { 409: t('sheet.conflict') },
    )
    if (saved) {
      setInputs({})
      setServerCodes({})
    }
  }

  return {
    rows,
    loadState,
    date,
    setDate,
    inputs,
    setInput,
    rowError,
    canSave: request !== null && !action.pending,
    saving: action.pending,
    save,
    error,
    dismissError: () => setError(null),
  }
}
