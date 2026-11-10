/** The Investments page's sub-tabs (F009, F023). */
export type InvestmentsTab = 'accounts' | 'products'

/** The tab shown when the URL names none; it is left out of the query string. */
const DEFAULT_TAB: InvestmentsTab = 'accounts'

/**
 * Reads the tab from the URL (F040), so a reload or a shared link opens the same one. Only the exact
 * value `products` selects Products: anything else, a hand-edited value included, is Accounts.
 */
export function parseInvestmentsTab(params: URLSearchParams): InvestmentsTab {
  return params.get('tab') === 'products' ? 'products' : DEFAULT_TAB
}

/** The query string for `tab`: empty for the default tab, `tab=products` otherwise. */
export function serializeInvestmentsTab(tab: InvestmentsTab): URLSearchParams {
  const params = new URLSearchParams()
  if (tab !== DEFAULT_TAB) params.set('tab', tab)
  return params
}
