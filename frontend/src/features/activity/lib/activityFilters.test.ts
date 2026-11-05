import { describe, expect, it } from 'vitest'
import { ACTIONS, ENTITY_TYPES, ORIGINS, endOfDayIso, startOfDayIso } from './activityFilters'

describe('activity filter options', () => {
  it('lists every entity type, action and origin once', () => {
    expect(new Set(ENTITY_TYPES).size).toBe(ENTITY_TYPES.length)
    expect(ENTITY_TYPES).toHaveLength(17)
    expect(ENTITY_TYPES).toContain('INVESTMENT_OPENING_POSITION')
    expect(ACTIONS).toEqual([
      'CREATE',
      'UPDATE',
      'DELETE',
      'CLOSE',
      'REOPEN',
      'STOPPED',
      'GENERATED',
    ])
    expect(ORIGINS).toEqual(['USER', 'SYSTEM'])
  })
})

// The suite pins TZ to America/Sao_Paulo (UTC-3, no DST in 2026) in vite.config.ts.
describe('startOfDayIso / endOfDayIso', () => {
  it('turn a local date into the UTC instants bounding that local day', () => {
    expect(startOfDayIso('2026-03-16')).toBe('2026-03-16T03:00:00.000Z')
    expect(endOfDayIso('2026-03-16')).toBe('2026-03-17T02:59:59.999Z')
  })

  it('cross the UTC date line for a late local day', () => {
    expect(endOfDayIso('2026-12-31')).toBe('2027-01-01T02:59:59.999Z')
  })
})
