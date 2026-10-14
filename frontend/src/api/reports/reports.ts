import type { AxiosResponse } from 'axios'
import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { NetWorthGranularity } from '../netWorth/netWorth'
import { today } from '../../utils/localDate'
import i18n from '../../i18n/i18n'

/** The eleven curated report types (F030 spec, PRD S6.9), replacing F013's all-entity export. */
export type ReportType =
  | 'TRANSACTIONS_TRANSFERS'
  | 'ACCOUNT_BALANCES'
  | 'FII_PORTFOLIO'
  | 'FUEL_HISTORY'
  | 'BUDGETS_SPENDING'
  | 'INVESTMENTS'
  | 'NET_WORTH_TREND'
  | 'RECURRING_TEMPLATES'
  | 'INVESTMENT_ALLOCATION'
  | 'DIVIDEND_INCOME'
  | 'ANNUAL_SUMMARY'

/** Which shared filter dimensions a report type actually reads - drives which controls show. */
export type ReportFilterDimension =
  'dateRange' | 'asOfDate' | 'account' | 'category' | 'vehicle' | 'month' | 'netWorthGranularity'

export interface ReportTypeInfo {
  type: ReportType
  /** `t()` key for the checkbox label (F033/ADR 0027 - was a literal English `label` string). */
  labelKey: string
  /** Matches backend `ReportType.slug()` - used to name a bare (non-zip) downloaded file. */
  slug: string
  dimensions: ReportFilterDimension[]
}

/** One entry per type, in the order the page lists them (PRD S6.9's own numbering). */
export const REPORT_TYPES: ReportTypeInfo[] = [
  {
    type: 'TRANSACTIONS_TRANSFERS',
    labelKey: 'reports:types.transactionsTransfers',
    slug: 'transactions-transfers',
    dimensions: ['dateRange', 'account', 'category'],
  },
  {
    type: 'ACCOUNT_BALANCES',
    labelKey: 'reports:types.accountBalances',
    slug: 'account-balances',
    dimensions: ['asOfDate', 'account'],
  },
  {
    type: 'FII_PORTFOLIO',
    labelKey: 'reports:types.fiiPortfolio',
    slug: 'fii-portfolio',
    dimensions: ['month'],
  },
  {
    type: 'FUEL_HISTORY',
    labelKey: 'reports:types.fuelHistory',
    slug: 'fuel-history',
    dimensions: ['dateRange', 'vehicle'],
  },
  {
    type: 'BUDGETS_SPENDING',
    labelKey: 'reports:types.budgetsSpending',
    slug: 'budgets-spending',
    dimensions: ['month'],
  },
  {
    type: 'INVESTMENTS',
    labelKey: 'reports:types.investments',
    slug: 'investments',
    dimensions: ['asOfDate', 'dateRange'],
  },
  {
    type: 'NET_WORTH_TREND',
    labelKey: 'reports:types.netWorthTrend',
    slug: 'net-worth-trend',
    dimensions: ['dateRange', 'netWorthGranularity'],
  },
  {
    type: 'RECURRING_TEMPLATES',
    labelKey: 'reports:types.recurringTemplates',
    slug: 'recurring-templates',
    dimensions: ['asOfDate'],
  },
  {
    type: 'INVESTMENT_ALLOCATION',
    labelKey: 'reports:types.investmentAllocation',
    slug: 'investment-allocation',
    dimensions: ['asOfDate', 'month'],
  },
  {
    type: 'DIVIDEND_INCOME',
    labelKey: 'reports:types.dividendIncome',
    slug: 'dividend-income',
    dimensions: ['dateRange'],
  },
  {
    type: 'ANNUAL_SUMMARY',
    labelKey: 'reports:types.annualSummary',
    slug: 'annual-summary',
    dimensions: ['month'],
  },
]

/**
 * Every optional filter dimension (F030 spec): each report type reads only the fields it has a
 * use for, same shape as the backend's `ReportFilter`. `month` doubles as the Annual Summary
 * report's year selector.
 */
export interface ReportFilter {
  dateFrom?: string
  dateTo?: string
  asOfDate?: string
  accountId?: string
  categoryId?: string
  vehicleId?: string
  month?: string
  netWorthGranularity?: NetWorthGranularity
}

/** Resolved via the `i18next` singleton, not the `useTranslation()` hook (same `apiError.ts`
 * pattern, frontend `CLAUDE.md`) - this module is plain data/HTTP, not a React component. */
export function reversedRangeMessage(): string {
  return i18n.t('reports:reversedRangeMessage')
}

/**
 * Generates and downloads the selected reports (F030 spec's `POST /api/reports`). Goes through
 * the shared Axios client (so `X-Request-Id` is sent) with an `arraybuffer` response (same
 * MSW-on-Node-22 reasoning F013's `export.ts` documented) - `Content-Disposition` isn't exposed to
 * the dev server's cross-origin requests, but `Content-Type` is a CORS-safelisted response header,
 * so the saved file's name/extension are derived from it instead of being duplicated here.
 */
export async function downloadReports(
  types: ReportType[],
  filter: ReportFilter = {},
): Promise<void> {
  const response = await unwrap<AxiosResponse<ArrayBuffer>>(
    apiClient
      .post<ArrayBuffer>('/reports', { types, filter }, { responseType: 'arraybuffer' })
      .then((res) => ({ data: res })),
  )
  const contentType = String(response.headers['content-type'] ?? '')
  const { extension, mimeType } = extensionFor(contentType)
  const fileName =
    extension === 'zip' || types.length !== 1
      ? `my-finances-reports-${today()}.zip`
      : `my-finances-${slugOf(types[0])}-${today()}.${extension}`
  saveBlob(new Blob([response.data], { type: mimeType }), fileName)
}

function slugOf(type: ReportType): string {
  return REPORT_TYPES.find((t) => t.type === type)?.slug ?? type.toLowerCase()
}

function extensionFor(contentType: string): { extension: string; mimeType: string } {
  if (contentType.includes('zip')) return { extension: 'zip', mimeType: 'application/zip' }
  if (contentType.includes('pdf')) return { extension: 'pdf', mimeType: 'application/pdf' }
  return { extension: 'csv', mimeType: 'text/csv' }
}

function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
