import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  seedAllocationByCategory,
  seedAllocationBySubcategory,
} from '../../mocks/handlers/investmentAllocation'
import { getInvestmentAllocation } from './investmentAllocation'

describe('investment allocation API client', () => {
  it('defaults to the category grouping', async () => {
    await expect(getInvestmentAllocation()).resolves.toEqual(seedAllocationByCategory)
  })

  it('passes groupBy and asOf as query params', async () => {
    let sent: Record<string, string> = {}
    server.use(
      http.get('/api/investments/allocation', ({ request }) => {
        sent = Object.fromEntries(new URL(request.url).searchParams)
        return HttpResponse.json(seedAllocationBySubcategory)
      }),
    )

    const rows = await getInvestmentAllocation({ groupBy: 'SUBCATEGORY', asOf: '2026-06-30' })

    expect(sent).toEqual({ groupBy: 'SUBCATEGORY', asOf: '2026-06-30' })
    expect(rows).toEqual(seedAllocationBySubcategory)
  })

  it('sub-category rows sum to their category total in the seed data', () => {
    const fixedIncomeTotal = seedAllocationBySubcategory
      .filter((row) => row.categoryId === 'icat-fixed')
      .reduce((sum, row) => sum + row.totalValue, 0)

    expect(fixedIncomeTotal).toBe(
      seedAllocationByCategory.find((row) => row.categoryId === 'icat-fixed')!.totalValue,
    )
  })
})
