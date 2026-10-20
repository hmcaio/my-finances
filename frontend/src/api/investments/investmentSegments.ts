import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'

/**
 * An investment segment (F026, ADR 0023): a flat taxonomy entry describing what kind of real
 * estate an FII holds (Shoppings, Logistica, Papel, ...). Orthogonal to the category/sub-category
 * taxonomy - generalized on `InvestmentProduct`, but only the FII page manages it for v1.
 *
 * F034/ADR 0028: read-only - every row is fixed, migration-seeded content (the nine B3 FII
 * sectors). create/rename/delete were removed from the backend and this client alike.
 */
export interface InvestmentSegment {
  id: string
  name: string
}

export async function getInvestmentSegments(): Promise<InvestmentSegment[]> {
  return unwrap(apiClient.get<InvestmentSegment[]>('/investment-segments'))
}
