import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { apiClient } from './client'
import { ApiError, defaultErrorMessage, unwrap } from './apiError'

describe('unwrap', () => {
  it('resolves with the response data on success', async () => {
    server.use(http.get('/api/things', () => HttpResponse.json({ id: 'thing-1' })))

    await expect(unwrap(apiClient.get('/things'))).resolves.toEqual({ id: 'thing-1' })
  })

  it('maps a response whose RFC 9457 type is in the central map to its message', async () => {
    server.use(
      http.get('/api/things', () =>
        HttpResponse.json(
          { type: 'urn:my-finances:error:account-has-history', title: 'Account has history' },
          { status: 409 },
        ),
      ),
    )

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).type).toBe('urn:my-finances:error:account-has-history')
    expect((error as ApiError).message).toBe(
      'This account has history (transactions, transfers, recurring templates or investment products) and cannot be deleted - close it instead.',
    )
  })

  it('falls back to the generic status message when the type is absent', async () => {
    server.use(http.get('/api/things', () => new HttpResponse(null, { status: 404 })))

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).type).toBeUndefined()
    expect((error as ApiError).message).toBe('Request failed with status 404')
  })

  it('falls back to the generic status message when the type is unrecognized', async () => {
    server.use(
      http.get('/api/things', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:some-brand-new-case' }, { status: 409 }),
      ),
    )

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).type).toBe('urn:my-finances:error:some-brand-new-case')
    expect((error as ApiError).message).toBe('Request failed with status 409')
  })

  it('maps a network failure (no response) to ApiError status 0', async () => {
    server.use(http.get('/api/things', () => HttpResponse.error()))

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(0)
  })

  it('leaves fieldErrors undefined when the response carries no errors array', async () => {
    server.use(http.get('/api/things', () => new HttpResponse(null, { status: 404 })))

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).fieldErrors).toBeUndefined()
  })
})

describe('unwrap - F032 bean-validation errors[] extension', () => {
  it('builds a joined summary string from the per-code message map and passes through fieldErrors', async () => {
    server.use(
      http.post('/api/things', () =>
        HttpResponse.json(
          {
            type: 'urn:my-finances:error:method-argument-not-valid',
            errors: [
              { field: 'amount', code: 'positive' },
              { field: 'description', code: 'not-blank' },
            ],
          },
          { status: 400 },
        ),
      ),
    )

    const error: unknown = await unwrap(apiClient.post('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.type).toBe('urn:my-finances:error:method-argument-not-valid')
    expect(apiError.message).toContain('greater than zero')
    expect(apiError.message).toContain('required')
    expect(apiError.fieldErrors).toEqual([
      { field: 'amount', code: 'positive' },
      { field: 'description', code: 'not-blank' },
    ])
  })

  it('does not repeat the same message twice when several fields share one code', async () => {
    server.use(
      http.post('/api/things', () =>
        HttpResponse.json(
          {
            type: 'urn:my-finances:error:method-argument-not-valid',
            errors: [
              { field: 'name', code: 'not-blank' },
              { field: 'description', code: 'not-blank' },
            ],
          },
          { status: 400 },
        ),
      ),
    )

    const error: unknown = await unwrap(apiClient.post('/things')).catch((err: unknown) => err)

    const message = (error as ApiError).message
    expect(message.match(/required/g)).toHaveLength(1)
  })

  it('falls back to the generic status message when every code is unrecognized', async () => {
    server.use(
      http.post('/api/things', () =>
        HttpResponse.json(
          {
            type: 'urn:my-finances:error:method-argument-not-valid',
            errors: [{ field: null, code: 'some-brand-new-invariant' }],
          },
          { status: 400 },
        ),
      ),
    )

    const error: unknown = await unwrap(apiClient.post('/things')).catch((err: unknown) => err)

    expect((error as ApiError).message).toBe('Request failed with status 400')
    expect((error as ApiError).fieldErrors).toEqual([
      { field: null, code: 'some-brand-new-invariant' },
    ])
  })

  it('reports a cross-field (@AssertTrue) violation with a null field the same way', async () => {
    server.use(
      http.post('/api/things', () =>
        HttpResponse.json(
          {
            type: 'urn:my-finances:error:method-argument-not-valid',
            errors: [{ field: null, code: 'at-most-one-resulting-balance-per-product' }],
          },
          { status: 400 },
        ),
      ),
    )

    const error: unknown = await unwrap(apiClient.post('/things')).catch((err: unknown) => err)

    expect((error as ApiError).fieldErrors).toEqual([
      { field: null, code: 'at-most-one-resulting-balance-per-product' },
    ])
  })
})

describe('defaultErrorMessage', () => {
  it("returns the ApiError's own message when there is no status override", () => {
    const error = new ApiError(404, 'Not found')

    expect(defaultErrorMessage(error)).toBe('Not found')
  })

  it('returns the override for the matching status', () => {
    const error = new ApiError(404, 'Not found')

    expect(defaultErrorMessage(error, { 404: 'This record was deleted elsewhere.' })).toBe(
      'This record was deleted elsewhere.',
    )
  })

  it("falls back to the ApiError's own message when no override matches its status", () => {
    const error = new ApiError(500, 'Server error')

    expect(defaultErrorMessage(error, { 404: 'This record was deleted elsewhere.' })).toBe(
      'Server error',
    )
  })

  it('returns a plain Error message for a non-ApiError error', () => {
    expect(defaultErrorMessage(new Error('boom'))).toBe('boom')
  })

  it('returns a generic fallback for something that is not even an Error', () => {
    expect(defaultErrorMessage('not an error')).toBe('Something went wrong.')
  })
})
