import { describe, expect, it } from 'vitest'
import { ApiError } from '../../core/apiError'
import { getSnapshotSheet, recordSnapshotSheet } from './investmentSnapshotSheet'

describe('investment snapshot sheet API client', () => {
  it('getSnapshotSheet lists the open holdings with product and account names and the latest snapshot', async () => {
    const rows = await getSnapshotSheet()

    expect(rows.map((row) => row.productName)).toEqual(['Bitcoin', 'Tesouro Selic 2029'])
    expect(rows[0]).toMatchObject({
      holdingId: 'iholding-btc',
      accountName: 'XP Investimentos',
      latestSnapshot: { date: '2026-08-05', balance: 900 },
    })
    expect(rows[1].latestSnapshot).toBeNull()
  })

  it('recordSnapshotSheet posts the date and entries and returns the counts', async () => {
    const result = await recordSnapshotSheet({
      date: '2026-09-01',
      entries: [
        { holdingId: 'iholding-btc', balance: 950 },
        { holdingId: 'iholding-selic', balance: 10 },
      ],
    })

    expect(result).toEqual({ created: 2, replaced: 0 })
  })

  it('recordSnapshotSheet reports a missing holding as an ApiError carrying the indexed rows', async () => {
    const error: unknown = await recordSnapshotSheet({
      date: '2026-09-01',
      entries: [
        { holdingId: 'iholding-btc', balance: 950 },
        { holdingId: 'iholding-gone', balance: 10 },
      ],
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
    expect((error as ApiError).fieldErrors).toEqual([
      { field: 'entries[1].holdingId', code: 'investment-holding-not-found' },
    ])
  })
})
