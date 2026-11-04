import { useState } from 'react'

/**
 * State of an inline row edit: which row is being edited (`editingId`, `null` for none) and the
 * draft typed so far. `start(id, draft)` selects a row with its current values; `cancel` (also
 * what a page calls after a successful save) clears the selection and resets the draft to
 * `emptyDraft`. A failed save leaves the row open and the draft intact, so the user can retry.
 */
export function useInlineEdit<Draft>(emptyDraft: Draft) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState(emptyDraft)

  return {
    editingId,
    draft,
    setDraft,
    start: (id: string, initialDraft: Draft) => {
      setEditingId(id)
      setDraft(initialDraft)
    },
    cancel: () => {
      setEditingId(null)
      setDraft(emptyDraft)
    },
  }
}
