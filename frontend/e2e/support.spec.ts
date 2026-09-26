import type { Page } from '@playwright/test'
import { http, HttpResponse } from 'msw'
import { seedCategories } from '../src/mocks/handlers/categories'
import { expect, expectNavMode, expectNoHorizontalOverflow, mockApi, test } from './support'

// Self-check of the F020 helpers against tiny fixture pages, so a broken helper can't make the
// real specs pass vacuously. Fixture pages are served through `page.route` on a made-up path.
const FIXTURE = '/__fixture'

async function openFixture(page: Page, html: string) {
  await page.route(`**${FIXTURE}`, (route) =>
    route.fulfill({
      contentType: 'text/html',
      body: `<!doctype html><html><body>${html}</body></html>`,
    }),
  )
  await page.goto(FIXTURE)
}

test.describe('expectNoHorizontalOverflow', () => {
  test('passes on a page that fits the viewport', async ({ page }) => {
    await openFixture(page, '<div style="width:100%">fits</div>')
    await expectNoHorizontalOverflow(page)
  })

  test('fails on a page wider than the viewport', async ({ page }) => {
    await openFixture(page, '<div style="width:3000px">too wide</div>')
    await expect(expectNoHorizontalOverflow(page)).rejects.toThrow()
  })
})

test.describe('expectNavMode', () => {
  test('permanent matches a docked drawer only', async ({ page }) => {
    await openFixture(page, '<div class="MuiDrawer-docked">nav</div>')
    await expectNavMode(page, 'permanent')
    await expect(expectNavMode(page, 'temporary')).rejects.toThrow()
  })

  test('temporary matches the absence of a docked drawer only', async ({ page }) => {
    await openFixture(page, '<div>no docked nav</div>')
    await expectNavMode(page, 'temporary')
    await expect(expectNavMode(page, 'permanent')).rejects.toThrow()
  })
})

test.describe('mockApi', () => {
  const get = (page: Page, path: string) =>
    page.evaluate(async (p) => {
      const res = await fetch(p)
      return { status: res.status, body: res.status === 200 ? await res.json() : null }
    }, path)

  test('serves the src/mocks fixtures and records unmocked requests', async ({ page, apiMock }) => {
    await openFixture(page, '<p>fixture</p>')
    expect(await get(page, '/api/categories')).toEqual({ status: 200, body: seedCategories })
    expect((await get(page, '/api/does-not-exist')).status).toBe(501)
    expect(apiMock.unmocked).toEqual(['GET /api/does-not-exist'])
    apiMock.unmocked.length = 0 // consumed: the fixture would otherwise fail this test
  })

  test('honours overrides before the default handlers', async ({ page }) => {
    await mockApi(page, [http.get('/api/categories', () => HttpResponse.json([]))])
    await openFixture(page, '<p>fixture</p>')
    expect(await get(page, '/api/categories')).toEqual({ status: 200, body: [] })
  })
})
