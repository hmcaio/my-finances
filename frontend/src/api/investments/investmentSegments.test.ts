import { describe, expect, it } from 'vitest'
import { seedInvestmentSegments } from '../../mocks/handlers/investmentSegments'
import { getInvestmentSegments } from './investmentSegments'

/**
 * F034/ADR 0028: InvestmentSegment lost create/rename/delete - every row is fixed,
 * migration-seeded content. Only the read path is left to test.
 */
describe('investment segments API client', () => {
  it('getInvestmentSegments returns every seeded segment', async () => {
    const segments = await getInvestmentSegments()

    expect(segments).toEqual(seedInvestmentSegments)
  })
})
