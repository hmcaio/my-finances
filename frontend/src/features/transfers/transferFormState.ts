import type { Account } from '../../api/accounts/accounts'
import type { CreateTransferRequest, Transfer } from '../../api/transfers/transfers'
import { translateKey, type Translate } from '../../i18n/translate'
import { today } from '../../utils/localDate'
import type { TradeSide } from './tradeMath'
import { numberOrNull } from './transferValidation'

/**
 * Pure state of the transfer form (F005, reworked by F027/ADR 0024): the shape of the form, how it
 * starts (blank, a preset, or an edited transfer), the transitions the component applies, and the
 * request it submits. No React in here, so every rule is unit-tested without rendering.
 */

/**
 * A buy/sell to start the form with (F009 spec, kept by F027): the product's own INVESTMENT
 * account on the traded side, the product selected and a suggested description. The user still
 * picks the cash account and everything else.
 */
export interface TransferFormPreset {
  direction: 'buy' | 'sell'
  investmentAccountId: string
  productId: string
  productName: string
}

export interface LineState {
  key: number
  productId: string
  side: TradeSide
  quantity: string
  unitPrice: string
  resultingBalance: string
  closeHolding: boolean
}

export type FormMode = 'plain' | 'trade'

export interface FormState {
  mode: FormMode
  date: string
  description: string
  additionalNotes: string
  // Plain mode.
  fromAccountId: string
  toAccountId: string
  amount: string
  // Trade mode (F027, ADR 0024): cashAccountId/investmentAccountId are unlabeled - direction is
  // derived from the lines' net settlement, never picked by the user.
  cashAccountId: string
  investmentAccountId: string
  taxes: string
  lines: LineState[]
}

export function blankLine(key: number, productId = '', side: TradeSide = 'BUY'): LineState {
  return {
    key,
    productId,
    side,
    quantity: '',
    unitPrice: '',
    resultingBalance: '',
    closeHolding: false,
  }
}

/**
 * Trade-confirmation edit: the cash and INVESTMENT accounts, re-derived from the transfer's
 * from/to now that `accounts` is known (the INVESTMENT side is whichever endpoint is an
 * INVESTMENT account).
 */
function deriveTradeAccounts(
  editing: Transfer,
  accounts: Account[],
): Pick<FormState, 'cashAccountId' | 'investmentAccountId'> {
  const toAccount = accounts.find((a) => a.id === editing.toAccountId)
  const fromAccount = accounts.find((a) => a.id === editing.fromAccountId)
  const investmentAccountId =
    toAccount?.type === 'INVESTMENT'
      ? editing.toAccountId
      : fromAccount?.type === 'INVESTMENT'
        ? editing.fromAccountId
        : ''
  const cashAccountId =
    investmentAccountId === editing.toAccountId ? editing.fromAccountId : editing.toAccountId
  return { cashAccountId, investmentAccountId }
}

/** A fresh form, built per use so its date is today's local date rather than the date the module
 * was first loaded. */
export function initialFormState(
  editing: Transfer | null,
  preset: TransferFormPreset | null,
  accounts: Account[],
  t: Translate,
): FormState {
  const blank: FormState = {
    mode: 'plain',
    date: today(),
    description: '',
    additionalNotes: '',
    fromAccountId: '',
    toAccountId: '',
    amount: '',
    cashAccountId: '',
    investmentAccountId: '',
    taxes: '0',
    lines: [blankLine(0)],
  }
  if (editing) {
    if (editing.tradeConfirmation) {
      return {
        ...blank,
        mode: 'trade',
        date: editing.date,
        description: editing.description,
        additionalNotes: editing.additionalNotes ?? '',
        taxes: editing.taxes === null ? '0' : String(editing.taxes),
        lines: editing.tradeConfirmation.lines.map((line, i) => ({
          key: i,
          productId: line.productId,
          side: line.side,
          quantity: String(line.quantity),
          unitPrice: String(line.unitPrice),
          resultingBalance: line.resultingBalance === null ? '' : String(line.resultingBalance),
          closeHolding: line.closeHolding,
        })),
        fromAccountId: editing.fromAccountId,
        toAccountId: editing.toAccountId,
        ...deriveTradeAccounts(editing, accounts),
      }
    }
    return {
      ...blank,
      mode: 'plain',
      date: editing.date,
      amount: String(editing.amount),
      fromAccountId: editing.fromAccountId,
      toAccountId: editing.toAccountId,
      description: editing.description,
      additionalNotes: editing.additionalNotes ?? '',
    }
  }
  if (preset) {
    return {
      ...blank,
      mode: 'trade',
      investmentAccountId: preset.investmentAccountId,
      lines: [blankLine(0, preset.productId, preset.direction === 'buy' ? 'BUY' : 'SELL')],
      description: translateKey(
        t,
        preset.direction === 'buy' ? 'transfers:tradeLabelBuy' : 'transfers:tradeLabelSell',
        { product: preset.productName },
      ),
    }
  }
  return blank
}

/**
 * Switches between the plain and trade shapes. Declassifying a trade confirmation back to plain
 * (TransferService.edit's "clearing any trade confirmation it had") would otherwise keep the
 * confirmation's INVESTMENT account in a now-plain fromAccountId/toAccountId - drop whichever side
 * that is so the picker (which excludes INVESTMENT accounts) doesn't show a stale, unselectable
 * value.
 */
export function switchMode(
  form: FormState,
  mode: FormMode,
  investmentAccountIds: readonly string[],
): FormState {
  if (mode === 'trade') return { ...form, mode }
  return {
    ...form,
    mode,
    fromAccountId: investmentAccountIds.includes(form.fromAccountId) ? '' : form.fromAccountId,
    toAccountId: investmentAccountIds.includes(form.toAccountId) ? '' : form.toAccountId,
  }
}

/** The holdings offered depend on the account, so changing it drops every line's product pick. */
export function changeInvestmentAccount(form: FormState, investmentAccountId: string): FormState {
  return {
    ...form,
    investmentAccountId,
    lines: form.lines.map((l) => ({ ...l, productId: '' })),
  }
}

/** The line fields a side change sets: "close this holding" only exists on a SELL. */
export function changeLineSide(line: LineState, side: TradeSide): Partial<LineState> {
  return { side, closeHolding: side === 'SELL' ? line.closeHolding : false }
}

export function updateLine(lines: LineState[], key: number, changes: Partial<LineState>) {
  return lines.map((l) => (l.key === key ? { ...l, ...changes } : l))
}

export function appendLine(lines: LineState[], key: number): LineState[] {
  return [...lines, blankLine(key)]
}

export function removeLine(lines: LineState[], key: number): LineState[] {
  return lines.filter((l) => l.key !== key)
}

/**
 * The accounts the pickers offer: closed ones are left out. Neither a confirmation's cash side nor
 * either side of a plain transfer may be INVESTMENT (F027/ADR 0024's "a transfer between two
 * investment accounts is not supported", generalized to plain transfers too, issue #113: an
 * INVESTMENT account has no cash balance of its own, so a lineless transfer into/out of one has no
 * trackable destination - money must move in/out of it only through a trade confirmation).
 */
export function pickerAccounts(accounts: Account[]) {
  const openAccounts = accounts.filter((a) => !a.closed)
  return {
    investmentAccounts: openAccounts.filter((a) => a.type === 'INVESTMENT'),
    cashOptions: openAccounts.filter((a) => a.type !== 'INVESTMENT'),
  }
}

/** The create/edit request for a form that already passed `isTransferFormValid`. */
export function buildTransferRequest(form: FormState): CreateTransferRequest {
  const common = {
    date: form.date,
    description: form.description.trim(),
    additionalNotes: form.additionalNotes.trim() || undefined,
  }
  if (form.mode === 'plain') {
    return {
      date: common.date,
      fromAccountId: form.fromAccountId,
      toAccountId: form.toAccountId,
      amount: Number(form.amount),
      description: common.description,
      additionalNotes: common.additionalNotes,
    }
  }
  return {
    date: common.date,
    cashAccountId: form.cashAccountId,
    investmentAccountId: form.investmentAccountId,
    description: common.description,
    additionalNotes: common.additionalNotes,
    tradeConfirmation: {
      taxes: numberOrNull(form.taxes) ?? 0,
      lines: form.lines.map((l) => ({
        productId: l.productId,
        side: l.side,
        quantity: Number(l.quantity),
        unitPrice: Number(l.unitPrice),
        resultingBalance: l.resultingBalance.trim() === '' ? undefined : Number(l.resultingBalance),
        closeHolding: l.closeHolding,
      })),
    },
  }
}
