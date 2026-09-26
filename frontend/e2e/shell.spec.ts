import { expect, expectNavMode, test } from './support'

// F021 shell: permanent drawer from lg (1200) up, hamburger-opened temporary drawer below it.
test.describe('shell', () => {
  test('shows the nav mode of the viewport and keeps the dark-mode toggle reachable', async ({
    page,
  }, testInfo) => {
    const desktop = testInfo.project.name === 'desktop'
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()

    await expectNavMode(page, desktop ? 'permanent' : 'temporary')
    await expect(page.getByRole('button', { name: 'Open navigation' })).toHaveCount(desktop ? 0 : 1)
    await expect(page.getByRole('button', { name: 'Toggle dark mode' })).toBeVisible()
  })

  test('opens the drawer from the hamburger and closes it after navigating', async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name === 'desktop', 'the drawer is permanent on desktop')
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()

    await page.getByRole('button', { name: 'Open navigation' }).click()
    const accounts = page.getByRole('link', { name: 'Accounts' })
    await expect(accounts).toBeVisible()
    await expect(page.getByText('Settings', { exact: true })).toBeVisible()

    await accounts.click()

    await expect(page).toHaveURL(/\/accounts$/)
    await expect(accounts).toBeHidden()
  })
})
