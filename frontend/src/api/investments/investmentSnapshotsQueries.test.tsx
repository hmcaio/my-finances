import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { seedBitcoinSnapshots } from '../../mocks/handlers/investmentSnapshots'
import { seedAllocationByCategory } from '../../mocks/handlers/investmentAllocation'
import { seedBitcoinSeries } from '../../mocks/handlers/investmentValueSeries'
import { seedBitcoinBuyTransfer } from '../../mocks/handlers/transfers'
import { renderHookWithQueryClient } from '../../test/renderWithQueryClient'
import { useInvestmentAllocation } from './investmentAllocationQueries'
import { useInvestmentProduct } from './investmentProductsQueries'
import { useInvestmentSnapshots, useRecordInvestmentSnapshot } from './investmentSnapshotsQueries'
import { useInvestmentValueSeries } from './investmentValueSeriesQueries'
import { useCreateTransfer, useTransfers } from '../transfers/transfersQueries'

describe('investment reads', () => {
  it('loads snapshots, allocation and value series', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      snapshots: useInvestmentSnapshots('iprod-btc'),
      allocation: useInvestmentAllocation({ groupBy: 'CATEGORY' }),
      series: useInvestmentValueSeries({ productId: 'iprod-btc' }),
    }))

    await waitFor(() => expect(result.current.snapshots.data).toEqual(seedBitcoinSnapshots))
    await waitFor(() => expect(result.current.allocation.data).toEqual(seedAllocationByCategory))
    await waitFor(() => expect(result.current.series.data).toEqual([seedBitcoinSeries]))
  })

  it('refreshes the product and the snapshot list after a snapshot is recorded', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      product: useInvestmentProduct('iprod-btc'),
      snapshots: useInvestmentSnapshots('iprod-btc'),
      record: useRecordInvestmentSnapshot(),
    }))
    await waitFor(() => expect(result.current.product.data?.latestSnapshot?.balance).toBe(900))

    await result.current.record.mutateAsync({
      productId: 'iprod-btc',
      date: '2026-09-01',
      balance: 1234.5,
    })

    await waitFor(() => expect(result.current.product.data?.latestSnapshot?.balance).toBe(1234.5))
    await waitFor(() => expect(result.current.snapshots.data?.[0].date).toBe('2026-09-01'))
  })
})

describe('transfers hooks', () => {
  it('keeps the previous page while the next one loads', async () => {
    let page = 0
    const { result, rerender } = renderHookWithQueryClient(() => useTransfers({}, page, 1))
    await waitFor(() => expect(result.current.data?.page.number).toBe(0))

    page = 1
    rerender()

    expect(result.current.data?.page.number).toBe(0)
    expect(result.current.isPlaceholderData).toBe(true)
    await waitFor(() => expect(result.current.data?.page.number).toBe(1))
  })

  it('shows a created transfer in the product trade list after the mutation', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      trades: useTransfers({ investmentProductId: 'iprod-btc' }),
      create: useCreateTransfer(),
    }))
    await waitFor(() => expect(result.current.trades.data?.content).toHaveLength(2))

    await result.current.create.mutateAsync({
      ...seedBitcoinBuyTransfer,
      description: 'Another buy',
      quantity: undefined,
      unitPrice: undefined,
      taxes: undefined,
      additionalNotes: undefined,
      investmentProductId: 'iprod-btc',
    })

    await waitFor(() => expect(result.current.trades.data?.content).toHaveLength(3))
  })
})
