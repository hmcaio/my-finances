import { http, HttpResponse } from 'msw'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F021 (budgets/recurring batch): the recurring templates page (settings table plus the embedded
// "upcoming recurring bills" widget) at each viewport project (mobile 390, tablet 768, desktop
// 1280).
test.describe('recurring templates', () => {
  test('lists the mocked templates and pending occurrences without horizontal overflow', async ({
    page,
  }) => {
    await page.goto('/recurring')

    await expect(page.getByRole('heading', { name: 'Recurring Templates', level: 1 })).toBeVisible()
    await expect(page.getByText('Rent').first()).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Upcoming recurring bills' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows cards on mobile and a table from tablet up, for both lists', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/recurring')
    await expect(page.getByText('Rent').first()).toBeVisible()

    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 2)
    await expect(page.getByRole('list', { name: 'Recurring templates' })).toHaveCount(
      mobile ? 1 : 0,
    )
    await expect(page.getByRole('list', { name: 'Upcoming recurring bills' })).toHaveCount(
      mobile ? 1 : 0,
    )
  })

  test('reaches the hidden category through a row expander on tablet only, in both lists', async ({
    page,
  }, testInfo) => {
    const tablet = testInfo.project.name === 'tablet'
    await page.goto('/recurring')
    await expect(page.getByText('Rent').first()).toBeVisible()

    const toggles = page.getByRole('button', { name: 'Show details' })
    if (!tablet) {
      await expect(toggles).toHaveCount(0)
    } else {
      await expect(toggles.first()).toBeVisible()
      // Two tables, one hidden Category column each.
      await expect(toggles).toHaveCount(2)
      await expect(page.getByText('Groceries')).toHaveCount(0)
      await toggles.first().click()
      await expect(page.getByText('Groceries')).toBeVisible()
      await expectNoHorizontalOverflow(page)
    }
  })

  test('adds a template through the header dialog, full screen only on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/recurring')
    await expect(page.getByText('Rent').first()).toBeVisible()
    // No form panel below the list at any size: the fields only exist inside the dialog.
    await expect(page.getByRole('spinbutton', { name: 'Amount' })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add recurring template' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('spinbutton', { name: 'Amount' })).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('edits amount/day inline on tablet and desktop, in a full-screen dialog on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/recurring')
    await expect(page.getByText('Rent').first()).toBeVisible()

    const scope = mobile
      ? page.getByRole('listitem').filter({ hasText: 'Rent' })
      : page.getByRole('row', { name: /Rent/ })
    await scope.getByRole('button', { name: 'Edit amount and day' }).click()

    if (mobile) {
      const dialog = page.getByRole('dialog')
      await expect(dialog.getByRole('spinbutton', { name: 'Amount' })).toHaveValue('1500')
      expect((await dialog.boundingBox())?.width).toBeGreaterThanOrEqual(page.viewportSize()!.width)
    } else {
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await expect(scope.getByRole('spinbutton', { name: 'Amount' })).toHaveValue('1500')
    }
    await expectNoHorizontalOverflow(page)
  })

  test('the pending-occurrence confirm dialog fits the viewport, full screen only on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/recurring')
    await expect(page.getByText('Rent').first()).toBeVisible()

    // Only the pending-occurrences widget's row/card has a Confirm button (the settings table's
    // own "Rent" row only has Edit/Stop), so this resolves uniquely without indexing.
    const scope = mobile
      ? page.getByRole('listitem').filter({ hasText: 'Rent' })
      : page.getByRole('row', { name: /Rent/ })
    await scope.getByRole('button', { name: 'Confirm occurrence' }).click()

    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('spinbutton', { name: 'Amount' })).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('shows empty states for both lists without overflow', async ({ page }) => {
    await mockApi(page, [
      http.get('/api/recurring-templates', () => HttpResponse.json([])),
      http.get('/api/recurring-templates/pending', () => HttpResponse.json([])),
    ])
    await page.goto('/recurring')

    await expect(page.getByText('No recurring templates yet.')).toBeVisible()
    await expect(page.getByText('Nothing pending right now.')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
