import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  investmentOpeningPositionsStore,
  seedOpeningPosition,
} from '../../mocks/handlers/investmentOpeningPositions'
import { ApiError } from '../core/apiError'
import {
  deleteOpeningPosition,
  getOpeningPosition,
  putOpeningPosition,
} from './investmentOpeningPositions'

describe('opening positions API client', () => {
  it('getOpeningPosition returns the holding position', async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)

    await expect(getOpeningPosition('iholding-selic')).resolves.toEqual(seedOpeningPosition)
  })

  it('getOpeningPosition maps the not-found problem type to null (the empty state)', async () => {
    await expect(getOpeningPosition('iholding-selic')).resolves.toBeNull()
  })

  it('getOpeningPosition still throws for an unknown holding', async () => {
    const error = await getOpeningPosition('iholding-nope').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).type).toBe('urn:my-finances:error:investment-holding-not-found')
  })

  it('getOpeningPosition rethrows any other failure', async () => {
    server.use(
      http.get('/api/investment-holdings/:holdingId/opening-position', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )

    await expect(getOpeningPosition('iholding-selic')).rejects.toBeInstanceOf(ApiError)
  })

  it('putOpeningPosition sends the body to the holding and returns the position', async () => {
    let sentBody: unknown = null
    let sentUrl = ''
    server.use(
      http.put('/api/investment-holdings/:holdingId/opening-position', async ({ request }) => {
        sentBody = await request.json()
        sentUrl = new URL(request.url).pathname
        return HttpResponse.json(seedOpeningPosition, { status: 201 })
      }),
    )

    const position = await putOpeningPosition('iholding-selic', {
      asOfDate: '2026-07-01',
      quantity: 40,
      unitPrice: 150,
      currentValue: 6400,
    })

    expect(sentUrl).toBe('/api/investment-holdings/iholding-selic/opening-position')
    expect(sentBody).toEqual({
      asOfDate: '2026-07-01',
      quantity: 40,
      unitPrice: 150,
      currentValue: 6400,
    })
    expect(position).toEqual(seedOpeningPosition)
  })

  it('putOpeningPosition on a holding with a position replaces it in place', async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)

    const replaced = await putOpeningPosition('iholding-selic', {
      asOfDate: '2026-06-01',
      quantity: 50,
      unitPrice: 100,
    })

    expect(replaced).toMatchObject({
      id: seedOpeningPosition.id,
      quantity: 50,
      amountContributed: 5000,
    })
    expect(investmentOpeningPositionsStore.list()).toHaveLength(1)
  })

  it('deleteOpeningPosition removes the position', async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)

    await deleteOpeningPosition('iholding-selic')

    expect(investmentOpeningPositionsStore.list()).toHaveLength(0)
  })
})
