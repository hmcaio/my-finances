import { http, HttpResponse } from 'msw'
import type {
  DividendRow,
  DividendTotalByMonth,
  DividendTotalByTicker,
} from '../../api/investments/fiiDividends'

const DIVIDENDS_URL = '/api/fii/dividends'

/**
 * Seed data returned by the default `GET /api/fii/dividends` handler below (F026, ADR 0023) - a
 * fixed fixture, same reasoning as `seedFiiPortfolio`/`seedFiiAllocation`.
 */
export const seedDividends: DividendRow[] = [
  {
    transactionId: 'txn-div-1',
    date: '2026-01-15',
    amount: 50,
    investmentHoldingId: 'iholding-knri11',
    productId: 'iprod-knri11',
    ticker: 'KNRI11',
    productName: 'Kinea Renda Imobiliaria',
    description: 'Dividend',
  },
  {
    transactionId: 'txn-div-2',
    date: '2026-02-15',
    amount: 30,
    investmentHoldingId: 'iholding-hglg11',
    productId: 'iprod-hglg11',
    ticker: 'HGLG11',
    productName: 'CSHG Logistica',
    description: 'Dividend',
  },
]

export const seedDividendTotalsByTicker: DividendTotalByTicker[] = [
  { productId: 'iprod-hglg11', ticker: 'HGLG11', amount: 30 },
  { productId: 'iprod-knri11', ticker: 'KNRI11', amount: 50 },
]

export const seedDividendTotalsByMonth: DividendTotalByMonth[] = [
  { month: '2026-01', amount: 50 },
  { month: '2026-02', amount: 30 },
]

export const fiiDividendsHandlers = [
  http.get(DIVIDENDS_URL, ({ request }) => {
    const url = new URL(request.url)
    const productId = url.searchParams.get('productId')
    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    const rows = seedDividends
      .filter((d) => !productId || d.productId === productId)
      .filter((d) => !from || d.date >= from)
      .filter((d) => !to || d.date <= to)
    return HttpResponse.json(rows)
  }),

  http.get(`${DIVIDENDS_URL}/totals`, ({ request }) => {
    const url = new URL(request.url)
    const groupBy = url.searchParams.get('groupBy')
    return HttpResponse.json(
      groupBy === 'MONTH' ? seedDividendTotalsByMonth : seedDividendTotalsByTicker,
    )
  }),
]
