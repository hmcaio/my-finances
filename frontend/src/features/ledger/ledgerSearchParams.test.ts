import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  activeFilterCount,
  isAllTime,
  parseLedgerView,
  serializeLedgerView,
  toLedgerFilter,
  type LedgerView,
} from './ledgerSearchParams'

// A non-UTC zone is pinned so a UTC-vs-local bug in the default month fails here too; only Date
// is faked, timers keep running.
beforeEach(() => {
  vi.stubEnv('TZ', 'America/Sao_Paulo')
  vi.useFakeTimers({ toFake: ['Date'] })
  // 23:30 on 31 March in UTC-3 is already April in UTC.
  vi.setSystemTime(new Date(2026, 2, 31, 23, 30))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllEnvs()
})

const parse = (query: string) => parseLedgerView(new URLSearchParams(query))

const blank: LedgerView = {
  scope: 'month',
  month: '2026-03',
  from: '',
  to: '',
  type: '',
  account: '',
  category: '',
  paymentMethod: '',
  q: '',
  focus: '',
}

describe('parseLedgerView', () => {
  it('defaults to the current local month with nothing filtered', () => {
    expect(parse('')).toEqual(blank)
  })

  it('reads a month', () => {
    expect(parse('month=2026-09').month).toBe('2026-09')
  })

  it('drops an invalid month and falls back to the current one', () => {
    for (const bad of ['2026-13', '2026-00', '26-01', '2026-1', 'march', '2026-01-05', '']) {
      expect(parse(`month=${bad}`).month).toBe('2026-03')
    }
  })

  it('allows a future month', () => {
    expect(parse('month=2031-12')).toMatchObject({ scope: 'month', month: '2031-12' })
  })

  it('reads a custom range and ignores month while scope=range', () => {
    expect(parse('scope=range&from=2026-01-05&to=2026-02-10&month=2026-09')).toMatchObject({
      scope: 'range',
      from: '2026-01-05',
      to: '2026-02-10',
    })
  })

  it('treats range mode with empty or missing bounds as all time', () => {
    expect(isAllTime(parse('scope=range'))).toBe(true)
    expect(isAllTime(parse('scope=range&from=&to='))).toBe(true)
    expect(isAllTime(parse('scope=range&from=2026-01-01'))).toBe(false)
    expect(isAllTime(parse('month=2026-01'))).toBe(false)
  })

  it('drops invalid dates silently, keeping the valid bound', () => {
    const view = parse('scope=range&from=2026-02-30&to=2026-03-15')

    expect(view.from).toBe('')
    expect(view.to).toBe('2026-03-15')
    expect(parse('scope=range&from=yesterday').from).toBe('')
  })

  it('maps the lower-case type and drops unknown ones', () => {
    expect(parse('type=income').type).toBe('income')
    expect(parse('type=expense').type).toBe('expense')
    expect(parse('type=transfer').type).toBe('transfer')
    expect(parse('type=INCOME').type).toBe('')
    expect(parse('type=bogus').type).toBe('')
  })

  it('reads the account, category, payment method, search text and focus', () => {
    expect(parse('account=a1&category=c1&paymentMethod=p1&q=caf%C3%A9&focus=t1')).toMatchObject({
      account: 'a1',
      category: 'c1',
      paymentMethod: 'p1',
      q: 'café',
      focus: 't1',
    })
  })

  it('drops a category and payment method next to type=transfer', () => {
    const view = parse('type=transfer&category=c1&paymentMethod=p1&account=a1')

    expect(view).toMatchObject({ type: 'transfer', category: '', paymentMethod: '', account: 'a1' })
  })

  it('truncates search text to the 100-character backend limit', () => {
    expect(parse(`q=${'a'.repeat(150)}`).q).toHaveLength(100)
  })
})

describe('serializeLedgerView', () => {
  it('writes only the month for an unfiltered month view', () => {
    expect(serializeLedgerView(blank).toString()).toBe('month=2026-03')
  })

  it('writes scope and the non-empty bounds in range mode, never the month', () => {
    const view = { ...blank, scope: 'range' as const, from: '2026-01-01', to: '' }

    expect(serializeLedgerView(view).toString()).toBe('scope=range&from=2026-01-01')
    expect(serializeLedgerView({ ...view, from: '' }).toString()).toBe('scope=range')
  })

  it('writes the filters, omitting empty ones', () => {
    const view = { ...blank, type: 'expense' as const, account: 'a1', category: 'c1', q: 'café' }

    expect(serializeLedgerView(view).toString()).toBe(
      'month=2026-03&type=expense&account=a1&category=c1&q=caf%C3%A9',
    )
  })

  it('never writes a category or payment method for type=transfer', () => {
    const view = { ...blank, type: 'transfer' as const, category: 'c1', paymentMethod: 'p1' }

    expect(serializeLedgerView(view).toString()).toBe('month=2026-03&type=transfer')
  })

  it('round-trips through parse', () => {
    const view: LedgerView = {
      ...blank,
      scope: 'range',
      from: '2025-12-01',
      to: '2026-01-31',
      type: 'income',
      account: 'a1',
      paymentMethod: 'p9',
      q: '50% off',
      focus: 'trf-1',
    }

    expect(parseLedgerView(serializeLedgerView(view))).toEqual(view)
  })
})

describe('toLedgerFilter', () => {
  it('turns a month into its first and last date', () => {
    expect(toLedgerFilter({ ...blank, month: '2028-02' })).toEqual({
      dateFrom: '2028-02-01',
      dateTo: '2028-02-29',
    })
  })

  it('sends only the set bounds in range mode, none for all time', () => {
    const range = { ...blank, scope: 'range' as const }

    expect(toLedgerFilter(range)).toEqual({})
    expect(toLedgerFilter({ ...range, to: '2026-05-01' })).toEqual({ dateTo: '2026-05-01' })
  })

  it('maps the type to the API enum and the other filters to their ids', () => {
    const filter = toLedgerFilter({
      ...blank,
      type: 'expense',
      account: 'a1',
      category: 'c1',
      paymentMethod: 'p1',
      q: '  café ',
    })

    expect(filter).toEqual({
      dateFrom: '2026-03-01',
      dateTo: '2026-03-31',
      type: 'EXPENSE',
      accountId: 'a1',
      categoryId: 'c1',
      paymentMethodId: 'p1',
      q: 'café',
    })
  })

  it('omits a blank search', () => {
    expect(toLedgerFilter({ ...blank, q: '   ' })).not.toHaveProperty('q')
  })
})

describe('activeFilterCount', () => {
  it('counts type, account, category, payment method and search, not the period', () => {
    expect(activeFilterCount(blank)).toBe(0)
    expect(
      activeFilterCount({ ...blank, type: 'income', account: 'a', category: 'c', q: 'x' }),
    ).toBe(4)
    expect(activeFilterCount({ ...blank, scope: 'range', from: '2026-01-01' })).toBe(0)
  })
})
