import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  DialogTitle,
  Link as MuiLink,
  Paper,
  Tooltip,
  Typography,
} from '@mui/material'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import { useInvestmentHoldingsByAccount } from '../../api/investments/investmentHoldingsQueries'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import { useCreateInvestmentProduct } from '../../api/investments/investmentProductsQueries'
import { useInvestmentProducts } from '../../api/investments/investmentProductsQueries'
import { useInvestmentSegments } from '../../api/investments/investmentSegmentsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'
import { InvestmentProductForm, type InvestmentProductFormValues } from './InvestmentProductForm'

interface InvestmentProductsSectionProps {
  /** The INVESTMENT account whose holdings are listed. */
  accountId: string
  /** A closed account takes no new products (F008 spec). */
  accountClosed: boolean
}

/**
 * The holdings in one INVESTMENT account (F022 spec, ADR 0020): a read-only table of product name
 * (joined client-side from the products list), category/sub-category, latest value and status,
 * linking out to the product's own page for management. Product creation still happens here (it
 * still asks for one account, creating the product and its first holding together) - closing,
 * deleting and adding further holdings all moved to `InvestmentProductDetailPage`.
 *
 * Responsive (F021): `ResponsiveTable` embedded in the account detail page's own outlined
 * "Products" section (cards below `sm`, Sub-category hidden on tablet behind the row expander).
 */
export function InvestmentProductsSection({
  accountId,
  accountClosed,
}: InvestmentProductsSectionProps) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const { locale } = useLocale()
  const [error, setError] = useState<string | null>(null)
  const holdingsQuery = useInvestmentHoldingsByAccount(accountId)
  const holdings = holdingsQuery.data
  const holdingsState = useQueryState(holdingsQuery, setError)
  const productsQuery = useInvestmentProducts()
  const products = productsQuery.data
  const productsState = useQueryState(productsQuery, setError)
  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const segmentsQuery = useInvestmentSegments()
  const segments = segmentsQuery.data
  const createMutation = useCreateInvestmentProduct()

  const productName = useMemo(() => nameLookup(products ?? [], (p) => p.name), [products])
  const productOf = useMemo(() => {
    const map = new Map((products ?? []).map((p) => [p.id, p]))
    return (productId: string) => map.get(productId)
  }, [products])
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

  const [addDialogOpen, setAddDialogOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  // Bumped on each open so the dialog's form mounts with fresh state.
  const [formKey, setFormKey] = useState(0)

  // One load state for the table plus the names behind it: rows show only once every name can be
  // resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(categoriesState, productsState, holdingsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  function openAddDialog() {
    setError(null)
    setFormKey((n) => n + 1)
    setAddDialogOpen(true)
  }

  function closeAddDialog() {
    setAddDialogOpen(false)
    setError(null)
  }

  async function handleAdd(values: InvestmentProductFormValues) {
    setError(null)
    setAdding(true)
    try {
      await createMutation.mutateAsync({
        accountId,
        investmentCategoryId: values.categoryId,
        investmentSubcategoryId: values.subcategoryId || undefined,
        name: values.name,
        additionalNotes: values.additionalNotes || undefined,
        ticker: values.ticker || undefined,
        segmentId: values.segmentId || undefined,
        openingPosition: values.openingPosition,
      })
      setAddDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  function productLink(holding: InvestmentHolding) {
    return (
      <MuiLink
        component={RouterLink}
        to={`/investment-products/${holding.productId}`}
        underline="hover"
      >
        {productName(holding.productId)}
      </MuiLink>
    )
  }

  function statusChips(holding: InvestmentHolding) {
    return (
      <>
        {holding.closed ? (
          <Chip label={t('investmentProducts:detail.statusClosed')} size="small" />
        ) : (
          <Chip label={t('investmentProducts:detail.statusOpen')} size="small" color="success" />
        )}
        {holding.needsSnapshot && (
          <Tooltip title={t('investmentProducts:section.needsSnapshotTooltip')}>
            <Chip
              label={t('investmentProducts:detail.needsSnapshotChip')}
              size="small"
              color="warning"
              sx={{ ml: 1 }}
            />
          </Tooltip>
        )}
      </>
    )
  }

  const columns: ResponsiveColumn<InvestmentHolding>[] = [
    {
      key: 'product',
      header: t('investmentProducts:section.columns.product'),
      render: productLink,
    },
    {
      key: 'category',
      header: t('investmentProducts:section.columns.category'),
      render: (h) => {
        const product = productOf(h.productId)
        return product ? categoryName(product.investmentCategoryId) : ''
      },
    },
    {
      key: 'subcategory',
      header: t('investmentProducts:section.columns.subcategory'),
      tabletPriority: 'low',
      render: (h) => {
        const product = productOf(h.productId)
        return product?.investmentSubcategoryId
          ? subcategoryName(product.investmentSubcategoryId)
          : '-'
      },
    },
    {
      key: 'latestValue',
      header: t('investmentProducts:section.columns.latestValue'),
      align: 'right',
      render: (h) =>
        h.latestSnapshot ? (
          <Tooltip
            title={t('investmentProducts:section.snapshotTooltip', {
              date: h.latestSnapshot.date,
            })}
          >
            <span>{formatMoney(h.latestSnapshot.balance, locale)}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    { key: 'status', header: t('investmentProducts:section.columns.status'), render: statusChips },
  ]

  // The card leads with the product name (linked) and its status, then category/sub-category, then
  // the latest value.
  function renderCard(holding: InvestmentHolding, actions: ReactNode) {
    const product = productOf(holding.productId)
    return (
      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
            {productLink(holding)}
          </Typography>
          {statusChips(holding)}
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {product ? categoryName(product.investmentCategoryId) : ''}
          {product?.investmentSubcategoryId
            ? ` · ${subcategoryName(product.investmentSubcategoryId)}`
            : ''}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {holding.latestSnapshot
            ? t('investmentProducts:section.cardLatestValueLine', {
                value: formatMoney(holding.latestSnapshot.balance, locale),
              })
            : t('investmentProducts:section.cardNoSnapshot')}
        </Typography>
        {actions && (
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1 }}>{actions}</Box>
        )}
      </Paper>
    )
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        {!accountClosed && (
          <Button variant="contained" onClick={openAddDialog}>
            {t('investmentProducts:section.addButton')}
          </Button>
        )}
      </Box>

      <ErrorAlert message={addDialogOpen ? null : error} onDismiss={() => setError(null)} />

      <ResponsiveTable
        aria-label={t('investmentProducts:section.aria')}
        embedded
        columns={columns}
        rows={holdings}
        getRowKey={(h) => h.id}
        state={tableState}
        onRetry={retry}
        renderCard={renderCard}
        emptyMessage={t('investmentProducts:section.emptyList')}
      />

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog} maxWidth="sm" fullWidth>
        <DialogTitle>{t('investmentProducts:section.addDialogTitle')}</DialogTitle>
        {categories && (
          <InvestmentProductForm
            key={formKey}
            dialog
            banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
            categories={categories}
            segments={segments ?? []}
            submitLabel={t('investmentProducts:section.addButton')}
            submitting={adding}
            onSubmit={(values) => void handleAdd(values)}
            onCancel={closeAddDialog}
          />
        )}
      </ResponsiveDialog>
    </Box>
  )
}
