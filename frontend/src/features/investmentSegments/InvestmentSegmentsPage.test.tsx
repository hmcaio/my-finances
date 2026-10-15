import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { seedInvestmentSegments } from '../../mocks/handlers/investmentSegments'
import { expectLoadStates } from '../../test/loadStates'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { InvestmentSegmentsPage } from './InvestmentSegmentsPage'

/**
 * F034/ADR 0028: InvestmentSegmentsPage lost every mutation affordance - every row is now fixed,
 * migration-seeded content (the nine B3 FII sectors). This replaces the old `describeSettingsPage`
 * CRUD contract with a plain read-only check plus the shared load-state cases.
 */
describe('InvestmentSegmentsPage', () => {
  it('renders the seeded segments with no add/rename/delete affordance', async () => {
    renderWithQueryClient(<InvestmentSegmentsPage />)

    for (const segment of seedInvestmentSegments) {
      expect(await screen.findByText(segment.name)).toBeInTheDocument()
    }
    expect(screen.queryByRole('button', { name: /add/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  expectLoadStates({
    render: () => renderWithQueryClient(<InvestmentSegmentsPage />),
    url: '/api/investment-segments',
    successBody: seedInvestmentSegments,
    loadedText: seedInvestmentSegments[0].name,
  })
})
