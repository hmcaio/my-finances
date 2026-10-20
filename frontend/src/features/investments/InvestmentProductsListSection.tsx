import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Chip,
  Link as MuiLink,
  MenuItem,
  Paper,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import type {
  InvestmentProduct,
  InvestmentProductFilter,
} from '../../api/investments/investmentProducts'
import { useInvestmentProductsPage } from '../../api/investments/investmentProductsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { PaginationControls } from '../../components/table/PaginationControls'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'

const PAGE_SIZE = 20

/** A row's own derived status (the response's `closed` field, F023) - accurate under every status
 * filter, including `ALL` where rows of both statuses can appear together. */
function statusChip(product: InvestmentProduct, t: (key: never) => string) {
  return product.closed ? (
    <Chip label={t('productsList.statusClosed' as never)} size="small" />
  ) : (
    <Chip label={t('productsList.statusOpen' as never)} size="small" color="success" />
  )
}

/**
 * The global, filtered, paginated product list (F023 spec): server-paginated across every
 * account, filterable by category, sub-category, account (has a holding there) and status
 * (derived Open/Closed/All, default Open). Read-only - a row links to the product detail page
 * (F022) for management (create/edit/close/add-holding stay there, so that flow isn't duplicated).
 * Mirrors `TransactionsPage`'s filter/table/pagination structure.
 */
export function InvestmentProductsListSection() {
  const { t } = useTranslation('investments')
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<InvestmentProductFilter>({ status: 'OPEN' })
  const [error, setError] = useState<string | null>(null)

  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const investmentAccounts = useMemo(
    () => (accounts ?? []).filter((a) => a.type === 'INVESTMENT'),
    [accounts],
  )
  const productsQuery = useInvestmentProductsPage(filters, page, PAGE_SIZE)
  const products = productsQuery.data?.content
  const pageInfo = productsQuery.data
    ? {
        number: productsQuery.data.page.number,
        totalPages: productsQuery.data.page.totalPages,
      }
    : null
  const productsState = useQueryState(productsQuery, setError)

  function updateFilter(patch: Partial<InvestmentProductFilter>) {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  function clearFilters() {
    setFilters({ status: 'OPEN' })
    setPage(0)
  }

  const activeFilterCount = [
    filters.categoryId,
    filters.subcategoryId,
    filters.accountId,
    filters.name,
  ].filter(Boolean).length

  const categoryName = useMemo(
    () => nameLookup(categories ?? [], (c) => translateSeedName('investmentCategories', c.name, t)),
    [categories, t],
  )
  const subcategoryName = useMemo(
    () =>
      nameLookup(
        (categories ?? []).flatMap((c) => c.subcategories),
        (s) => translateSeedName('investmentSubcategories', s.name, t),
      ),
    [categories, t],
  )
  const subcategoryOptions = useMemo(
    () => (categories ?? []).find((c) => c.id === filters.categoryId)?.subcategories ?? [],
    [categories, filters.categoryId],
  )

  const tableState = combineLoadState(categoriesState, accountsState, productsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  function productLink(product: InvestmentProduct) {
    return (
      <MuiLink component={RouterLink} to={`/investment-products/${product.id}`} underline="hover">
        {product.name}
      </MuiLink>
    )
  }

  const columns: ResponsiveColumn<InvestmentProduct>[] = [
    { key: 'name', header: t('productsList.columns.name'), render: productLink, role: 'primary' },
    {
      key: 'category',
      header: t('productsList.columns.category'),
      render: (p) => categoryName(p.investmentCategoryId),
    },
    {
      key: 'subcategory',
      header: t('productsList.columns.subcategory'),
      tabletPriority: 'low',
      render: (p) => (p.investmentSubcategoryId ? subcategoryName(p.investmentSubcategoryId) : '-'),
    },
    {
      key: 'status',
      header: t('productsList.columns.status'),
      render: (p) => statusChip(p, t as (key: never) => string),
    },
  ]

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ResponsiveFilterBar activeCount={activeFilterCount} onClear={clearFilters}>
        <Select
          size="small"
          displayEmpty
          value={filters.categoryId ?? ''}
          onChange={(e) =>
            updateFilter({ categoryId: e.target.value || undefined, subcategoryId: undefined })
          }
          aria-label={t('productsList.categoryFilterAria')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('productsList.allCategories')}</MenuItem>
          {categories?.map((c) => (
            <MenuItem key={c.id} value={c.id}>
              {translateSeedName('investmentCategories', c.name, t)}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          displayEmpty
          value={filters.subcategoryId ?? ''}
          onChange={(e) => updateFilter({ subcategoryId: e.target.value || undefined })}
          disabled={!filters.categoryId}
          aria-label={t('productsList.subcategoryFilterAria')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('productsList.allSubcategories')}</MenuItem>
          {subcategoryOptions.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {translateSeedName('investmentSubcategories', s.name, t)}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          displayEmpty
          value={filters.accountId ?? ''}
          onChange={(e) => updateFilter({ accountId: e.target.value || undefined })}
          aria-label={t('productsList.accountFilterAria')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('productsList.allAccounts')}</MenuItem>
          {investmentAccounts.map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
        </Select>
        <TextField
          label={t('productsList.columns.name')}
          size="small"
          value={filters.name ?? ''}
          onChange={(e) => updateFilter({ name: e.target.value || undefined })}
        />
        <Select
          size="small"
          value={filters.status ?? 'OPEN'}
          onChange={(e) =>
            updateFilter({ status: e.target.value as InvestmentProductFilter['status'] })
          }
          aria-label={t('productsList.statusFilterAria')}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="OPEN">{t('productsList.statusOpen')}</MenuItem>
          <MenuItem value="CLOSED">{t('productsList.statusClosed')}</MenuItem>
          <MenuItem value="ALL">{t('productsList.statusAll')}</MenuItem>
        </Select>
      </ResponsiveFilterBar>

      <ResponsiveTable
        aria-label={t('productsList.aria')}
        columns={columns}
        rows={products}
        getRowKey={(p) => p.id}
        state={tableState}
        onRetry={retry}
        emptyMessage={t('productsList.emptyList')}
        renderCard={(product) => (
          <Paper variant="outlined" sx={{ p: 1.5 }}>
            <Box
              sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}
            >
              <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
                {productLink(product)}
              </Typography>
              {statusChip(product, t as (key: never) => string)}
            </Box>
            <Typography variant="body2" color="text.secondary">
              {categoryName(product.investmentCategoryId)}
              {product.investmentSubcategoryId
                ? ` · ${subcategoryName(product.investmentSubcategoryId)}`
                : ''}
            </Typography>
          </Paper>
        )}
      />

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} sx={{ mt: 0, mb: 1 }} />
    </Box>
  )
}
