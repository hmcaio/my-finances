import { describe, expect, it } from 'vitest'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import { formatEntryAmount, ledgerEntryStyle } from './ledgerEntryStyle'

describe('ledgerEntryStyle', () => {
  it('income is a green up-arrow with a plus', () => {
    expect(ledgerEntryStyle('INCOME')).toMatchObject({
      Icon: ArrowUpwardIcon,
      color: 'success',
      sign: '+',
      labelKey: 'type.income',
    })
  })

  it('expense is a red down-arrow with a minus', () => {
    expect(ledgerEntryStyle('EXPENSE')).toMatchObject({
      Icon: ArrowDownwardIcon,
      color: 'error',
      sign: '-',
      labelKey: 'type.expense',
    })
  })

  it('a transfer is a neutral swap icon with no sign', () => {
    expect(ledgerEntryStyle('TRANSFER')).toMatchObject({
      Icon: SwapHorizIcon,
      color: 'info',
      sign: '',
      labelKey: 'type.transfer',
    })
  })
})

describe('formatEntryAmount', () => {
  it('signs income and expense and leaves a transfer unsigned', () => {
    expect(formatEntryAmount('INCOME', 3000, 'en')).toBe('+3,000.00')
    expect(formatEntryAmount('EXPENSE', 42.5, 'en')).toBe('-42.50')
    expect(formatEntryAmount('TRANSFER', 200, 'en')).toBe('200.00')
  })

  it('uses the active locale for the number', () => {
    expect(formatEntryAmount('EXPENSE', 1234.5, 'pt-BR')).toBe('-1.234,50')
  })
})
