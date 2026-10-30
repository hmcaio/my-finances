import type {
  OpeningPosition,
  OpeningPositionRequest,
} from '../../api/investments/investmentOpeningPositions'
import { today } from '../../utils/localDate'

/**
 * What the opening-position fields collect (F038, ADR 0033), as the strings the inputs hold.
 * `asOfDate` defaults to today; the position is optional on the create form, so a draft is only
 * "started" once the user has put something in it (see `isOpeningPositionStarted`).
 */
export interface OpeningPositionDraft {
  asOfDate: string
  quantity: string
  unitPrice: string
  /** Optional: records the holding's snapshot on `asOfDate`; empty means "no snapshot". */
  currentValue: string
}

export function emptyOpeningPositionDraft(): OpeningPositionDraft {
  return { asOfDate: today(), quantity: '', unitPrice: '', currentValue: '' }
}

/** A draft that edits an existing position; its current value lives in the snapshots, so empty. */
export function draftOf(position: OpeningPosition): OpeningPositionDraft {
  return {
    asOfDate: position.asOfDate,
    quantity: String(position.quantity),
    unitPrice: String(position.unitPrice),
    currentValue: '',
  }
}

/**
 * Whether anything was entered beyond the default as-of date. Once started, cotas and average
 * price are required (the section is not silently dropped); an untouched section sends nothing.
 */
export function isOpeningPositionStarted(
  draft: OpeningPositionDraft,
  defaultAsOfDate: string,
): boolean {
  return (
    draft.asOfDate !== defaultAsOfDate ||
    draft.quantity !== '' ||
    draft.unitPrice !== '' ||
    draft.currentValue !== ''
  )
}

/** The request for a complete, valid draft; `null` when a required value is missing or invalid. */
export function openingPositionRequestOf(
  draft: OpeningPositionDraft,
): OpeningPositionRequest | null {
  const quantity = Number(draft.quantity)
  const unitPrice = Number(draft.unitPrice)
  if (draft.asOfDate === '' || draft.quantity === '' || draft.unitPrice === '') return null
  if (!(quantity > 0) || !(unitPrice > 0)) return null
  const request: OpeningPositionRequest = { asOfDate: draft.asOfDate, quantity, unitPrice }
  if (draft.currentValue !== '') {
    const currentValue = Number(draft.currentValue)
    if (!(currentValue >= 0)) return null
    request.currentValue = currentValue
  }
  return request
}
