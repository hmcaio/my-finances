import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { seedInstitutions } from '../mocks/handlers/institutions'
import { seedPaymentMethods } from '../mocks/handlers/paymentMethods'
import { renderHookWithQueryClient } from '../test/renderWithQueryClient'
import { useCreateInstitution, useInstitutions } from './institutionsQueries'
import { usePaymentMethods } from './paymentMethodsQueries'

describe('institutions hooks', () => {
  it('loads the list', async () => {
    const { result } = renderHookWithQueryClient(() => useInstitutions())

    await waitFor(() => expect(result.current.data).toEqual(seedInstitutions))
  })

  it('shares one request between two consumers (page and picker)', async () => {
    let requests = 0
    server.use(
      http.get('/api/institutions', () => {
        requests += 1
        return HttpResponse.json(seedInstitutions)
      }),
    )
    const { result } = renderHookWithQueryClient(() => ({
      page: useInstitutions(),
      picker: useInstitutions(),
    }))

    await waitFor(() => expect(result.current.page.data).toBeDefined())
    expect(result.current.picker.data).toEqual(seedInstitutions)
    expect(requests).toBe(1)
  })

  it('has the new institution in the list by the time the create mutation resolves', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      list: useInstitutions(),
      create: useCreateInstitution(),
    }))
    await waitFor(() => expect(result.current.list.data).toBeDefined())

    const created = await result.current.create.mutateAsync({ name: 'Inter' })

    await waitFor(() => expect(result.current.list.data?.map((i) => i.id)).toContain(created.id))
  })
})

describe('payment methods hooks', () => {
  it('loads the list', async () => {
    const { result } = renderHookWithQueryClient(() => usePaymentMethods())

    await waitFor(() => expect(result.current.data).toEqual(seedPaymentMethods))
  })
})
