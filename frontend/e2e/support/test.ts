import { test as base, expect } from '@playwright/test'
import { mockApi, type ApiMock } from './mockApi'

/**
 * `test` for every spec: mocks the API before the test body and fails the test afterwards if any
 * `/api` request went unmocked (F020: the mock layer must be complete for the routes visited).
 * Only the helpers' own self-check reads `apiMock.unmocked` directly.
 */
export const test = base.extend<{ apiMock: ApiMock }>({
  apiMock: [
    async ({ page }, use) => {
      const mock = await mockApi(page)
      await use(mock)
      expect(mock.unmocked, 'unmocked /api requests').toEqual([])
    },
    { auto: true },
  ],
})

export { expect }
