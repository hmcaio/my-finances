import { describe, expect, it } from 'vitest'
import { LEDGER_MAX_ENTRIES } from '../../mocks/handlers/ledger'
import {
  seedBitcoinBuyTransfer,
  seedBitcoinSellTransfer,
  seedCreditCardPaymentTransfer,
  seedTransfers,
} from '../../mocks/handlers/transfers'
import {
  seedGroceriesTransaction,
  seedTransactions,
  transactions,
} from '../../mocks/handlers/transactions'
import { ApiError } from '../core/apiError'
import { createTransaction } from '../transactions/transactions'
import { getLedger } from './ledger'

describe('getLedger (the mocked GET /api/ledger)', () => {
  it('interleaves transactions and transfers newest first with flat entries', async () => {
    const ledger = await getLedger({ dateFrom: '2026-01-01', dateTo: '2026-01-31' })

    expect(ledger.entries.map((e) => `${e.date} ${e.type}`)).toEqual([
      '2026-01-25 TRANSFER',
      '2026-01-20 INCOME',
      '2026-01-10 TRANSFER',
      '2026-01-05 EXPENSE',
    ])
    expect(ledger.truncated).toBe(false)
    const expense = ledger.entries.find((e) => e.id === seedGroceriesTransaction.id)!
    expect(expense).toMatchObject({
      accountId: seedGroceriesTransaction.accountId,
      categoryId: seedGroceriesTransaction.categoryId,
      fromAccountId: null,
      tradeLines: [],
    })
    const transfer = ledger.entries.find((e) => e.id === seedCreditCardPaymentTransfer.id)!
    expect(transfer).toMatchObject({
      categoryId: null,
      accountId: null,
      paymentMethodId: null,
      fromAccountId: seedCreditCardPaymentTransfer.fromAccountId,
    })
  })

  it('summarises income and expense of the whole scope, excluding transfers', async () => {
    const ledger = await getLedger({ dateFrom: '2026-01-01', dateTo: '2026-01-31' })

    const income = seedTransactions
      .filter((t) => t.type === 'INCOME')
      .reduce((s, t) => s + t.amount, 0)
    const expense = seedTransactions
      .filter((t) => t.type === 'EXPENSE')
      .reduce((s, t) => s + t.amount, 0)
    expect(ledger.summary).toEqual({ income, expense, net: income - expense })
  })

  it('carries the trade lines of a trade confirmation', async () => {
    const ledger = await getLedger({ dateFrom: '2026-08-01', dateTo: '2026-08-31' })

    expect(ledger.entries.map((e) => e.id)).toEqual([
      seedBitcoinSellTransfer.id,
      seedBitcoinBuyTransfer.id,
    ])
    expect(ledger.entries[1].tradeLines).toEqual([{ side: 'BUY', productId: 'iprod-btc' }])
  })

  it('type=TRANSFER reads transfers only; INCOME and EXPENSE read transactions only', async () => {
    const transfersOnly = await getLedger({ type: 'TRANSFER', dateTo: '2026-01-31' })
    const incomeOnly = await getLedger({ type: 'INCOME' })

    expect(transfersOnly.entries.every((e) => e.type === 'TRANSFER')).toBe(true)
    expect(transfersOnly.entries).toHaveLength(seedTransfers.length)
    expect(transfersOnly.summary.income).toBe(0)
    expect(incomeOnly.entries.map((e) => e.type)).toEqual(['INCOME'])
  })

  it('a category or payment method excludes transfers; transfer plus a category is empty', async () => {
    const byCategory = await getLedger({ categoryId: seedGroceriesTransaction.categoryId })
    const contradictory = await getLedger({ type: 'TRANSFER', categoryId: 'cat-1' })

    expect(byCategory.entries.map((e) => e.id)).toEqual([seedGroceriesTransaction.id])
    expect(byCategory.summary.expense).toBe(seedGroceriesTransaction.amount)
    expect(contradictory.entries).toEqual([])
  })

  it('accountId matches a transaction account or either side of a transfer', async () => {
    const ledger = await getLedger({ accountId: 'acct-3', dateTo: '2026-01-31' })

    expect(ledger.entries.map((e) => e.id).sort()).toEqual(seedTransfers.map((t) => t.id).sort())
  })

  it('searches description and notes ignoring case and accents, within the period', async () => {
    await createTransaction({
      date: '2026-01-15',
      amount: 12,
      categoryId: 'cat-1',
      accountId: 'acct-1',
      paymentMethodId: 'pm-1',
      description: 'Café da manhã',
    })

    const accentless = await getLedger({ q: 'cafe' })
    const upperAccented = await getLedger({ q: 'CAFÉ' })
    const viaNotes = await getLedger({ q: 'direct deposit' })
    const outOfScope = await getLedger({ q: 'cafe', dateFrom: '2026-02-01' })

    expect(accentless.entries.map((e) => e.description)).toEqual(['Café da manhã'])
    expect(upperAccented.entries).toHaveLength(1)
    expect(viaNotes.entries.map((e) => e.description)).toEqual(['Monthly salary deposit'])
    expect(outOfScope.entries).toEqual([])
    expect(accentless.summary.expense).toBe(12)
  })

  it('treats a literal % as text rather than a wildcard', async () => {
    const ledger = await getLedger({ q: '%' })

    expect(ledger.entries).toEqual([])
  })

  it('rejects search text over 100 characters with the method-validation problem type', async () => {
    const error: unknown = await getLedger({ q: 'a'.repeat(101) }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).type).toBe('urn:my-finances:error:handler-method-validation')
  })

  it('caps the list at 1,000 entries and flags it truncated while the summary covers every row', async () => {
    // Rows go straight into the mock store: 1,002 mocked HTTP creates took ~6s under CI coverage.
    for (let i = 0; i < LEDGER_MAX_ENTRIES + 2; i++) {
      transactions.add({
        ...seedGroceriesTransaction,
        id: `txn-bulk-${i}`,
        date: '2030-06-15',
        amount: 1,
        description: `Bulk ${i}`,
      })
    }

    const ledger = await getLedger({ dateFrom: '2030-06-01', dateTo: '2030-06-30' })

    expect(ledger.entries).toHaveLength(LEDGER_MAX_ENTRIES)
    expect(ledger.truncated).toBe(true)
    expect(ledger.summary.expense).toBe(LEDGER_MAX_ENTRIES + 2)
  })

  it('reflects a create made through the transactions endpoint', async () => {
    const before = await getLedger({ dateFrom: '2027-01-01', dateTo: '2027-01-31' })
    expect(before.entries).toEqual([])

    await createTransaction({
      date: '2027-01-10',
      amount: 5,
      categoryId: 'cat-2',
      accountId: 'acct-1',
      paymentMethodId: 'pm-1',
      description: 'Bonus',
    })

    const after = await getLedger({ dateFrom: '2027-01-01', dateTo: '2027-01-31' })
    expect(after.entries.map((e) => e.description)).toEqual(['Bonus'])
    expect(after.entries[0].type).toBe('INCOME')
  })
})
