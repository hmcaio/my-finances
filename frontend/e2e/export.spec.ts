import { expect, expectNoHorizontalOverflow, test } from './support'

// F021 (last PR 4 batch): the Export page's filters at each viewport project (mobile 390, tablet
// 768, desktop 1280). Its four filters (From, To, Account, Category) are the same shape as
// Transactions'/Transfers' filter row, so they share `ResponsiveFilterBar` instead of the old
// standalone "Filters (optional)" panel.
test.describe('export', () => {
  test('shows the filters and Download action without horizontal overflow', async ({ page }) => {
    await page.goto('/export')

    await expect(page.getByRole('heading', { name: 'Export', level: 1 })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Download' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('collapses the filters behind a button on mobile, inline from tablet up', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/export')
    await expect(page.getByRole('button', { name: 'Download' })).toBeVisible()

    await expect(page.getByLabel('From')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('button', { name: 'Filters' })).toHaveCount(mobile ? 1 : 0)
  })

  test('the Filters sheet never hides the Download action on mobile', async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', 'only the mobile sheet hides the filters')
    await page.goto('/export')

    await page.getByRole('button', { name: 'Filters' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('From')).toBeVisible()
    const box = await dialog.boundingBox()
    expect(box?.width).toBeGreaterThanOrEqual(page.viewportSize()!.width)

    await page.getByRole('button', { name: 'Done' }).click()
    await expect(page.getByRole('button', { name: 'Download' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
