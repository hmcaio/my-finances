import { expect, expectNoHorizontalOverflow, test } from './support'

// F040: the snapshot sheet at 360px. Below `sm` its rows are cards (`ResponsiveTable`), each with
// a balance input a thumb can reach, instead of a five-column table that would overflow.
test.describe('investment snapshot sheet', () => {
  test('renders the rows as cards with a reachable balance input per card at 360px', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 360, height: 740 })
    await page.goto('/investments/snapshots')

    const list = page.getByRole('list', { name: 'Open holdings' })
    await expect(list).toBeVisible()
    await expect(page.getByRole('table')).toHaveCount(0)
    await page.waitForLoadState('networkidle')

    const cards = list.getByRole('listitem')
    expect(await cards.count(), 'one card per open holding').toBe(2)
    for (let i = 0; i < 2; i++) {
      const card = cards.nth(i)
      const input = card.getByRole('spinbutton')
      await expect(input).toBeVisible()
      const [cardBox, inputBox] = await Promise.all([card.boundingBox(), input.boundingBox()])
      expect(inputBox!.x, 'input starts inside the card').toBeGreaterThanOrEqual(cardBox!.x)
      expect(inputBox!.x + inputBox!.width, 'input ends inside the card').toBeLessThanOrEqual(
        cardBox!.x + cardBox!.width,
      )
      expect(inputBox!.x + inputBox!.width).toBeLessThanOrEqual(360)
      expect(inputBox!.height, 'a touch-sized input').toBeGreaterThanOrEqual(36)
    }
    await cards.nth(0).getByRole('spinbutton').fill('950')
    await expect(page.getByRole('button', { name: 'Save snapshots' })).toBeEnabled()
    await expectNoHorizontalOverflow(page)
  })
})
