import { describe, expect, it } from 'vitest'
import {
  buildRequest,
  checkBalance,
  differenceOf,
  rowCodesFromErrors,
  rowIssues,
} from './snapshotSheet'

const rows = [{ holdingId: 'h1' }, { holdingId: 'h2' }, { holdingId: 'h3' }, { holdingId: 'h4' }]

describe('checkBalance', () => {
  it('treats an empty or whitespace input as blank', () => {
    expect(checkBalance('')).toEqual({ kind: 'blank' })
    expect(checkBalance('   ')).toEqual({ kind: 'blank' })
  })

  it('accepts zero, whole numbers and up to two decimals', () => {
    expect(checkBalance('0')).toEqual({ kind: 'valid', value: 0 })
    expect(checkBalance('1200')).toEqual({ kind: 'valid', value: 1200 })
    expect(checkBalance('1200.5')).toEqual({ kind: 'valid', value: 1200.5 })
    expect(checkBalance('1200.55')).toEqual({ kind: 'valid', value: 1200.55 })
  })

  it('accepts a decimal comma', () => {
    expect(checkBalance('12,5')).toEqual({ kind: 'valid', value: 12.5 })
  })

  it('flags a negative balance with the code the backend would answer with', () => {
    expect(checkBalance('-0.01')).toEqual({ kind: 'invalid', code: 'positive-or-zero' })
    expect(checkBalance('-5')).toEqual({ kind: 'invalid', code: 'positive-or-zero' })
  })

  it('flags more than two decimals and more than seventeen integer digits', () => {
    expect(checkBalance('1.234')).toEqual({ kind: 'invalid', code: 'digits' })
    expect(checkBalance('123456789012345678')).toEqual({ kind: 'invalid', code: 'digits' })
    expect(checkBalance('12345678901234567').kind).toBe('valid')
  })

  it('flags text that is not a number', () => {
    expect(checkBalance('abc')).toEqual({ kind: 'invalid', code: 'digits' })
  })
})

describe('differenceOf', () => {
  it('is signed, to the cent', () => {
    expect(differenceOf(120.5, 100)).toBe(20.5)
    expect(differenceOf(80, 100)).toBe(-20)
    expect(differenceOf(0.3, 0.1)).toBe(0.2)
  })

  it('is null without a previous balance', () => {
    expect(differenceOf(10, null)).toBeNull()
  })
})

describe('rowIssues and buildRequest', () => {
  it('skips blank rows and sends only the filled ones, in row order', () => {
    const inputs = { h1: '10', h2: '', h3: '30.5' }

    expect(rowIssues(rows, inputs)).toEqual({})
    expect(buildRequest('2026-05-31', rows, inputs)).toEqual({
      date: '2026-05-31',
      entries: [
        { holdingId: 'h1', balance: 10 },
        { holdingId: 'h3', balance: 30.5 },
      ],
    })
  })

  it('has nothing to send while every row is blank', () => {
    expect(buildRequest('2026-05-31', rows, { h1: '', h2: ' ' })).toBeNull()
  })

  it('has nothing to send without a date', () => {
    expect(buildRequest('', rows, { h1: '10' })).toBeNull()
  })

  it('flags a bad row and refuses to build a request while one exists', () => {
    const inputs = { h1: '10', h2: '-1', h3: '1.234' }

    expect(rowIssues(rows, inputs)).toEqual({ h2: 'positive-or-zero', h3: 'digits' })
    expect(buildRequest('2026-05-31', rows, inputs)).toBeNull()
  })
})

describe('rowCodesFromErrors', () => {
  const sent = [
    { holdingId: 'h1', balance: 10 },
    { holdingId: 'h3', balance: 30 },
    { holdingId: 'h4', balance: 40 },
  ]

  it('maps indexes of the request back to rows even when blank rows sit between them', () => {
    // h2 was blank and never sent, so entries[1] is h3 and entries[2] is h4.
    const result = rowCodesFromErrors(
      [
        { field: 'entries[1].holdingId', code: 'investment-holding-not-found' },
        { field: 'entries[2].balance', code: 'positive-or-zero' },
      ],
      sent,
    )

    expect(result).toEqual({
      h3: ['investment-holding-not-found'],
      h4: ['positive-or-zero'],
    })
  })

  it('ignores entries without a row field and indexes beyond the request', () => {
    const result = rowCodesFromErrors(
      [
        { field: null, code: 'holding-ids-unique' },
        { field: 'date', code: 'not-null' },
        { field: 'entries[9].balance', code: 'digits' },
      ],
      sent,
    )

    expect(result).toEqual({})
  })

  it('collects several codes of one row once each', () => {
    const result = rowCodesFromErrors(
      [
        { field: 'entries[0].balance', code: 'digits' },
        { field: 'entries[0].balance', code: 'digits' },
        { field: 'entries[0].holdingId', code: 'not-null' },
      ],
      sent,
    )

    expect(result).toEqual({ h1: ['digits', 'not-null'] })
  })

  it('is empty without errors', () => {
    expect(rowCodesFromErrors(undefined, sent)).toEqual({})
  })
})
