import { describe, expect, it } from 'vitest'
import type { Category } from '../../api/categories/categories'
import { sortCategories } from './sortCategories'

function category(name: string, type: Category['type'], builtIn = false): Category {
  return { id: name, name, type, builtIn, fuelCategory: false, dividendCategory: false }
}

describe('sortCategories', () => {
  it('puts expenses before income, the built-in row first within a type, then names ignoring case', () => {
    const sorted = sortCategories([
      category('salary', 'INCOME'),
      category('Rent', 'EXPENSE'),
      category('Other Income', 'INCOME', true),
      category('dining', 'EXPENSE'),
      category('Other Expense', 'EXPENSE', true),
      category('Bonus', 'INCOME'),
    ])

    expect(sorted.map((c) => c.name)).toEqual([
      'Other Expense',
      'dining',
      'Rent',
      'Other Income',
      'Bonus',
      'salary',
    ])
  })

  it('keeps the built-in row first whatever it is renamed to, and does not mutate its input', () => {
    const input = [category('Alpha', 'EXPENSE'), category('Zeta (renamed)', 'EXPENSE', true)]

    const sorted = sortCategories(input)

    expect(sorted[0].name).toBe('Zeta (renamed)')
    expect(input[0].name).toBe('Alpha')
  })
})
