import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { categoryCreateConflictHandler, seedCategories } from '../mocks/handlers/categories'
import { renderHookWithQueryClient } from '../test/renderWithQueryClient'
import { DUPLICATE_NAME_MESSAGE } from './categories'
import { useCategories, useCreateCategory } from './categoriesQueries'

describe('categories hooks', () => {
  it('loads the category list', async () => {
    const { result } = renderHookWithQueryClient(() => useCategories())

    await waitFor(() => expect(result.current.data).toEqual(seedCategories))
  })

  it('surfaces a failed load as the query error', async () => {
    server.use(http.get('/api/categories', () => new HttpResponse(null, { status: 500 })))
    const { result } = renderHookWithQueryClient(() => useCategories())

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
  })

  it('shares one request between two consumers of the same list', async () => {
    let requests = 0
    server.use(
      http.get('/api/categories', () => {
        requests += 1
        return HttpResponse.json(seedCategories)
      }),
    )
    const { result } = renderHookWithQueryClient(() => ({ a: useCategories(), b: useCategories() }))

    await waitFor(() => expect(result.current.a.data).toBeDefined())
    expect(result.current.b.data).toEqual(seedCategories)
    expect(requests).toBe(1)
  })

  it('refetches the list after a successful mutation', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useCategories(),
      create: useCreateCategory(),
    }))
    await waitFor(() => expect(result.current.list.data).toHaveLength(seedCategories.length))

    await result.current.create.mutateAsync({ name: 'Rent', type: 'EXPENSE' })

    await waitFor(() => expect(result.current.list.data?.map((c) => c.name)).toContain('Rent'))
  })

  it('leaves the cache alone after a failed mutation and keeps the conflict message', async () => {
    server.use(categoryCreateConflictHandler)
    let requests = 0
    server.use(
      http.get('/api/categories', () => {
        requests += 1
        return HttpResponse.json(seedCategories)
      }),
    )
    const { result } = renderHookWithQueryClient(() => ({
      list: useCategories(),
      create: useCreateCategory(),
    }))
    await waitFor(() => expect(result.current.list.data).toBeDefined())

    await expect(
      result.current.create.mutateAsync({ name: 'Groceries', type: 'EXPENSE' }),
    ).rejects.toThrow(DUPLICATE_NAME_MESSAGE)

    expect(requests).toBe(1)
  })
})
