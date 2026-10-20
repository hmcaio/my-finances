import { http, HttpResponse } from 'msw'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F021 (last PR 4 batch): the first-run onboarding screen at each viewport project (mobile 390,
// tablet 768, desktop 1280). It renders outside `Layout` (there is no nav yet), gated by zero
// accounts existing; `AccountCreateForm`'s default (non-`dialog`) layout now stacks to one column
// below `sm` via `FormGrid` instead of just wrapping a flex row.
test.describe('onboarding', () => {
  test('shows the welcome screen and account fields without horizontal overflow', async ({
    page,
  }) => {
    await mockApi(page, [http.get('/api/accounts', () => HttpResponse.json([]))])
    await page.goto('/')

    await expect(page.getByRole('heading', { name: /welcome/i })).toBeVisible()
    await expect(page.getByLabel('Name', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create account' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('stacks the balance and date fields on mobile, side by side from sm up', async ({
    page,
  }, testInfo) => {
    await mockApi(page, [http.get('/api/accounts', () => HttpResponse.json([]))])
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /welcome/i })).toBeVisible()

    const balance = page.getByLabel('Opening Balance', { exact: true })
    const date = page.getByLabel('Opening Balance Date')
    await expect(balance).toBeVisible()
    await expect(date).toBeVisible()
    const [balanceBox, dateBox] = await Promise.all([balance.boundingBox(), date.boundingBox()])

    if (testInfo.project.name === 'mobile') {
      expect(dateBox!.y).toBeGreaterThan(balanceBox!.y + balanceBox!.height / 2)
    } else {
      expect(Math.abs(balanceBox!.y - dateBox!.y)).toBeLessThan(5)
    }
    await expectNoHorizontalOverflow(page)
  })
})
