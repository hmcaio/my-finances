import { http, HttpResponse } from 'msw'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F036: the login screen fits every viewport, and a signed-out visit to any route lands on it.
test.describe('login', () => {
  test('a signed-out visit lands on a login form that fits the viewport', async ({ page }) => {
    await mockApi(page, [
      http.get('/api/auth/session', () =>
        HttpResponse.json(
          {
            type: 'urn:my-finances:error:unauthenticated',
            title: 'Unauthenticated',
            status: 401,
            instance: '/api/auth/session',
          },
          { status: 401 },
        ),
      ),
    ])

    await page.goto('/transactions')

    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
    await expect(page.getByLabel('Username')).toBeVisible()
    await expect(page.getByLabel('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
    expect(new URL(page.url()).pathname).toBe('/login')
    await expectNoHorizontalOverflow(page)
  })
})
