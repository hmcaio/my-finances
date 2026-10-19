import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import { useInvestmentProducts } from '../../api/investments/investmentProductsQueries'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import { combineLoadState, useQueryState, type LoadState } from '../../hooks/queryState'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'

/** Id -> display name for everything a Ledger row refers to (F037). */
export interface LedgerLookups {
  categoryName: (id: string) => string
  accountName: (id: string) => string
  paymentMethodName: (id: string) => string
  productName: (id: string) => string
}

/**
 * The reference lists behind a Ledger row's name cells and their combined load state: the rows
 * show only once every list has arrived, so a raw id never stands in for a name (the same rule as
 * the pages this replaced). Shared by the Ledger page and the account page's embed. Closed
 * accounts are included, since history refers to them.
 */
export function useLedgerLookups(onError?: (message: string) => void) {
  const { t } = useTranslation(['transactions', 'common'])
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, onError)
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, onError)
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data
  const paymentMethodsState = useQueryState(paymentMethodsQuery, onError)
  const productsQuery = useInvestmentProducts()
  const products = productsQuery.data
  const productsState = useQueryState(productsQuery, onError)

  const lookups: LedgerLookups = useMemo(
    () => ({
      categoryName: nameLookup(categories ?? [], (c) => translateSeedName('categories', c.name, t)),
      accountName: nameLookup(accounts ?? [], (a) => a.name),
      paymentMethodName: nameLookup(paymentMethods ?? [], (p) =>
        translateSeedName('paymentMethods', p.name, t),
      ),
      productName: nameLookup(products ?? [], (p) => p.name),
    }),
    [categories, accounts, paymentMethods, products, t],
  )

  const state: LoadState = combineLoadState(
    categoriesState,
    accountsState,
    paymentMethodsState,
    productsState,
  )

  return { lookups, state, categories, accounts, paymentMethods }
}
