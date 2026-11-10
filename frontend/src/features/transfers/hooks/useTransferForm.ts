import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Account } from '../../../api/accounts/accounts'
import type { Transfer } from '../../../api/transfers/transfers'
import type { TradeSide } from '../lib/tradeMath'
import {
  appendLine,
  changeInvestmentAccount,
  changeLineSide,
  initialFormState,
  pickerAccounts,
  removeLine,
  switchMode,
  updateLine,
  type FormMode,
  type FormState,
  type LineState,
  type TransferCarry,
  type TransferFormPreset,
} from '../lib/transferFormState'

/** What the trade lines section can do to the form's lines. */
export interface LineActions {
  update: (key: number, changes: Partial<LineState>) => void
  changeSide: (line: LineState, side: TradeSide) => void
  add: () => void
  remove: (key: number) => void
}

/**
 * The transfer form's state: starts from the edited transfer, the preset or a blank form, and
 * exposes the transitions of `transferFormState` bound to it (field patch, mode switch, investment
 * account change, line add/update/remove with their stable keys).
 */
export function useTransferForm(
  accounts: Account[],
  editing: Transfer | null,
  preset: TransferFormPreset | null,
  carry: TransferCarry | null = null,
) {
  const { t } = useTranslation(['transfers'])
  const [form, setForm] = useState(() => initialFormState(editing, preset, accounts, t, carry))
  const [nextLineKey, setNextLineKey] = useState(form.lines.length)

  function patch(changes: Partial<FormState>) {
    setForm((prev) => ({ ...prev, ...changes }))
  }

  function setMode(mode: FormMode) {
    const { investmentAccounts } = pickerAccounts(accounts)
    setForm((prev) =>
      switchMode(
        prev,
        mode,
        investmentAccounts.map((a) => a.id),
      ),
    )
  }

  function setInvestmentAccountId(investmentAccountId: string) {
    setForm((prev) => changeInvestmentAccount(prev, investmentAccountId))
  }

  function patchLines(change: (lines: LineState[]) => LineState[]) {
    setForm((prev) => ({ ...prev, lines: change(prev.lines) }))
  }

  const lineActions: LineActions = {
    update: (key, changes) => patchLines((lines) => updateLine(lines, key, changes)),
    changeSide: (line, side) =>
      patchLines((lines) => updateLine(lines, line.key, changeLineSide(line, side))),
    add: () => {
      patchLines((lines) => appendLine(lines, nextLineKey))
      setNextLineKey((n) => n + 1)
    },
    remove: (key) => patchLines((lines) => removeLine(lines, key)),
  }

  return { form, patch, setMode, setInvestmentAccountId, lineActions }
}
