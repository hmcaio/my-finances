import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { TransactionsRedirect, TransfersRedirect } from './LegacyRedirects'

function Where() {
  const { pathname, search, state } = useLocation()
  return (
    <div>
      <span data-testid="url">{`${pathname}${search}`}</span>
      <span data-testid="state">{JSON.stringify(state)}</span>
    </div>
  )
}

function renderAt(entry: string | { pathname: string; state: unknown }) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/transactions" element={<TransactionsRedirect />} />
        <Route path="/transfers" element={<TransfersRedirect />} />
        <Route path="/ledger" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('legacy redirects', () => {
  it('/transactions goes to /ledger', () => {
    renderAt('/transactions')

    expect(screen.getByTestId('url')).toHaveTextContent('/ledger')
  })

  it('/transactions keeps its query string', () => {
    renderAt('/transactions?x=1&y=2')

    expect(screen.getByTestId('url')).toHaveTextContent('/ledger?x=1&y=2')
  })

  it('/transactions keeps the navigation state (the Fuel page preset)', () => {
    renderAt({ pathname: '/transactions', state: { presetCategoryId: 'cat-fuel' } })

    expect(screen.getByTestId('state')).toHaveTextContent('{"presetCategoryId":"cat-fuel"}')
  })

  it('/transfers goes to /ledger with type=transfer', () => {
    renderAt('/transfers')

    expect(screen.getByTestId('url')).toHaveTextContent('/ledger?type=transfer')
  })

  it('/transfers?focus=ID goes to /ledger?type=transfer&focus=ID', () => {
    renderAt('/transfers?focus=trf-1')

    expect(screen.getByTestId('url')).toHaveTextContent('/ledger?type=transfer&focus=trf-1')
  })

  it('/transfers does not duplicate an existing type parameter', () => {
    renderAt('/transfers?type=income&focus=trf-1')

    expect(screen.getByTestId('url')).toHaveTextContent('/ledger?type=transfer&focus=trf-1')
  })
})
