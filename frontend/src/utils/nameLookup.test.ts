import { describe, expect, it } from 'vitest'
import { nameLookup } from './nameLookup'

describe('nameLookup', () => {
  it('resolves an id to the name returned by getName', () => {
    const lookup = nameLookup(
      [
        { id: 'cat-1', name: 'Groceries' },
        { id: 'cat-2', name: 'Salary' },
      ],
      (item) => item.name,
    )

    expect(lookup('cat-1')).toBe('Groceries')
    expect(lookup('cat-2')).toBe('Salary')
  })

  it('falls back to the id itself when it is not found', () => {
    const lookup = nameLookup([{ id: 'cat-1', name: 'Groceries' }], (item) => item.name)

    expect(lookup('cat-missing')).toBe('cat-missing')
  })

  it('falls back to the id for every lookup against an empty list', () => {
    const lookup = nameLookup<{ id: string; name: string }>([], (item) => item.name)

    expect(lookup('anything')).toBe('anything')
  })
})
