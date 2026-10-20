import { describe, expect, it } from 'vitest'
import type { Institution } from '../../api/institutions/institutions'
import { sortInstitutions } from './sortInstitutions'

function institution(name: string, builtIn = false): Institution {
  return { id: name, name, builtIn }
}

describe('sortInstitutions', () => {
  it('puts the built-in row first, then the rest by name ignoring case', () => {
    const sorted = sortInstitutions([
      institution('nubank'),
      institution('No institution', true),
      institution('Itau'),
    ])

    expect(sorted.map((i) => i.name)).toEqual(['No institution', 'Itau', 'nubank'])
  })

  it('keeps the built-in row first whatever it is renamed to, and does not mutate its input', () => {
    const input = [institution('Alpha'), institution('Zzz Sem instituicao', true)]

    const sorted = sortInstitutions(input)

    expect(sorted[0].name).toBe('Zzz Sem instituicao')
    expect(input[0].name).toBe('Alpha')
  })
})
