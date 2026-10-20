import { http, HttpResponse } from 'msw'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F021 (budgets/recurring batch): the budgets page at each viewport project (mobile 390, tablet
// 768, desktop 1280).
test.describe('budgets', () => {
  test('lists the mocked budgets and report without horizontal overflow', async ({ page }) => {
    await page.goto('/budgets')

    await expect(page.getByRole('heading', { name: 'Budgets', level: 1 })).toBeVisible()
    await expect(page.getByText('Groceries').first()).toBeVisible()
    await expect(page.getByText(/620\.00 \/ 500\.00/)).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows cards on mobile and a table from tablet up', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/budgets')
    await expect(page.getByText('Groceries').first()).toBeVisible()

    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('list', { name: 'Budgets' })).toHaveCount(mobile ? 1 : 0)
    // All three columns fit at every size that keeps the table, so no row expander appears.
    await expect(page.getByRole('button', { name: 'Show details' })).toHaveCount(0)
  })

  test('adds through the header dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/budgets')
    await expect(page.getByText('Groceries').first()).toBeVisible()
    // No form panel below the list at any size: the fields only exist inside the dialog.
    await expect(page.getByRole('spinbutton', { name: 'Monthly cap' })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add budget' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('spinbutton', { name: 'Monthly cap' })).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('edits the cap inline on tablet and desktop, in a full-screen dialog on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/budgets')
    await expect(page.getByText('Groceries').first()).toBeVisible()

    const scope = mobile
      ? page.getByRole('listitem').filter({ hasText: 'Groceries' })
      : page.getByRole('row', { name: /Groceries/ })
    await scope.getByRole('button', { name: 'Edit cap' }).click()

    if (mobile) {
      const dialog = page.getByRole('dialog')
      await expect(dialog.getByRole('spinbutton', { name: 'Monthly cap' })).toHaveValue('500')
      expect((await dialog.boundingBox())?.width).toBeGreaterThanOrEqual(page.viewportSize()!.width)
    } else {
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await expect(scope.getByRole('spinbutton', { name: 'Monthly cap' })).toHaveValue('500')
    }
    await expectNoHorizontalOverflow(page)
  })

  test('stopping a budget opens a confirmation dialog that fits the viewport', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/budgets')
    await expect(page.getByText('Groceries').first()).toBeVisible()

    const scope = mobile
      ? page.getByRole('listitem').filter({ hasText: 'Groceries' })
      : page.getByRole('row', { name: /Groceries/ })
    await scope.getByRole('button', { name: 'Stop budget' }).click()

    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText(/Past months keep the cap they had/)).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('shows an empty state without overflow', async ({ page }) => {
    await mockApi(page, [
      http.get('/api/budgets', () => HttpResponse.json([])),
      http.get('/api/budgets/report', () => HttpResponse.json([])),
    ])
    await page.goto('/budgets')

    await expect(page.getByText('No budgets yet.')).toBeVisible()
    await expect(page.getByText('No active budgets for this month.')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
