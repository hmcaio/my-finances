import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedInvestmentCategories } from '../../mocks/handlers/investmentCategories'
import { seedInvestmentProducts } from '../../mocks/handlers/investmentProducts'
import { renderHookWithQueryClient } from '../../test/renderWithQueryClient'
import { useCreateInvestmentCategory, useInvestmentCategories } from './investmentCategoriesQueries'
import { useCreateInvestmentSubcategory } from './investmentSubcategoriesQueries'
import {
  useCreateInvestmentProduct,
  useInvestmentProduct,
  useInvestmentProducts,
} from './investmentProductsQueries'
import { SAVE_CONFLICT_MESSAGE } from './investmentProducts'

describe('investment taxonomy and product hooks', () => {
  it('loads the taxonomy and refetches it after a category or sub-category is created', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useInvestmentCategories(),
      createCategory: useCreateInvestmentCategory(),
      createSubcategory: useCreateInvestmentSubcategory(),
    }))
    await waitFor(() => expect(result.current.list.data).toEqual(seedInvestmentCategories))

    await result.current.createCategory.mutateAsync({ name: 'Real Estate' })
    await result.current.createSubcategory.mutateAsync({
      investmentCategoryId: 'icat-crypto',
      name: 'Stablecoins',
    })

    await waitFor(() => {
      const list = result.current.list.data ?? []
      expect(list.map((c) => c.name)).toContain('Real Estate')
      expect(list.find((c) => c.id === 'icat-crypto')?.subcategories.map((s) => s.name)).toEqual([
        'Stablecoins',
      ])
    })
  })

  it('lists products per account and loads one by id', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useInvestmentProducts(seedInvestmentProducts[0].accountId),
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

    server.use(http.post('/api/investment-products', () => HttpResponse.json({}, { status: 409 })))
    await expect(
      result.current.create.mutateAsync({
        accountId: 'acct-inv',
        investmentCategoryId: 'icat-fixed',
        name: 'LCI',
      }),
    ).rejects.toThrow(SAVE_CONFLICT_MESSAGE)
  })
})
