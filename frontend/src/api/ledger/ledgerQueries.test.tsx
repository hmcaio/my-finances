import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { seedGroceriesTransaction } from '../../mocks/handlers/transactions'
import { renderHookWithQueryClient } from '../../test/renderWithQueryClient'
import { useLedger } from './ledgerQueries'

describe('useLedger', () => {
  it('loads the ledger for a filter', async () => {
    const { result } = renderHookWithQueryClient(() =>
      useLedger({ dateFrom: '2026-01-01', dateTo: '2026-01-31', type: 'EXPENSE' }),
    )

    await waitFor(() =>
      expect(result.current.data?.entries.map((e) => e.id)).toEqual([seedGroceriesTransaction.id]),
    )
    expect(result.current.data?.summary.expense).toBe(seedGroceriesTransaction.amount)
  })

  it('keeps the previous month on screen while the next one loads', async () => {
    let month = '2026-01'
    const { result, rerender } = renderHookWithQueryClient(() =>
      useLedger({ dateFrom: `${month}-01`, dateTo: `${month}-28` }),
    )
    await waitFor(() => expect(result.current.data?.entries.length).toBeGreaterThan(0))
    const january = result.current.data

    month = '2026-02'
    rerender()

    expect(result.current.data).toBe(january)
    expect(result.current.isPlaceholderData).toBe(true)
    await waitFor(() => expect(result.current.data?.entries).toEqual([]))
    expect(result.current.isPlaceholderData).toBe(false)
  })
})
