import type { Page, Request as PlaywrightRequest } from '@playwright/test'
import { getResponse, type RequestHandler } from 'msw'
import { handlers } from '../../src/mocks/handlers'
import { resetStores } from '../../src/mocks/store'

// The MSW handlers declare relative URLs ('/api/categories'). In the browser and under jsdom MSW
// resolves those against `location`; in this Node process there is none, so give it one.
// Only the origin matters: the value is never requested.
const ORIGIN = 'http://localhost'
if (typeof globalThis.location === 'undefined') {
  Object.defineProperty(globalThis, 'location', {
    value: new URL(ORIGIN),
    configurable: true,
  })
}

/** What `mockApi` hands back: every `/api` request that no handler answered. */
export interface ApiMock {
  /** `METHOD /path` of each unmocked request, in arrival order. Must stay empty (see `test`). */
  unmocked: string[]
}

function toFetchRequest(request: PlaywrightRequest): Request {
  const body = request.postDataBuffer()
  const method = request.method()
  // Rebased onto the fake origin above so MSW's relative handler paths match whatever port the
  // dev server is on.
  const { pathname, search } = new URL(request.url())
  return new Request(`${ORIGIN}${pathname}${search}`, {
    method,
    headers: request.headers(),
    body: body && method !== 'GET' && method !== 'HEAD' ? new Uint8Array(body) : undefined,
  })
}

/**
 * Fulfils every `/api` request of `page` from the `src/mocks` MSW handlers (the same fixtures and
 * in-memory stores the Vitest suite uses) through `page.route`; MSW itself is never started in
 * the browser. `overrides` are tried first, like `server.use(...)`. A request no handler matches
 * is answered `501`, recorded in the returned `unmocked` list, and fails the test through the
 * `test` fixture in `./test.ts`.
 *
 * The stores are module state shared by the tests of one worker, so they are reset here.
 */
export async function mockApi(page: Page, overrides: RequestHandler[] = []): Promise<ApiMock> {
  resetStores()
  const mock: ApiMock = { unmocked: [] }
  // A predicate, not the glob '**/api/**': that would also catch Vite's own `/src/api/...` modules.
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const request = route.request()
      const response = await getResponse([...overrides, ...handlers], toFetchRequest(request))
      if (!response) {
        mock.unmocked.push(`${request.method()} ${new URL(request.url()).pathname}`)
        await route.fulfill({ status: 501, body: 'Unmocked /api request' })
        return
      }
      await route.fulfill({
        status: response.status,
        headers: Object.fromEntries(response.headers.entries()),
        body: Buffer.from(await response.arrayBuffer()),
      })
    },
  )
  return mock
}
