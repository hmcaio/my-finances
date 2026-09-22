import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { apiClient } from './client'
import { ApiError, defaultErrorMessage, unwrap } from './apiError'

describe('unwrap', () => {
  it('resolves with the response data on success', async () => {
    server.use(http.get('/api/things', () => HttpResponse.json({ id: 'thing-1' })))

    await expect(unwrap(apiClient.get('/things'))).resolves.toEqual({ id: 'thing-1' })
  })

  it('rejects with an ApiError carrying the backend message for a non-409 failure', async () => {
    server.use(
      http.get('/api/things', () => HttpResponse.json({ message: 'Not found' }, { status: 404 })),
    )

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
    expect((error as ApiError).message).toBe('Not found')
  })

  it('falls back to a generic message when the backend sends none', async () => {
    server.use(http.get('/api/things', () => new HttpResponse(null, { status: 404 })))

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).message).toBe('Request failed with status 404')
  })

  it('replaces a 409 with the call-site conflictMessage, ignoring the backend body', async () => {
    server.use(
      http.get('/api/things', () =>
        HttpResponse.json({ message: 'ignored backend detail' }, { status: 409 }),
      ),
    )

    const error: unknown = await unwrap(apiClient.get('/things'), 'Reassign them first').catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe('Reassign them first')
  })

  it('falls back to the generic status message for a 409 with no conflictMessage given', async () => {
    server.use(http.get('/api/things', () => new HttpResponse(null, { status: 409 })))

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).message).toBe('Request failed with status 409')
  })

  it('maps a network failure (no response) to ApiError status 0', async () => {
    server.use(http.get('/api/things', () => HttpResponse.error()))

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(0)
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
