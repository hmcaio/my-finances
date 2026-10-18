import { Navigate, useLocation } from 'react-router-dom'

/**
 * `/transactions` -> `/ledger` (F037). The old Transactions page is gone, but its URL lives on in
 * bookmarks, the browser history and the Fuel page's hand-off. The query string and the navigation
 * state ride along: a string `to` would drop the query string, so `search` is passed explicitly.
 */
export function TransactionsRedirect() {
  const { search, state } = useLocation()
  return <Navigate replace to={{ pathname: '/ledger', search }} state={state} />
}

/**
 * `/transfers` -> `/ledger?type=transfer` (F037), keeping any other query parameter, so
 * `/transfers?focus=ID` (the product page's old "View confirmation" link) lands on
 * `/ledger?type=transfer&focus=ID` and still opens that transfer.
 */
export function TransfersRedirect() {
  const { search, state } = useLocation()
  const params = new URLSearchParams({ type: 'transfer' })
  new URLSearchParams(search).forEach((value, key) => {
    if (key !== 'type') params.append(key, value)
  })
  return <Navigate replace to={{ pathname: '/ledger', search: params.toString() }} state={state} />
}
