import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedBitcoinSnapshots } from '../../mocks/handlers/investmentSnapshots'
import {
  deleteInvestmentSnapshot,
  getInvestmentSnapshots,
  recordInvestmentSnapshot,
  updateInvestmentSnapshot,
} from './investmentSnapshots'
import { ApiError } from '../core/apiError'

describe('investment snapshots API client', () => {
  it('getInvestmentSnapshots returns the holding history, most recent first', async () => {
    await expect(getInvestmentSnapshots('iholding-btc')).resolves.toEqual(seedBitcoinSnapshots)
  })

  it('getInvestmentSnapshots of a holding with none returns an empty list', async () => {
    await expect(getInvestmentSnapshots('iholding-selic')).resolves.toEqual([])
  })

  it('recordInvestmentSnapshot posts date and balance to the holding and returns the snapshot', async () => {
    let sentBody: unknown = null
    let sentUrl = ''
    server.use(
      http.post('/api/investment-holdings/:holdingId/snapshots', async ({ request }) => {
        sentBody = await request.json()
        sentUrl = new URL(request.url).pathname
        return HttpResponse.json(
          { id: 'isnap-x', holdingId: 'iholding-selic', date: '2026-09-01', balance: 0 },
          { status: 201 },
        )
      }),
    )

    const snapshot = await recordInvestmentSnapshot('iholding-selic', {
      date: '2026-09-01',
      balance: 0,
    })

    expect(sentUrl).toBe('/api/investment-holdings/iholding-selic/snapshots')
    expect(sentBody).toEqual({ date: '2026-09-01', balance: 0 })
    expect(snapshot).toMatchObject({ holdingId: 'iholding-selic', date: '2026-09-01', balance: 0 })
  })

  it('recordInvestmentSnapshot on an existing date returns the replaced snapshot', async () => {
    const snapshot = await recordInvestmentSnapshot('iholding-btc', {
      date: seedBitcoinSnapshots[0].date,
      balance: 950,
    })

    expect(snapshot).toMatchObject({ id: seedBitcoinSnapshots[0].id, balance: 950 })
  })

  it('updateInvestmentSnapshot puts date and balance to the snapshot and returns it', async () => {
    let sentBody: unknown = null
    let sentUrl = ''
    server.use(
      http.put('/api/investment-holdings/:holdingId/snapshots/:snapshotId', async ({ request }) => {
        sentBody = await request.clone().json()
        sentUrl = new URL(request.url).pathname
      }),
    )

    const snapshot = await updateInvestmentSnapshot('iholding-btc', 'isnap-1', {
      date: '2026-07-30',
      balance: 0,
    })

    expect(sentUrl).toBe('/api/investment-holdings/iholding-btc/snapshots/isnap-1')
    expect(sentBody).toEqual({ date: '2026-07-30', balance: 0 })
    expect(snapshot).toMatchObject({ id: 'isnap-1', date: '2026-07-30', balance: 0 })
  })

  it('updateInvestmentSnapshot maps a 409 to the conflict message', async () => {
    server.use(
      http.put('/api/investment-holdings/:holdingId/snapshots/:snapshotId', () =>
        HttpResponse.json({}, { status: 409 }),
      ),
    )

    const error = await updateInvestmentSnapshot('iholding-btc', 'isnap-1', {
      date: '2026-08-05',
      balance: 1,
    }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toMatch(/another snapshot already has that date/)
  })

  it('deleteInvestmentSnapshot deletes the snapshot', async () => {
    await expect(deleteInvestmentSnapshot('iholding-btc', 'isnap-2')).resolves.toBeUndefined()
    await expect(getInvestmentSnapshots('iholding-btc')).resolves.toEqual([seedBitcoinSnapshots[1]])
  })

  it('deleteInvestmentSnapshot maps a 409 to the conflict message', async () => {
    server.use(
      http.delete('/api/investment-holdings/:holdingId/snapshots/:snapshotId', () =>
        HttpResponse.json({}, { status: 409 }),
      ),
    )

    const error = await deleteInvestmentSnapshot('iholding-btc', 'isnap-2').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toMatch(/latest snapshot would no longer be zero/)
  })
})
