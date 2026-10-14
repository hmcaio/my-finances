export type SeedTable =
  | 'categories'
  | 'paymentMethods'
  | 'institutions'
  | 'investmentCategories'
  | 'investmentSubcategories'

/**
 * Exact-string-match seed-name lookups (F033/ADR 0027): a row whose *current* name matches one of
 * a migration's literal seeded strings renders translated; a row that doesn't match (the user
 * renamed it, or it's their own data) renders verbatim. No schema change, no `built_in`-flag
 * dependency - this degrades gracefully the moment a seeded row is renamed, which is the point.
 *
 * `categories` holds V2's starter names as adopted/renamed by V14 ("Other" -> "Other Expense",
 * unless an income row already held that name - see V14's own migration comment for the adoption
 * rules), since that's the name a fresh install actually ends up with. V18's "Fuel" and V20's
 * "Dividends" are added by the fuel/FII feature areas' own phase steps, not here, even though they
 * also seed into this same `categories` table.
 */
const CATEGORY_SEED_NAMES: Record<string, string> = {
  Groceries: 'common:seedNames.categories.groceries',
  Rent: 'common:seedNames.categories.rent',
  Utilities: 'common:seedNames.categories.utilities',
  Subscriptions: 'common:seedNames.categories.subscriptions',
  Transport: 'common:seedNames.categories.transport',
  Dining: 'common:seedNames.categories.dining',
  Health: 'common:seedNames.categories.health',
  'Other Expense': 'common:seedNames.categories.otherExpense',
  Salary: 'common:seedNames.categories.salary',
  'Other Income': 'common:seedNames.categories.otherIncome',
}

/** V2's starter payment methods. */
const PAYMENT_METHOD_SEED_NAMES: Record<string, string> = {
  'Debit Card': 'common:seedNames.paymentMethods.debitCard',
  'Credit Card': 'common:seedNames.paymentMethods.creditCard',
  PIX: 'common:seedNames.paymentMethods.pix',
  Cash: 'common:seedNames.paymentMethods.cash',
}

/** V12's single built-in institution. */
const INSTITUTION_SEED_NAMES: Record<string, string> = {
  'No institution': 'common:seedNames.institutions.noInstitution',
}

/** V13's starter investment categories. */
const INVESTMENT_CATEGORY_SEED_NAMES: Record<string, string> = {
  'Fixed Income': 'common:seedNames.investmentCategories.fixedIncome',
  'Variable Income': 'common:seedNames.investmentCategories.variableIncome',
  Funds: 'common:seedNames.investmentCategories.funds',
  'Pension (Previdência)': 'common:seedNames.investmentCategories.pension',
  International: 'common:seedNames.investmentCategories.international',
  Crypto: 'common:seedNames.investmentCategories.crypto',
  Other: 'common:seedNames.investmentCategories.other',
}

/**
 * V13's starter investment sub-categories (name unique per category, not globally - "ETFs" exists
 * under both Variable Income and International, which is why this is one flat lookup rather than
 * nested per category: both translate to the same word, so there is no collision in practice).
 */
const INVESTMENT_SUBCATEGORY_SEED_NAMES: Record<string, string> = {
  'Tesouro Selic': 'common:seedNames.investmentSubcategories.tesouroSelic',
  'Tesouro IPCA+': 'common:seedNames.investmentSubcategories.tesouroIpca',
  'Tesouro Prefixado': 'common:seedNames.investmentSubcategories.tesouroPrefixado',
  CDB: 'common:seedNames.investmentSubcategories.cdb',
  LCI: 'common:seedNames.investmentSubcategories.lci',
  LCA: 'common:seedNames.investmentSubcategories.lca',
  CRI: 'common:seedNames.investmentSubcategories.cri',
  CRA: 'common:seedNames.investmentSubcategories.cra',
  Debentures: 'common:seedNames.investmentSubcategories.debentures',
  'Savings (Poupança)': 'common:seedNames.investmentSubcategories.savings',
  'Stocks (Ações)': 'common:seedNames.investmentSubcategories.stocksAcoes',
  'REITs (FIIs)': 'common:seedNames.investmentSubcategories.reits',
  ETFs: 'common:seedNames.investmentSubcategories.etfs',
  BDRs: 'common:seedNames.investmentSubcategories.bdrs',
  'Fixed Income Funds': 'common:seedNames.investmentSubcategories.fixedIncomeFunds',
  Multimercado: 'common:seedNames.investmentSubcategories.multimercado',
  'Equity Funds': 'common:seedNames.investmentSubcategories.equityFunds',
  PGBL: 'common:seedNames.investmentSubcategories.pgbl',
  VGBL: 'common:seedNames.investmentSubcategories.vgbl',
  Stocks: 'common:seedNames.investmentSubcategories.stocks',
  Bonds: 'common:seedNames.investmentSubcategories.bonds',
}

const SEED_NAMES: Record<SeedTable, Record<string, string>> = {
  categories: CATEGORY_SEED_NAMES,
  paymentMethods: PAYMENT_METHOD_SEED_NAMES,
  institutions: INSTITUTION_SEED_NAMES,
  investmentCategories: INVESTMENT_CATEGORY_SEED_NAMES,
  investmentSubcategories: INVESTMENT_SUBCATEGORY_SEED_NAMES,
}

/**
 * Translates `currentName` if (and only if) it exactly matches one of `table`'s seeded strings;
 * otherwise returns it verbatim. Call wherever that table's rows render as a label - a list, a
 * select, a chip.
 *
 * `t` is typed as `(key: never) => string` rather than `i18next`'s own `TFunction`:
 * `useTranslation()`'s return type is branded by the namespace(s) passed to it, so a `TFunction`
 * scoped to e.g. `["categories", "common"]` doesn't structurally match one scoped to
 * `["paymentMethods", "common"]` - a `never` parameter is (contravariantly) assignable from any
 * such `TFunction` regardless of its namespace brand, so every call site's real `t` fits here
 * without a cast; the lookup key itself is cast once, internally, below.
 */
export function translateSeedName(
  table: SeedTable,
  currentName: string,
  t: (key: never) => string,
): string {
  const key = SEED_NAMES[table][currentName]
  return key ? t(key as never) : currentName
}
