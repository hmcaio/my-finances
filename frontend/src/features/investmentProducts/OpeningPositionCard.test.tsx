import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import { server } from '../../mocks/server'
import { seedInvestmentHoldings } from '../../mocks/handlers/investmentHoldings'
import {
  investmentOpeningPositionsStore,
  openingPositionAfterTradesHandler,
  seedOpeningPosition,
} from '../../mocks/handlers/investmentOpeningPositions'
import { investmentSnapshotsStore } from '../../mocks/handlers/investmentSnapshots'
import { resolveErrorMessage } from '../../test/errorMessages'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { OpeningPositionCard } from './OpeningPositionCard'

const open = seedInvestmentHoldings.find((h) => h.id === 'iholding-selic')!
const closed = seedInvestmentHoldings.find((h) => h.id === 'iholding-old')!

function renderCard(holding: InvestmentHolding = open) {
  renderWithQueryClient(<OpeningPositionCard holding={holding} padding={{ xs: 2, sm: 3 }} />)
  return userEvent.setup({ delay: null })
}

const addButton = () => screen.findByRole('button', { name: 'Add opening position' })

describe('OpeningPositionCard', () => {
  it('shows the position: as-of date, cotas, average price and the contributed amount', async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)
    renderCard()

    expect(await screen.findByText('July 1, 2026')).toBeInTheDocument()
    expect(screen.getByText('40')).toBeInTheDocument()
    expect(screen.getByText('150.00')).toBeInTheDocument()
    expect(screen.getByText('6,000.00')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit opening position' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add opening position' })).not.toBeInTheDocument()
  })

  it('shows the empty state with an add action when the holding has no position', async () => {
    renderCard()

    expect(await addButton()).toBeInTheDocument()
    expect(screen.getByText(/No opening position recorded/)).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Delete opening position' }),
    ).not.toBeInTheDocument()
  })

  it('adds a position with a current value, which also records the snapshot', async () => {
    const user = renderCard()
    await user.click(await addButton())

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled()
    await user.clear(within(dialog).getByLabelText('As of'))
    await user.type(within(dialog).getByLabelText('As of'), '2026-07-01')
    await user.type(within(dialog).getByLabelText('Cotas'), '40')
    await user.type(within(dialog).getByLabelText('Average price'), '150')
    await user.type(within(dialog).getByLabelText('Current value (optional)'), '6400')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('6,000.00')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(investmentSnapshotsStore.list()).toContainEqual(
      expect.objectContaining({ holdingId: 'iholding-selic', date: '2026-07-01', balance: 6400 }),
    )
  })

  it('keeps Save disabled until cotas and average price are positive', async () => {
    const user = renderCard()
    await user.click(await addButton())
    const dialog = await screen.findByRole('dialog')
    const save = within(dialog).getByRole('button', { name: 'Save' })

    await user.type(within(dialog).getByLabelText('Cotas'), '0')
    await user.type(within(dialog).getByLabelText('Average price'), '10')
    expect(save).toBeDisabled()

    await user.clear(within(dialog).getByLabelText('Cotas'))
    await user.type(within(dialog).getByLabelText('Cotas'), '5')
    expect(save).toBeEnabled()
  })

  it('edits the quantity of the existing position', async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)
    const user = renderCard()
    await user.click(await screen.findByRole('button', { name: 'Edit opening position' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('Cotas')).toHaveValue(40)
    await user.clear(within(dialog).getByLabelText('Cotas'))
    await user.type(within(dialog).getByLabelText('Cotas'), '50')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('7,500.00')).toBeInTheDocument()
    expect(investmentOpeningPositionsStore.list()).toHaveLength(1)
  })

  it('deletes the position after a confirmation and returns to the empty state', async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)
    const user = renderCard()
    await user.click(await screen.findByRole('button', { name: 'Delete opening position' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Snapshots are kept/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await addButton()).toBeInTheDocument()
    expect(investmentOpeningPositionsStore.list()).toHaveLength(0)
  })

  it('cancelling the delete confirmation keeps the position', async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)
    const user = renderCard()
    await user.click(await screen.findByRole('button', { name: 'Delete opening position' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    )

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(investmentOpeningPositionsStore.list()).toHaveLength(1)
  })

  it('hides every action on a closed holding but still shows its position', async () => {
    investmentOpeningPositionsStore.add({ ...seedOpeningPosition, holdingId: closed.id })
    renderCard(closed)

    expect(await screen.findByText('6,000.00')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit opening position' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Delete opening position' }),
    ).not.toBeInTheDocument()
  })

  it('hides the add action on a closed holding with no position', async () => {
    renderCard(closed)

    expect(await screen.findByText(/No opening position recorded/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add opening position' })).not.toBeInTheDocument()
  })

  it("shows the server's 409 for a date after the earliest trade inside the dialog", async () => {
    server.use(openingPositionAfterTradesHandler)
    const user = renderCard()
    await user.click(await addButton())
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Cotas'), '40')
    await user.type(within(dialog).getByLabelText('Average price'), '150')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(
      await within(dialog).findByText(
        resolveErrorMessage('urn:my-finances:error:opening-position-after-trades'),
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it("shows the server's 409 for a closed holding on delete", async () => {
    investmentOpeningPositionsStore.add(seedOpeningPosition)
    server.use(
      http.delete('/api/investment-holdings/:holdingId/opening-position', () =>
        HttpResponse.json(
          { type: 'urn:my-finances:error:investment-holding-closed' },
          { status: 409 },
        ),
      ),
    )
    const user = renderCard()
    await user.click(await screen.findByRole('button', { name: 'Delete opening position' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }),
    )

    expect(
      await screen.findByText(
        resolveErrorMessage('urn:my-finances:error:investment-holding-closed'),
      ),
    ).toBeInTheDocument()
  })

  it('shows a load failure with a retry instead of the empty state', async () => {
    server.use(
      http.get('/api/investment-holdings/:holdingId/opening-position', () =>
        HttpResponse.json({ type: 'urn:my-finances:error:internal-error' }, { status: 500 }),
      ),
    )
    renderCard()

    expect(await screen.findByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add opening position' })).not.toBeInTheDocument()
  })
})
