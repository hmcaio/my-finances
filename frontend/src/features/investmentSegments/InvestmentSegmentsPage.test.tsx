import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import i18n from '../../i18n/i18n'
import { seedInvestmentSegments } from '../../mocks/handlers/investmentSegments'
import { translateSeedName } from '../../i18n/seedNames'
import { expectLoadStates } from '../../test/loadStates'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { InvestmentSegmentsPage } from './InvestmentSegmentsPage'

/**
 * F034/ADR 0028: InvestmentSegmentsPage lost every mutation affordance - every row is now fixed,
 * migration-seeded content (the B3 FII sectors). This replaces the old `describeSettingsPage`
 * CRUD contract with a plain read-only check plus the shared load-state cases.
 *
 * Unlike the other taxonomy fixtures (category/payment-method/institution seed names, which are
 * already English words), the segment seed names are Portuguese market jargon, so their English
 * translation genuinely differs from the raw literal ("Shoppings" -> "Shopping Malls") - resolved
 * against a fixed `en` `t()` (`i18n.getFixedT`, same pattern as `test/errorMessages.ts`), not the
 * live active language, since this runs at module/describe-body evaluation time.
 */
const t = i18n.getFixedT('en')

describe('InvestmentSegmentsPage', () => {
  it('renders the seeded segments, translated, with no add/rename/delete affordance', async () => {
    renderWithQueryClient(<InvestmentSegmentsPage />)

    for (const segment of seedInvestmentSegments) {
      const translated = translateSeedName('investmentSegments', segment.name, t)
      expect(await screen.findByText(translated)).toBeInTheDocument()
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
    loadedText: translateSeedName('investmentSegments', seedInvestmentSegments[0].name, t),
  })
})
