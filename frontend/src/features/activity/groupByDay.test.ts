import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuditLogEntry } from '../../api/auditLog/auditLog'
import { groupEntriesByLocalDay } from './groupByDay'

function entry(id: string, occurredAt: string): AuditLogEntry {
  return {
    id,
    occurredAt,
    entityType: 'CATEGORY',
    entityId: 'cat-1',
    entityLabel: 'Groceries',
    action: 'CREATE',
    origin: 'USER',
    changes: {},
    requestId: null,
  }
}

describe('groupEntriesByLocalDay', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('groups consecutive same-day entries together, newest first', () => {
    const entries = [
      entry('a3', '2026-03-16T14:30:00Z'),
      entry('a2', '2026-03-16T12:00:00Z'),
      entry('a1', '2026-03-15T09:15:00Z'),
    ]

    const groups = groupEntriesByLocalDay(entries)

    expect(groups).toHaveLength(2)
    expect(groups[0].entries.map((e) => e.id)).toEqual(['a3', 'a2'])
    expect(groups[1].entries.map((e) => e.id)).toEqual(['a1'])
  })

  it('labels each group with a human-readable date', () => {
    const groups = groupEntriesByLocalDay([entry('a1', '2026-03-16T14:30:00Z')])

    expect(groups[0].label).toContain('2026')
    expect(groups[0].label).toContain('March')
  })

  it('groups an entry near UTC midnight onto the viewer local day, not the UTC day', () => {
    // Pinned to a non-UTC zone (tests default to America/Sao_Paulo per vite.config.ts; pinning
    // explicitly here, per frontend CLAUDE.md's convention for a test whose correctness depends on
    // the zone) - 2026-03-16T02:00:00Z is 2026-03-15 23:00 in UTC-3, a full day earlier.
    vi.stubEnv('TZ', 'America/Sao_Paulo')

    const groups = groupEntriesByLocalDay([entry('a1', '2026-03-16T02:00:00Z')])

    expect(groups[0].dateKey).toBe('2026-03-15')
  })

  it('returns no groups for an empty page', () => {
    expect(groupEntriesByLocalDay([])).toEqual([])
  })
})
