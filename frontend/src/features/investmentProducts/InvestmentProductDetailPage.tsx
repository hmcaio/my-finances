import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { Alert, Box, IconButton, Link as MuiLink, Skeleton, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import { useInvestmentSegments } from '../../api/investments/investmentSegmentsQueries'
import { useInvestmentHoldingsByProduct } from '../../api/investments/investmentHoldingsQueries'
import { useInvestmentProduct } from '../../api/investments/investmentProductsQueries'
import { useInvestmentSnapshots } from '../../api/investments/investmentSnapshotsQueries'
import { useInvestmentValueSeries } from '../../api/investments/investmentValueSeriesQueries'
import { useTradeConfirmationLinesByProduct } from '../../api/investments/tradeConfirmationLinesQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { nameLookup } from '../../utils/nameLookup'
import { CurrentValueCard } from './CurrentValueCard'
import { HoldingDialogs } from './HoldingDialogs'
import { HoldingsPanel } from './HoldingsPanel'
import { OpeningPositionCard } from './OpeningPositionCard'
import { ProductDialogs } from './ProductDialogs'
import { SnapshotDialogs } from './SnapshotDialogs'
import { SnapshotsPanel } from './SnapshotsPanel'
import { TradeDialog } from './TradeDialog'
import { TradesPanel } from './TradesPanel'
import { useHoldingActions } from './useHoldingActions'
import { useProductActions } from './useProductActions'
import { useSnapshotActions } from './useSnapshotActions'
import { useTradeDialog } from './useTradeDialog'
import { ValueOverTimePanel } from './ValueOverTimePanel'
import { PageContainer } from '../../components/layout/PageContainer'

/**
 * Per-product detail view (F008 spec, made holding-aware by F022/ADR 0020): the product's taxonomy
 * (with Edit/Delete - delete only once it has zero holdings), a "Holdings" panel listing every
 * account this product is held in (each with its own close/delete/notes), a holding picker when
 * there is more than one, and - for the selected holding - the current value, Buy/Sell buttons, the
 * snapshot entry form/history, the product's trade history and the value/contribution chart. This
 * is the minimum needed to keep the page usable with multiple holdings; F023 turns it into the full
 * multi-holding management page.
 *
 * Responsive (F021): section padding tightens on phones. Dialogs use `ResponsiveDialog`; the
 * snapshot history and holdings tables stay plain tables (the 1-2/3-column rule); the trades table
 * becomes a `ResponsiveTable`.
 */
export function InvestmentProductDetailPage() {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const { id } = useParams<{ id: string }>()
  const productId = id ?? ''
  const [error, setError] = useState<string | null>(null)

  const productQuery = useInvestmentProduct(id)
  const product = productQuery.data
  const productState = useQueryState(productQuery, undefined, (err) =>
    defaultErrorMessage(err, { 404: t('investmentProducts:detail.notFound') }),
  )
  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const segmentsQuery = useInvestmentSegments()
  const segments = segmentsQuery.data
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  useQueryState(accountsQuery, setError)
  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])

  const holdingsQuery = useInvestmentHoldingsByProduct(id)
  const holdings = holdingsQuery.data
  const holdingsState = useQueryState(holdingsQuery, setError)
  const [selectedHoldingId, setSelectedHoldingId] = useState<string>('')
  const selectedHolding =
    holdings?.find((h) => h.id === selectedHoldingId) ?? holdings?.[0] ?? undefined

  const snapshotsQuery = useInvestmentSnapshots(selectedHolding?.id)
  const snapshots = snapshotsQuery.data
  const snapshotsState = useQueryState(snapshotsQuery, setError)
  const seriesQuery = useInvestmentValueSeries({ productId })
  const series = seriesQuery.data
  const seriesState = useQueryState(seriesQuery, setError)
  const tradesQuery = useTradeConfirmationLinesByProduct(productId)
  const tradesState = useQueryState(tradesQuery, setError)

  const snapshotActions = useSnapshotActions({ holdingId: selectedHolding?.id, setError })
  const tradeDialog = useTradeDialog({ holding: selectedHolding, product, setError })
  const productActions = useProductActions({ product, setError })
  const holdingActions = useHoldingActions({
    productId: product?.id,
    accounts,
    holdings,
    selectedHoldingId,
    setSelectedHoldingId,
    setError,
  })

  const { loading, loadError } = combineLoadState(productState)
  const showSkeleton = useDelayedFlag(loading)
  const selectedAccount = accounts?.find((a) => a.id === selectedHolding?.accountId)
  const selectedAccountClosed = selectedAccount?.closed ?? false
  const canTrade =
    selectedHolding !== undefined &&
    !selectedHolding.closed &&
    !selectedAccountClosed &&
    accounts !== undefined

  const points = series?.[0]?.points ?? []

  // The same dismissible banner, rendered inside a dialog.
  const errorBanner = <ErrorAlert message={error} onDismiss={() => setError(null)} />

  // While any dialog is open, a save error shows inside it: the page banner sits behind it.
  const anyDialogOpen =
    tradeDialog.preset !== null ||
    snapshotActions.record.open ||
    productActions.edit.open ||
    holdingActions.add.open

  return (
    <PageContainer>
      <MuiLink
        component={RouterLink}
        to={selectedHolding ? `/accounts/${selectedHolding.accountId}` : '/accounts'}
        underline="hover"
      >
        {t('investmentProducts:detail.backLink')}
      </MuiLink>

      {loadError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {loadError}
        </Alert>
      )}
      {showSkeleton && (
        <Box role="status" aria-label={t('investmentProducts:detail.loading')} sx={{ mt: 2 }}>
          <Skeleton variant="text" width={280} sx={{ typography: 'h4' }} />
          <Skeleton variant="rounded" height={160} sx={{ mt: 2 }} />
        </Box>
      )}

      {product && (
        <Box sx={fadeInSx}>
          <Box
            sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, mb: 1, flexWrap: 'wrap' }}
          >
            <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere' }}>
              {product.name}
            </Typography>
            <IconButton
              size="small"
              aria-label={t('investmentProducts:detail.editProductAria')}
              onClick={() => productActions.edit.setOpen(true)}
            >
              <EditIcon fontSize="small" />
            </IconButton>
            {(holdings?.length ?? 0) === 0 && (
              <IconButton
                size="small"
                aria-label={t('investmentProducts:detail.deleteProductAria')}
                onClick={() => productActions.remove.setOpen(true)}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            )}
          </Box>
          {product.additionalNotes && (
            <Typography color="text.secondary" sx={{ mb: 2, overflowWrap: 'anywhere' }}>
              {product.additionalNotes}
            </Typography>
          )}

          <ErrorAlert message={anyDialogOpen ? null : error} onDismiss={() => setError(null)} />

          <HoldingsPanel
            holdings={holdings}
            state={holdingsState}
            accountName={accountName}
            selectedId={selectedHolding?.id}
            addDisabled={holdingActions.add.availableAccounts.length === 0}
            onAdd={holdingActions.add.openDialog}
            onSelect={setSelectedHoldingId}
            onEditNotes={holdingActions.notes.start}
            onClose={holdingActions.closing.setTarget}
            onDelete={holdingActions.removing.setTarget}
          />

          {selectedHolding && (
            <>
              {/* Side by side from `sm` up (tablet and desktop), stacked on mobile. `minmax(0, ...)`
                  lets each column shrink below its 520px cap, so a narrow tablet never overflows. */}
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 520px))' },
                  gap: 3,
                  mb: 3,
                }}
              >
                <CurrentValueCard
                  holding={selectedHolding}
                  accountName={selectedAccount?.name}
                  canTrade={canTrade}
                  onTrade={tradeDialog.open}
                />

                <OpeningPositionCard holding={selectedHolding} />
              </Box>

              <SnapshotsPanel
                snapshots={snapshots}
                state={snapshotsState}
                onRecord={snapshotActions.record.openDialog}
                editing={snapshotActions.edit.editing}
                onEditingChange={snapshotActions.edit.setEditing}
                onSaveEdit={() => void snapshotActions.edit.save()}
                savingEdit={snapshotActions.edit.pending}
                onDelete={snapshotActions.remove.setTarget}
              />
            </>
          )}

          <ValueOverTimePanel points={points} state={seriesState} />

          <TradesPanel lines={tradesQuery.data} state={tradesState} />
        </Box>
      )}

      <SnapshotDialogs actions={snapshotActions} banner={errorBanner} />

      <TradeDialog
        trade={tradeDialog}
        productName={product?.name}
        accounts={accounts}
        setError={setError}
        banner={errorBanner}
      />

      <ProductDialogs
        actions={productActions}
        product={product}
        categories={categories}
        segments={segments}
        banner={errorBanner}
      />

      <HoldingDialogs actions={holdingActions} accountName={accountName} banner={errorBanner} />
    </PageContainer>
  )
}
