import { describe, expect, it } from 'vitest'
import type { FormState, LineState } from './transferFormState'
import {
  duplicateResultingBalanceProductIds,
  isLineValid,
  isTransferFormValid,
  numberOrNull,
  tradeNetPreview,
} from './transferValidation'

function line(changes: Partial<LineState> = {}): LineState {
  return {
    key: 0,
    productId: 'p1',
    side: 'BUY',
    quantity: '2',
    unitPrice: '10',
    resultingBalance: '',
    closeHolding: false,
    ...changes,
  }
}

function plain(changes: Partial<FormState> = {}): FormState {
  return {
    mode: 'plain',
    date: '2026-08-10',
    description: 'Pay',
    additionalNotes: '',
    fromAccountId: 'a1',
    toAccountId: 'a2',
    amount: '25',
    cashAccountId: '',
    investmentAccountId: '',
    taxes: '0',
    lines: [line()],
    ...changes,
  }
}

function trade(changes: Partial<FormState> = {}): FormState {
  return plain({
    mode: 'trade',
    fromAccountId: '',
    toAccountId: '',
    amount: '',
    cashAccountId: 'cash',
    investmentAccountId: 'inv',
    ...changes,
  })
}

describe('numberOrNull', () => {
  it('parses a number and ignores surrounding whitespace-only emptiness', () => {
    expect(numberOrNull('12.5')).toBe(12.5)
    expect(numberOrNull('0')).toBe(0)
  })

  it('is null for blank or non-numeric text', () => {
    expect(numberOrNull('')).toBeNull()
    expect(numberOrNull('   ')).toBeNull()
    expect(numberOrNull('abc')).toBeNull()
    expect(numberOrNull('Infinity')).toBeNull()
  })
})

describe('duplicateResultingBalanceProductIds', () => {
  it('is empty when no product sets a balance twice', () => {
    const lines = [
      line({ key: 0, productId: 'p1', resultingBalance: '5' }),
      line({ key: 1, productId: 'p2', resultingBalance: '6' }),
    ]

    expect(duplicateResultingBalanceProductIds(lines).size).toBe(0)
  })

  it('flags a product that sets a resulting balance on more than one line', () => {
    const lines = [
      line({ key: 0, productId: 'p1', resultingBalance: '5' }),
      line({ key: 1, productId: 'p1', resultingBalance: '6' }),
      line({ key: 2, productId: 'p2', resultingBalance: '7' }),
    ]

    expect([...duplicateResultingBalanceProductIds(lines)]).toEqual(['p1'])
  })

  it('ignores lines with a blank balance or no product', () => {
    const lines = [
      line({ key: 0, productId: 'p1', resultingBalance: '5' }),
      line({ key: 1, productId: 'p1', resultingBalance: '  ' }),
      line({ key: 2, productId: '', resultingBalance: '1' }),
      line({ key: 3, productId: '', resultingBalance: '2' }),
    ]

    expect(duplicateResultingBalanceProductIds(lines).size).toBe(0)
  })
})

describe('isLineValid', () => {
  it('accepts a product with a positive quantity and unit price', () => {
    expect(isLineValid(line())).toBe(true)
  })

  it('rejects a missing product', () => {
    expect(isLineValid(line({ productId: '' }))).toBe(false)
  })

  it('rejects a non-positive or missing quantity or unit price', () => {
    expect(isLineValid(line({ quantity: '' }))).toBe(false)
    expect(isLineValid(line({ quantity: '0' }))).toBe(false)
    expect(isLineValid(line({ quantity: '-1' }))).toBe(false)
    expect(isLineValid(line({ unitPrice: '' }))).toBe(false)
    expect(isLineValid(line({ unitPrice: '0' }))).toBe(false)
  })

  it('accepts a blank or zero-or-more resulting balance and rejects a negative one', () => {
    expect(isLineValid(line({ resultingBalance: '' }))).toBe(true)
    expect(isLineValid(line({ resultingBalance: '0' }))).toBe(true)
    expect(isLineValid(line({ resultingBalance: '10' }))).toBe(true)
    expect(isLineValid(line({ resultingBalance: '-1' }))).toBe(false)
    expect(isLineValid(line({ resultingBalance: 'abc' }))).toBe(false)
  })

  it('allows closing the holding only on a SELL', () => {
    expect(isLineValid(line({ side: 'SELL', closeHolding: true }))).toBe(true)
    expect(isLineValid(line({ side: 'BUY', closeHolding: true }))).toBe(false)
  })
})

describe('tradeNetPreview', () => {
  it('is null in plain mode', () => {
    expect(tradeNetPreview(plain())).toBeNull()
  })

  it('nets BUY against SELL and adds taxes once', () => {
    const form = trade({
      taxes: '5',
      lines: [
        line({ key: 0, side: 'BUY', quantity: '10', unitPrice: '100' }),
        line({ key: 1, side: 'SELL', quantity: '1', unitPrice: '100' }),
      ],
    })

    expect(tradeNetPreview(form)).toBe(905)
  })

  it('is null while a line has no quantity or unit price yet', () => {
    expect(tradeNetPreview(trade({ lines: [line({ quantity: '' })] }))).toBeNull()
  })

  it('treats unreadable taxes as zero', () => {
    expect(tradeNetPreview(trade({ taxes: '' }))).toBe(20)
  })
})

describe('isTransferFormValid', () => {
  it('accepts a plain transfer with two different accounts and a positive amount', () => {
    expect(isTransferFormValid(plain())).toBe(true)
  })

  it('rejects a blank date or description', () => {
    expect(isTransferFormValid(plain({ date: '' }))).toBe(false)
    expect(isTransferFormValid(plain({ description: '   ' }))).toBe(false)
  })

  it('rejects a plain transfer with a missing account, a non-positive amount or the same account', () => {
    expect(isTransferFormValid(plain({ fromAccountId: '' }))).toBe(false)
    expect(isTransferFormValid(plain({ toAccountId: '' }))).toBe(false)
    expect(isTransferFormValid(plain({ amount: '0' }))).toBe(false)
    expect(isTransferFormValid(plain({ amount: '' }))).toBe(false)
    expect(isTransferFormValid(plain({ toAccountId: 'a1' }))).toBe(false)
  })

  it('ignores the trade-only fields in plain mode', () => {
    expect(isTransferFormValid(plain({ lines: [], taxes: 'x' }))).toBe(true)
  })

  it('accepts a trade confirmation with both accounts, valid lines and a non-zero net', () => {
    expect(isTransferFormValid(trade())).toBe(true)
  })

  it('rejects a trade with a missing or identical cash and investment account', () => {
    expect(isTransferFormValid(trade({ cashAccountId: '' }))).toBe(false)
    expect(isTransferFormValid(trade({ investmentAccountId: '' }))).toBe(false)
    expect(isTransferFormValid(trade({ cashAccountId: 'inv' }))).toBe(false)
  })

  it('rejects a trade without lines or with an invalid line', () => {
    expect(isTransferFormValid(trade({ lines: [] }))).toBe(false)
    expect(isTransferFormValid(trade({ lines: [line({ productId: '' })] }))).toBe(false)
  })

  it('rejects negative or unreadable taxes', () => {
    expect(isTransferFormValid(trade({ taxes: '-1' }))).toBe(false)
    expect(isTransferFormValid(trade({ taxes: '' }))).toBe(false)
    expect(isTransferFormValid(trade({ taxes: '0' }))).toBe(true)
  })

  it('rejects two lines of one product that both set a resulting balance', () => {
    const lines = [line({ key: 0, resultingBalance: '1' }), line({ key: 1, resultingBalance: '2' })]

    expect(isTransferFormValid(trade({ lines }))).toBe(false)
  })

  it('rejects a settlement that nets to exactly zero', () => {
    const lines = [
      line({ key: 0, side: 'BUY', productId: 'p1' }),
      line({ key: 1, side: 'SELL', productId: 'p2' }),
    ]

    expect(isTransferFormValid(trade({ lines }))).toBe(false)
  })
})
