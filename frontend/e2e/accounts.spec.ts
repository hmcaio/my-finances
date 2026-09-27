import { expect, expectNoHorizontalOverflow, test } from './support'

// F021 (accounts batch): the accounts list and the account detail route at each viewport project
// (mobile 390, tablet 768, desktop 1280).
test.describe('accounts', () => {
  test('lists the mocked accounts without horizontal overflow', async ({ page }) => {
    await page.goto('/accounts')

    await expect(page.getByRole('heading', { name: 'Accounts', level: 1 })).toBeVisible()
    await expect(page.getByText('Itau Checking')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows cards on mobile and a table from tablet up', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/accounts')
    await expect(page.getByText('Itau Checking')).toBeVisible()

    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('list', { name: 'Accounts' })).toHaveCount(mobile ? 1 : 0)
    await expect(page.getByRole('columnheader', { name: 'Type' })).toHaveCount(
      testInfo.project.name === 'desktop' ? 1 : 0,
    )
  })

  test('reaches the hidden type through a row expander on tablet only', async ({
    page,
  }, testInfo) => {
    const tablet = testInfo.project.name === 'tablet'
    await page.goto('/accounts')
    await expect(page.getByText('Itau Checking')).toBeVisible()

    const toggles = page.getByRole('button', { name: 'Show details' })
    if (!tablet) {
      await expect(toggles).toHaveCount(0)
    } else {
      const table = page.getByRole('table')
      await expect(table.getByText('Checking', { exact: true })).toHaveCount(0)
      await table
        .getByRole('row', { name: /Itau Checking/ })
        .getByRole('button', { name: 'Show details' })
        .click()
      await expect(table.getByText('Checking', { exact: true })).toBeVisible()
      await expectNoHorizontalOverflow(page)
    }
  })

  test('adds through the header dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/accounts')
    await expect(page.getByText('Itau Checking')).toBeVisible()
    // No form panel below the list at any size: the fields only exist inside the dialog.
    await expect(page.getByRole('textbox', { name: 'Name' })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add account' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('textbox', { name: 'Name' })).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('renames inline on tablet and desktop, in a full-screen dialog on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/accounts')
    await expect(page.getByText('Itau Checking')).toBeVisible()

    const scope = mobile
      ? page.getByRole('listitem').filter({ hasText: 'Itau Checking' })
      : page.getByRole('row', { name: /Itau Checking/ })
    await scope.getByRole('button', { name: 'Edit' }).click()

    if (mobile) {
      const dialog = page.getByRole('dialog')
      await expect(dialog.getByRole('textbox', { name: 'Name' })).toHaveValue('Itau Checking')
      expect((await dialog.boundingBox())?.width).toBeGreaterThanOrEqual(page.viewportSize()!.width)
    } else {
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await expect(scope.getByRole('textbox', { name: 'Name' })).toHaveValue('Itau Checking')
    }
    await expectNoHorizontalOverflow(page)
  })
})

test.describe('account detail', () => {
  test('stacks the summary and embedded lists without horizontal overflow', async ({ page }) => {
    await page.goto('/accounts/acct-1')

    await expect(page.getByRole('heading', { name: 'Itau Checking', level: 1 })).toBeVisible()
    await expect(page.getByText('Running balance')).toBeVisible()
    await expect(page.getByText('Weekly groceries')).toBeVisible()
    await expect(page.getByText('Credit card payment')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows the embedded lists as cards on mobile and tables from tablet up', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/accounts/acct-1')
    await expect(page.getByText('Weekly groceries')).toBeVisible()
    await expect(page.getByText('Credit card payment')).toBeVisible()

    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 2)
    await expect(page.getByRole('list', { name: 'Account transactions' })).toHaveCount(
      mobile ? 1 : 0,
    )
    await expect(page.getByRole('list', { name: 'Account transfers' })).toHaveCount(mobile ? 1 : 0)
  })
})
