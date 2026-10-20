import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedInvestmentProducts } from '../../mocks/handlers/investmentProducts'
import { renderHookWithQueryClient } from '../../test/renderWithQueryClient'
import {
  useCreateInvestmentProduct,
  useInvestmentProduct,
  useInvestmentProducts,
} from './investmentProductsQueries'
import { resolveErrorMessage } from '../../test/errorMessages'

// F034/ADR 0028: InvestmentCategory/InvestmentSubcategory lost create/rename/delete - every row
// is fixed, migration-seeded content, so there is no mutation left to exercise a refetch through.
// The read side (`useInvestmentCategories`) is covered by `investmentCategories.test.ts` instead.
describe('investment product hooks', () => {
  it('lists every product and loads one by id', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useInvestmentProducts(),
      one: useInvestmentProduct('iprod-btc'),
    }))

    await waitFor(() =>
      expect(result.current.list.data).toHaveLength(seedInvestmentProducts.length),
    )
    await waitFor(() => expect(result.current.one.data?.name).toBe('Bitcoin'))
  })

  it('refetches the product list after a create, and keeps the conflict message on failure', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useInvestmentProducts(),
      create: useCreateInvestmentProduct(),
    }))
    await waitFor(() => expect(result.current.list.data).toBeDefined())

    await result.current.create.mutateAsync({
      accountId: 'acct-inv',
      investmentCategoryId: 'icat-fixed',
      name: 'LCI',
    })
    await waitFor(() => expect(result.current.list.data?.map((p) => p.name)).toContain('LCI'))

    server.use(
      http.post('/api/investment-products', () =>
        HttpResponse.json(
          { type: 'urn:my-finances:error:investment-product-name-already-exists' },
          { status: 409 },
        ),
      ),
    )
    await expect(
      result.current.create.mutateAsync({
        accountId: 'acct-inv',
        investmentCategoryId: 'icat-fixed',
        name: 'LCI',
      }),
    ).rejects.toThrow(
      resolveErrorMessage('urn:my-finances:error:investment-product-name-already-exists'),
    )
  })
})
