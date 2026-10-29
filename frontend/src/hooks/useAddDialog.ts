import { useState } from 'react'

/**
 * State of a page's "Add" dialog: whether it is open and the draft being typed. `close` always
 * resets the draft to `emptyDraft`, so Cancel, Esc and a successful save all reopen empty (the
 * reset used to be copied into each page's close handler and was forgotten, issue #232). A failed
 * save keeps the dialog open and the draft intact, so the user can fix it and retry.
 */
export function useAddDialog<Draft>(emptyDraft: Draft) {
  const [isOpen, setIsOpen] = useState(false)
  const [draft, setDraft] = useState(emptyDraft)

  return {
    isOpen,
    draft,
    setDraft,
    show: () => setIsOpen(true),
    close: () => {
      setIsOpen(false)
      setDraft(emptyDraft)
    },
  }
}
