import { describe, expect, it } from 'vitest'
import { seedAccounts, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import {
  seedBitcoinBuyTransfer,
  seedBitcoinSellTransfer,
  seedCreditCardPaymentTransfer,
} from '../../mocks/handlers/transfers'
import { today } from '../../utils/localDate'
import {
  appendLine,
  blankLine,
  buildTransferRequest,
  changeInvestmentAccount,
  changeLineSide,
  initialFormState,
  removeLine,
  switchMode,
  updateLine,
  type FormState,
  type LineState,
} from './transferFormState'

const accounts = [...seedAccounts, seedInvestmentAccount]
const t = (key: never, options?: Record<string, unknown>) =>
  `${key as string}|${String(options?.product)}`

function line(changes: Partial<LineState> = {}): LineState {
  return { ...blankLine(0, 'p1'), quantity: '2', unitPrice: '10', ...changes }
}

function form(changes: Partial<FormState> = {}): FormState {
  return { ...initialFormState(null, null, accounts, t), description: 'Pay', ...changes }
}

describe('blankLine', () => {
  it('is an empty BUY line unless told otherwise', () => {
    expect(blankLine(3)).toEqual({
      key: 3,
      productId: '',
      side: 'BUY',
      quantity: '',
      unitPrice: '',
      resultingBalance: '',
      closeHolding: false,
    })
    expect(blankLine(1, 'p9', 'SELL')).toMatchObject({ key: 1, productId: 'p9', side: 'SELL' })
  })
})

describe('initialFormState', () => {
  it('starts a blank plain transfer dated today with one blank line and zero taxes', () => {
    const state = initialFormState(null, null, accounts, t)

    expect(state).toEqual({
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
    })
  })

  it('prefills a plain transfer being edited', () => {
    const state = initialFormState(
      { ...seedCreditCardPaymentTransfer, additionalNotes: 'n', amount: 200 },
      null,
      accounts,
      t,
    )

    expect(state).toMatchObject({
      mode: 'plain',
      date: seedCreditCardPaymentTransfer.date,
      amount: '200',
      description: seedCreditCardPaymentTransfer.description,
      additionalNotes: 'n',
      fromAccountId: seedCreditCardPaymentTransfer.fromAccountId,
      toAccountId: seedCreditCardPaymentTransfer.toAccountId,
      cashAccountId: '',
      investmentAccountId: '',
    })
  })

  it('prefills an edited buy confirmation and derives cash and investment accounts (investment on the to side)', () => {
    const state = initialFormState(seedBitcoinBuyTransfer, null, accounts, t)

    expect(state).toMatchObject({
      mode: 'trade',
      taxes: '5',
      cashAccountId: seedBitcoinBuyTransfer.fromAccountId,
      investmentAccountId: seedBitcoinBuyTransfer.toAccountId,
      fromAccountId: seedBitcoinBuyTransfer.fromAccountId,
      toAccountId: seedBitcoinBuyTransfer.toAccountId,
    })
    expect(state.lines).toEqual([
      {
        key: 0,
        productId: 'iprod-btc',
        side: 'BUY',
        quantity: '0.01',
        unitPrice: '100000',
        resultingBalance: '',
        closeHolding: false,
      },
    ])
  })

  it('derives the accounts of an edited sell confirmation (investment on the from side)', () => {
    const state = initialFormState(seedBitcoinSellTransfer, null, accounts, t)

    expect(state.investmentAccountId).toBe(seedBitcoinSellTransfer.fromAccountId)
    expect(state.cashAccountId).toBe(seedBitcoinSellTransfer.toAccountId)
  })

  it('leaves the investment account empty when neither endpoint is an investment account', () => {
    const state = initialFormState(seedBitcoinBuyTransfer, null, seedAccounts, t)

    expect(state.investmentAccountId).toBe('')
    // Unreachable in practice (a confirmation always has an INVESTMENT endpoint); the pre-split
    // fallback picked the to side as the cash account, kept as is.
    expect(state.cashAccountId).toBe(seedBitcoinBuyTransfer.toAccountId)
  })

  it('keeps recorded taxes, notes and resulting balances, and defaults null taxes to 0', () => {
    const editing = {
      ...seedBitcoinBuyTransfer,
      taxes: null,
      additionalNotes: 'memo',
      tradeConfirmation: {
        lines: [{ ...seedBitcoinBuyTransfer.tradeConfirmation!.lines[0], resultingBalance: 7.5 }],
      },
    }

    const state = initialFormState(editing, null, accounts, t)

    expect(state.taxes).toBe('0')
    expect(state.additionalNotes).toBe('memo')
    expect(state.lines[0].resultingBalance).toBe('7.5')
  })

  it('starts a buy preset in trade mode with the product line and a translated description', () => {
    const state = initialFormState(
      null,
      {
        direction: 'buy',
        investmentAccountId: 'acct-inv',
        productId: 'iprod-btc',
        productName: 'Bitcoin',
      },
      accounts,
      t,
    )

    expect(state).toMatchObject({
      mode: 'trade',
      investmentAccountId: 'acct-inv',
      cashAccountId: '',
      description: 'transfers:tradeLabelBuy|Bitcoin',
    })
    expect(state.lines).toEqual([blankLine(0, 'iprod-btc', 'BUY')])
  })

  it('starts a sell preset on a SELL line', () => {
    const state = initialFormState(
      null,
      {
        direction: 'sell',
        investmentAccountId: 'acct-inv',
        productId: 'iprod-btc',
        productName: 'Bitcoin',
      },
      accounts,
      t,
    )

    expect(state.lines[0].side).toBe('SELL')
    expect(state.description).toBe('transfers:tradeLabelSell|Bitcoin')
  })

  it('ignores the preset when editing', () => {
    const state = initialFormState(
      seedCreditCardPaymentTransfer,
      {
        direction: 'buy',
        investmentAccountId: 'acct-inv',
        productId: 'iprod-btc',
        productName: 'Bitcoin',
      },
      accounts,
      t,
    )

    expect(state.mode).toBe('plain')
  })
})

describe('switchMode', () => {
  it('only flips the mode when switching to trade', () => {
    const before = form({ fromAccountId: 'acct-1', toAccountId: 'acct-inv' })

    expect(switchMode(before, 'trade', ['acct-inv'])).toEqual({ ...before, mode: 'trade' })
  })

  it('drops an investment account from the plain from and to sides when switching to plain', () => {
    const before = form({ mode: 'trade', fromAccountId: 'acct-1', toAccountId: 'acct-inv' })

    expect(switchMode(before, 'plain', ['acct-inv'])).toMatchObject({
      mode: 'plain',
      fromAccountId: 'acct-1',
      toAccountId: '',
    })
    const flipped = form({ mode: 'trade', fromAccountId: 'acct-inv', toAccountId: 'acct-1' })
    expect(switchMode(flipped, 'plain', ['acct-inv'])).toMatchObject({
      fromAccountId: '',
      toAccountId: 'acct-1',
    })
  })

  it('keeps both sides when neither is an investment account', () => {
    const before = form({ mode: 'trade', fromAccountId: 'acct-1', toAccountId: 'acct-3' })

    expect(switchMode(before, 'plain', ['acct-inv'])).toMatchObject({
      fromAccountId: 'acct-1',
      toAccountId: 'acct-3',
    })
  })
})

describe('changeInvestmentAccount', () => {
  it('sets the account and drops every line product, keeping the rest of each line', () => {
    const before = form({
      investmentAccountId: 'acct-inv',
      lines: [line({ key: 0, productId: 'p1' }), line({ key: 1, productId: 'p2', quantity: '5' })],
    })

    const after = changeInvestmentAccount(before, 'acct-other')

    expect(after.investmentAccountId).toBe('acct-other')
    expect(after.lines.map((l) => l.productId)).toEqual(['', ''])
    expect(after.lines[1]).toMatchObject({ key: 1, quantity: '5' })
  })
})

describe('line helpers', () => {
  it('changeLineSide keeps closeHolding on SELL and clears it otherwise', () => {
    const closing = line({ side: 'SELL', closeHolding: true })

    expect(changeLineSide(closing, 'SELL')).toEqual({ side: 'SELL', closeHolding: true })
    expect(changeLineSide(closing, 'BUY')).toEqual({ side: 'BUY', closeHolding: false })
  })

  it('updateLine patches only the line with that key', () => {
    const lines = [line({ key: 0 }), line({ key: 1 })]

    const next = updateLine(lines, 1, { quantity: '9' })

    expect(next.map((l) => l.quantity)).toEqual(['2', '9'])
    expect(next[0]).toBe(lines[0])
  })

  it('appendLine adds a blank line with the given key at the end', () => {
    const next = appendLine([line({ key: 0 })], 4)

    expect(next).toHaveLength(2)
    expect(next[1]).toEqual(blankLine(4))
  })

  it('removeLine drops the line with that key', () => {
    const next = removeLine([line({ key: 0 }), line({ key: 1 })], 0)

    expect(next.map((l) => l.key)).toEqual([1])
  })
})

describe('buildTransferRequest', () => {
  it('builds the plain shape with a trimmed description and no empty notes', () => {
    const request = buildTransferRequest(
      form({
        date: '2026-08-10',
        fromAccountId: 'acct-1',
        toAccountId: 'acct-3',
        amount: '25.5',
        description: '  Pay  ',
        additionalNotes: '   ',
      }),
    )

    expect(request).toEqual({
      date: '2026-08-10',
      fromAccountId: 'acct-1',
      toAccountId: 'acct-3',
      amount: 25.5,
      description: 'Pay',
      additionalNotes: undefined,
    })
  })

  it('builds the trade shape with numbers, optional resulting balance and trimmed notes', () => {
    const request = buildTransferRequest(
      form({
        mode: 'trade',
        date: '2026-08-10',
        cashAccountId: 'acct-1',
        investmentAccountId: 'acct-inv',
        description: 'Buy',
        additionalNotes: ' memo ',
        taxes: '5',
        lines: [
          line({ productId: 'p1', quantity: '10', unitPrice: '100', resultingBalance: '2000' }),
          line({ key: 1, productId: 'p2', side: 'SELL', closeHolding: true }),
        ],
      }),
    )

    expect(request).toEqual({
      date: '2026-08-10',
      cashAccountId: 'acct-1',
      investmentAccountId: 'acct-inv',
      description: 'Buy',
      additionalNotes: 'memo',
      tradeConfirmation: {
        taxes: 5,
        lines: [
          {
            productId: 'p1',
            side: 'BUY',
            quantity: 10,
            unitPrice: 100,
            resultingBalance: 2000,
            closeHolding: false,
          },
          {
            productId: 'p2',
            side: 'SELL',
            quantity: 2,
            unitPrice: 10,
            resultingBalance: undefined,
            closeHolding: true,
          },
        ],
      },
    })
  })
})
