import { describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import type { Category } from '../../../api/categories/categories'
import {
  seedCategories,
  seedFuelCategory,
  seedGroceriesCategory,
  seedSalaryCategory,
} from '../../../mocks/handlers/categories'
import { seedGroceriesTransaction } from '../../../mocks/handlers/transactions'
import { formFromTransaction } from '../../transactions/transactionForm'
import { useEntryDraft } from './useEntryDraft'

const categories: Category[] = [...seedCategories, seedFuelCategory]

function setup(options: Partial<Parameters<typeof useEntryDraft>[0]> = {}) {
  return renderHook(() => useEntryDraft({ initialType: 'EXPENSE', categories, ...options }))
}

describe('useEntryDraft', () => {
  it('starts on the given type with an empty transaction dated today', () => {
    const { result } = setup({ initialType: 'INCOME' })

    expect(result.current.type).toBe('INCOME')
    expect(result.current.transaction.amount).toBe('')
    expect(result.current.transaction.date).not.toBe('')
  })

  it('opens on Expense with a preset category (the Fuel page)', () => {
    const { result } = setup({ presetCategoryId: seedFuelCategory.id })

    expect(result.current.type).toBe('EXPENSE')
    expect(result.current.transaction.categoryId).toBe(seedFuelCategory.id)
  })

  describe('switching type', () => {
    it('carries date and account from Expense to the transfer as From', () => {
      const { result } = setup()
      act(() => result.current.patchTransaction({ date: '2026-01-14', accountId: 'acct-1' }))

      act(() => result.current.switchType('TRANSFER'))

      expect(result.current.type).toBe('TRANSFER')
      expect(result.current.transferSeed).toEqual({
        date: '2026-01-14',
        fromAccountId: 'acct-1',
        toAccountId: '',
      })
    })

    it('remounts the transfer form on every switch to it (a new key)', () => {
      const { result } = setup()
      const first = result.current.transferKey

      act(() => result.current.switchType('TRANSFER'))
      const second = result.current.transferKey
      act(() => result.current.switchType('INCOME'))
      act(() => result.current.switchType('TRANSFER'))

      expect(second).not.toBe(first)
      expect(result.current.transferKey).not.toBe(second)
    })

    it('carries what the transfer form reported from Transfer to Income as the account', () => {
      const { result } = setup()
      act(() => result.current.switchType('TRANSFER'))
      act(() => result.current.reportCarry({ date: '2026-02-03', accountId: 'acct-9' }))

      act(() => result.current.switchType('INCOME'))

      expect(result.current.type).toBe('INCOME')
      expect(result.current.transaction.date).toBe('2026-02-03')
      expect(result.current.transaction.accountId).toBe('acct-9')
    })

    it('keeps the rest of the transaction draft across Expense, Transfer and back', () => {
      const { result } = setup()
      act(() => result.current.patchTransaction({ amount: '12', description: 'Lunch' }))

      act(() => result.current.switchType('TRANSFER'))
      act(() => result.current.switchType('EXPENSE'))

      expect(result.current.transaction.amount).toBe('12')
      expect(result.current.transaction.description).toBe('Lunch')
    })

    it('clears a category whose type no longer matches when Expense becomes Income', () => {
      const { result } = setup()
      act(() => result.current.patchTransaction({ categoryId: seedGroceriesCategory.id }))

      act(() => result.current.switchType('INCOME'))

      expect(result.current.transaction.categoryId).toBe('')
    })

    it('keeps a category whose type still matches', () => {
      const { result } = setup({ initialType: 'INCOME' })
      act(() => result.current.patchTransaction({ categoryId: seedSalaryCategory.id }))

      act(() => result.current.switchType('TRANSFER'))
      act(() => result.current.switchType('INCOME'))

      expect(result.current.transaction.categoryId).toBe(seedSalaryCategory.id)
    })

    it('clears the fuel details together with a mismatched fuel category', () => {
      const { result } = setup({ presetCategoryId: seedFuelCategory.id })
      act(() => result.current.patchTransaction({ vehicleId: 'veh-1', liters: '30' }))

      act(() => result.current.switchType('INCOME'))

      expect(result.current.transaction).toMatchObject({
        categoryId: '',
        vehicleId: '',
        liters: '',
      })
    })
  })

  describe('"Add and new" carry rules', () => {
    it('keeps date, account and payment method of a plain transaction and clears the rest', () => {
      const { result } = setup()
      act(() =>
        result.current.patchTransaction({
          date: '2026-01-14',
          accountId: 'acct-1',
          paymentMethodId: 'pm-1',
          amount: '15',
          categoryId: seedGroceriesCategory.id,
          description: 'Coffee',
          additionalNotes: 'with milk',
        }),
      )

      act(() => result.current.transactionAdded())

      expect(result.current.transaction).toMatchObject({
        date: '2026-01-14',
        accountId: 'acct-1',
        paymentMethodId: 'pm-1',
        amount: '',
        categoryId: '',
        description: '',
        additionalNotes: '',
      })
    })

    it('keeps category, vehicle and fuel type of a fuel fill-up and clears liters, price, km and odometer', () => {
      const { result } = setup()
      act(() =>
        result.current.patchTransaction({
          date: '2026-01-14',
          accountId: 'acct-1',
          paymentMethodId: 'pm-1',
          amount: '200',
          categoryId: seedFuelCategory.id,
          description: 'Fill-up',
          vehicleId: 'veh-1',
          fuelType: 'GASOLINA',
          liters: '40',
          pricePerLiter: '5',
          kmSinceLastFill: '400',
          odometer: '12000',
        }),
      )

      act(() => result.current.transactionAdded())

      expect(result.current.transaction).toMatchObject({
        categoryId: seedFuelCategory.id,
        vehicleId: 'veh-1',
        fuelType: 'GASOLINA',
        amount: '',
        description: '',
        liters: '',
        pricePerLiter: '',
        kmSinceLastFill: '',
        odometer: '',
      })
    })

    it('reseeds the transfer form from the saved transfer: date, from and to only', () => {
      const { result } = setup({ initialType: 'TRANSFER' })
      const before = result.current.transferKey

      act(() =>
        result.current.transferAdded({
          date: '2026-01-15',
          fromAccountId: 'acct-1',
          toAccountId: 'acct-3',
        }),
      )

      expect(result.current.transferSeed).toEqual({
        date: '2026-01-15',
        fromAccountId: 'acct-1',
        toAccountId: 'acct-3',
      })
      expect(result.current.transferKey).not.toBe(before)
    })

    it('carries the saved transfer to a transaction switched to afterwards', () => {
      const { result } = setup({ initialType: 'TRANSFER' })
      act(() =>
        result.current.transferAdded({
          date: '2026-01-15',
          fromAccountId: 'acct-1',
          toAccountId: 'acct-3',
        }),
      )

      act(() => result.current.switchType('EXPENSE'))

      expect(result.current.transaction.date).toBe('2026-01-15')
      expect(result.current.transaction.accountId).toBe('acct-1')
    })
  })

  describe('edit mode', () => {
    it('locks the type and starts from the edited transaction', () => {
      const { result } = setup({
        lockedType: 'EXPENSE',
        editingTransaction: seedGroceriesTransaction,
      })

      act(() => result.current.switchType('INCOME'))
      act(() => result.current.switchType('TRANSFER'))

      expect(result.current.type).toBe('EXPENSE')
      expect(result.current.transaction).toEqual(formFromTransaction(seedGroceriesTransaction))
    })

    it('locks a transfer edit on Transfer', () => {
      const { result } = setup({ lockedType: 'TRANSFER' })

      act(() => result.current.switchType('EXPENSE'))

      expect(result.current.type).toBe('TRANSFER')
    })
  })
})
