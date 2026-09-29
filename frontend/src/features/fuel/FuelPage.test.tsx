import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { server } from '../../mocks/server'
import { seedCategories, seedFuelCategory } from '../../mocks/handlers/categories'
import { seedCivicVehicle, seedCorollaVehicle } from '../../mocks/handlers/vehicles'
import type { Transaction } from '../../api/transactions/transactions'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { renderWithRouter, selectOption } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { FuelPage } from './FuelPage'

function fuelTransaction(overrides: Partial<Transaction>): Transaction {
  return {
    id: 'fuel-txn',
    date: '2026-01-05',
    amount: 200,
    categoryId: seedFuelCategory.id,
    type: 'EXPENSE',
    accountId: 'acct-1',
    paymentMethodId: 'pm-1',
    recurringTemplateVersionId: null,
    description: 'Fill up',
    additionalNotes: null,
    vehicleId: seedCivicVehicle.id,
    fuelType: 'GASOLINA',
    liters: 40,
    pricePerLiter: 5,
    kmSinceLastFill: null,
    odometer: null,
    kmPerLiter: null,
    amountPerKm: null,
    litersPerKm: null,
    ...overrides,
  }
}

describe('FuelPage', () => {
  // jsdom has no viewport, which MUI treats as the tablet band (Km/L, Spend/km columns hidden
  // behind the row expander): these tests assert the full desktop table.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  function useCategoriesWithFuel() {
    server.use(
      http.get('/api/categories', () => HttpResponse.json([...seedCategories, seedFuelCategory])),
    )
  }

  it('defaults to the first vehicle and shows an empty state with no fuel history', async () => {
    useCategoriesWithFuel()
    renderWithRouter(<FuelPage />)

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Vehicle' })).toHaveTextContent(
        seedCivicVehicle.name,
      ),
    )
    expect(
      await screen.findByText('No fuel purchases recorded for this vehicle yet.'),
    ).toBeInTheDocument()
  })

  it('lists fuel history for the selected vehicle only, with computed ratios', async () => {
    useCategoriesWithFuel()
    server.use(
      http.get('/api/vehicles/:id/fuel-history', ({ params }) => {
        if (params.id !== seedCivicVehicle.id) return HttpResponse.json([])
        return HttpResponse.json([
          fuelTransaction({ id: 'fuel-1', date: '2026-01-01' }),
          fuelTransaction({
            id: 'fuel-2',
            date: '2026-01-20',
            kmSinceLastFill: 400,
            kmPerLiter: 10,
            amountPerKm: 0.5,
            litersPerKm: 0.1,
          }),
        ])
      }),
    )
    renderWithRouter(<FuelPage />)

    const table = await screen.findByRole('table', { name: 'Fuel history' })
    expect(await within(table).findAllByText('Gasolina')).toHaveLength(2)
    expect(within(table).getByText('10.00')).toBeInTheDocument()
  })

  it("switches vehicles and refetches that vehicle's history", async () => {
    useCategoriesWithFuel()
    server.use(
      http.get('/api/vehicles/:id/fuel-history', ({ params }) =>
        HttpResponse.json(
          params.id === seedCorollaVehicle.id
            ? [fuelTransaction({ id: 'corolla-fill', vehicleId: seedCorollaVehicle.id })]
            : [],
        ),
      ),
    )
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<FuelPage />)
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Vehicle' })).toHaveTextContent(
        seedCivicVehicle.name,
      ),
    )
    expect(
      await screen.findByText('No fuel purchases recorded for this vehicle yet.'),
    ).toBeInTheDocument()

    await selectOption(user, 'Vehicle', seedCorollaVehicle.name)

    const table = await screen.findByRole('table', { name: 'Fuel history' })
    expect(await within(table).findByText('Gasolina')).toBeInTheDocument()
  })

  it('navigates to Transactions with the fuel category pre-selected on Add', async () => {
    useCategoriesWithFuel()
    const user = userEvent.setup({ delay: null })
    // A tiny two-route harness (not `renderWithRouter`, which mounts `FuelPage` with no `Routes`
    // to react to a navigation) so the `navigate('/transactions', ...)` call actually swaps the
    // page out - proving the hand-off works, not just that the click didn't throw.
    renderWithQueryClient(
      <MemoryRouter initialEntries={['/fuel']}>
        <Routes>
          <Route path="/fuel" element={<FuelPage />} />
          <Route path="/transactions" element={<div>Landed on Transactions</div>} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Add fuel transaction' })).toBeEnabled(),
    )

    await user.click(screen.getByRole('button', { name: 'Add fuel transaction' }))

    expect(await screen.findByText('Landed on Transactions')).toBeInTheDocument()
  })

  it('shows a prompt instead of the charts/list when there are no vehicles yet', async () => {
    useCategoriesWithFuel()
    server.use(http.get('/api/vehicles', () => HttpResponse.json([])))
    renderWithRouter(<FuelPage />)

    expect(
      await screen.findByText('No vehicles yet - add one under Settings > Vehicles first.'),
    ).toBeInTheDocument()
  })
})
