import { describe, expect, it } from 'vitest'
import i18n from '../../i18n/i18n'
import { formatDiffValue } from './activityDiff'

const en = i18n.getFixedT('en', 'activity')
const ptBr = i18n.getFixedT('pt-BR', 'activity')

describe('formatDiffValue', () => {
  it('renders null and undefined as a dash', () => {
    expect(formatDiffValue(null, 'en', en)).toBe('—')
    expect(formatDiffValue(undefined, 'en', en)).toBe('—')
  })

  it('renders booleans as translated Yes/No', () => {
    expect(formatDiffValue(true, 'en', en)).toBe('Yes')
    expect(formatDiffValue(false, 'en', en)).toBe('No')
    expect(formatDiffValue(true, 'pt-BR', ptBr)).toBe('Sim')
    expect(formatDiffValue(false, 'pt-BR', ptBr)).toBe('Não')
  })

  it('renders integers bare and other numbers with two decimals in the locale', () => {
    expect(formatDiffValue(1234, 'en', en)).toBe('1234')
    expect(formatDiffValue(1234.5, 'en', en)).toBe('1,234.50')
    expect(formatDiffValue(1234.5, 'pt-BR', ptBr)).toBe('1.234,50')
  })

  it('renders a resolved reference as "label (id)" and falls back to the bare id without a label', () => {
    expect(formatDiffValue({ id: 'abc', label: 'Nubank' }, 'en', en)).toBe('Nubank (abc)')
    expect(formatDiffValue({ id: 'abc', label: null }, 'en', en)).toBe('abc')
    expect(formatDiffValue({ id: 'abc', label: '' }, 'en', en)).toBe('abc')
  })

  it('renders any other object as JSON, including one whose id is not a string', () => {
    expect(formatDiffValue({ a: 1 }, 'en', en)).toBe('{"a":1}')
    expect(formatDiffValue({ id: 5, label: 'x' }, 'en', en)).toBe('{"id":5,"label":"x"}')
    expect(formatDiffValue([1, 2], 'en', en)).toBe('[1,2]')
  })

  it('renders strings as-is', () => {
    expect(formatDiffValue('2026-03-01', 'en', en)).toBe('2026-03-01')
  })
})
