import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../../core/queryClient'
import {
  getSnapshotSheet,
  recordSnapshotSheet,
  type RecordSnapshotSheetRequest,
  type RecordSnapshotSheetResult,
} from './investmentSnapshotSheet'

export const investmentSnapshotSheetKeys = {
  all: [API_KEY_ROOT, 'investment-snapshot-sheet'] as const,
}

export function useSnapshotSheet() {
  return useQuery({
    queryKey: investmentSnapshotSheetKeys.all,
    queryFn: getSnapshotSheet,
  })
}

/**
 * The batch write. Its toast is the first plural one: `successMessageOptions` hands the count to
 * the `sheetSaved` key (`_one` / `_other`), so the text says how many snapshots were saved without
 * ever carrying a balance or a name.
 */
export function useRecordSnapshotSheet() {
  return useMutation({
    meta: {
      successMessage: 'investmentSnapshots:toast.sheetSaved',
      successMessageOptions: (data) => {
        const { created, replaced } = data as RecordSnapshotSheetResult
        return { count: created + replaced }
      },
    },
    mutationFn: (request: RecordSnapshotSheetRequest) => recordSnapshotSheet(request),
  })
}
