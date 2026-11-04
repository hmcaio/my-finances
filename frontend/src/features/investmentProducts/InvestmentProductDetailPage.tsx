import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Box,
  Button,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Link as MuiLink,
  MenuItem,
  Select,
  Skeleton,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import { useInvestmentSegments } from '../../api/investments/investmentSegmentsQueries'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import {
  useCloseInvestmentHolding,
  useCreateInvestmentHolding,
  useDeleteInvestmentHolding,
  useEditInvestmentHoldingNotes,
  useInvestmentHoldingsByProduct,
} from '../../api/investments/investmentHoldingsQueries'
import {
  useDeleteInvestmentProduct,
  useEditInvestmentProduct,
  useInvestmentProduct,
} from '../../api/investments/investmentProductsQueries'
import { useInvestmentSnapshots } from '../../api/investments/investmentSnapshotsQueries'
import { useInvestmentValueSeries } from '../../api/investments/investmentValueSeriesQueries'
import { useTradeConfirmationLinesByProduct } from '../../api/investments/tradeConfirmationLinesQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useAction } from '../../hooks/useAction'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { nameLookup } from '../../utils/nameLookup'
import { TransferForm, type TransferFormPreset } from '../transfers/TransferForm'
import { InvestmentProductForm, type InvestmentProductFormValues } from './InvestmentProductForm'
import { CurrentValueCard } from './CurrentValueCard'
import { SECTION_PADDING } from './detailLayout'
import { HoldingsPanel } from './HoldingsPanel'
import { OpeningPositionCard } from './OpeningPositionCard'
import { SnapshotDialogs } from './SnapshotDialogs'
import { SnapshotsPanel } from './SnapshotsPanel'
import { TradesPanel } from './TradesPanel'
import { useSnapshotActions } from './useSnapshotActions'
import { ValueOverTimePanel } from './ValueOverTimePanel'

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
  const navigate = useNavigate()
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
  const holdingAccountIds = new Set((holdings ?? []).map((h) => h.accountId))

  const snapshotsQuery = useInvestmentSnapshots(selectedHolding?.id)
  const snapshots = snapshotsQuery.data
  const snapshotsState = useQueryState(snapshotsQuery, setError)
  const seriesQuery = useInvestmentValueSeries({ productId })
  const series = seriesQuery.data
  const seriesState = useQueryState(seriesQuery, setError)
  const tradesQuery = useTradeConfirmationLinesByProduct(productId)
  const tradesState = useQueryState(tradesQuery, setError)
  const editProduct = useEditInvestmentProduct()
  const deleteProduct = useDeleteInvestmentProduct()
  const createHolding = useCreateInvestmentHolding()
  const closeHolding = useCloseInvestmentHolding()
  const deleteHolding = useDeleteInvestmentHolding()
  const editHoldingNotes = useEditInvestmentHoldingNotes()

  const snapshotActions = useSnapshotActions({ holdingId: selectedHolding?.id, setError })
  const [tradeDialog, setTradeDialog] = useState<TransferFormPreset | null>(null)
  // The dialog form remounts fresh for each Buy/Sell click.
  const [dialogKey, setDialogKey] = useState(0)

  const [editProductOpen, setEditProductOpen] = useState(false)
  const saveProductAction = useAction(setError)
  const [deleteProductConfirm, setDeleteProductConfirm] = useState(false)
  const deleteProductAction = useAction(setError)
  const [addHoldingOpen, setAddHoldingOpen] = useState(false)
  const [addHoldingAccountId, setAddHoldingAccountId] = useState('')
  const [addHoldingNotes, setAddHoldingNotes] = useState('')
  const addHoldingAction = useAction(setError)
  const [closeHoldingTarget, setCloseHoldingTarget] = useState<InvestmentHolding | null>(null)
  // Closing and deleting a holding share one confirmation spinner.
  const holdingConfirmAction = useAction(setError)
  const [deleteHoldingTarget, setDeleteHoldingTarget] = useState<InvestmentHolding | null>(null)
  const [notesEdit, setNotesEdit] = useState<{ id: string; value: string } | null>(null)
  const notesAction = useAction(setError)

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
    tradeDialog !== null || snapshotActions.record.open || editProductOpen || addHoldingOpen

  function openTrade(direction: 'buy' | 'sell') {
    if (!selectedHolding || !product) return
    setError(null)
    setDialogKey((n) => n + 1)
    setTradeDialog({
      direction,
      investmentAccountId: selectedHolding.accountId,
      productId: product.id,
      productName: product.name,
    })
  }

  function handleTradeSaved() {
    setTradeDialog(null)
  }

  async function handleEditProduct(values: InvestmentProductFormValues) {
    if (!product) return
    const saved = await saveProductAction.run(() =>
      editProduct.mutateAsync({
        id: product.id,
        investmentCategoryId: values.categoryId,
        investmentSubcategoryId: values.subcategoryId || undefined,
        name: values.name,
        additionalNotes: values.additionalNotes || undefined,
        ticker: values.ticker || undefined,
        segmentId: values.segmentId || undefined,
      }),
    )
    if (saved) setEditProductOpen(false)
  }

  async function handleDeleteProduct() {
    if (!product) return
    if (await deleteProductAction.run(() => deleteProduct.mutateAsync(product.id))) {
      navigate('/accounts')
    } else {
      setDeleteProductConfirm(false)
    }
  }

  function openAddHolding() {
    setError(null)
    setAddHoldingAccountId('')
    setAddHoldingNotes('')
    setAddHoldingOpen(true)
  }

  async function handleAddHolding() {
    if (!product || addHoldingAccountId === '') return
    const added = await addHoldingAction.run(async () => {
      const created = await createHolding.mutateAsync({
        productId: product.id,
        accountId: addHoldingAccountId,
        additionalNotes: addHoldingNotes.trim() || undefined,
      })
      setSelectedHoldingId(created.id)
    })
    if (added) setAddHoldingOpen(false)
  }

  async function confirmCloseHolding() {
    if (!closeHoldingTarget) return
    if (await holdingConfirmAction.run(() => closeHolding.mutateAsync(closeHoldingTarget.id))) {
      setCloseHoldingTarget(null)
    }
  }

  async function confirmDeleteHolding() {
    if (!deleteHoldingTarget) return
    const deleted = await holdingConfirmAction.run(() =>
      deleteHolding.mutateAsync(deleteHoldingTarget.id),
    )
    if (deleted && selectedHoldingId === deleteHoldingTarget.id) setSelectedHoldingId('')
    setDeleteHoldingTarget(null)
  }

  async function handleSaveNotes() {
    if (!notesEdit) return
    const saved = await notesAction.run(() =>
      editHoldingNotes.mutateAsync({
        id: notesEdit.id,
        additionalNotes: notesEdit.value || undefined,
      }),
    )
    if (saved) setNotesEdit(null)
  }

  const availableAccountsForNewHolding = (accounts ?? []).filter(
    (a) => a.type === 'INVESTMENT' && !a.closed && !holdingAccountIds.has(a.id),
  )

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
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
              onClick={() => setEditProductOpen(true)}
            >
              <EditIcon fontSize="small" />
            </IconButton>
            {(holdings?.length ?? 0) === 0 && (
              <IconButton
                size="small"
                aria-label={t('investmentProducts:detail.deleteProductAria')}
                onClick={() => setDeleteProductConfirm(true)}
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
            addDisabled={availableAccountsForNewHolding.length === 0}
            onAdd={openAddHolding}
            onSelect={setSelectedHoldingId}
            onEditNotes={(h) => setNotesEdit({ id: h.id, value: h.additionalNotes ?? '' })}
            onClose={setCloseHoldingTarget}
            onDelete={setDeleteHoldingTarget}
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
                  onTrade={openTrade}
                />

                <OpeningPositionCard holding={selectedHolding} padding={SECTION_PADDING} />
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

      <ResponsiveDialog
        open={tradeDialog !== null}
        onClose={() => setTradeDialog(null)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          {t('investmentProducts:detail.tradeDialogTitle', {
            direction:
              tradeDialog?.direction === 'sell'
                ? t('investmentProducts:detail.sellButton')
                : t('investmentProducts:detail.buyButton'),
            product: product?.name,
          })}
        </DialogTitle>
        {tradeDialog && accounts && (
          <TransferForm
            key={dialogKey}
            dialog
            banner={errorBanner}
            accounts={accounts}
            preset={tradeDialog}
            onSaved={handleTradeSaved}
            onError={setError}
            onCancel={() => setTradeDialog(null)}
          />
        )}
      </ResponsiveDialog>

      <ResponsiveDialog
        open={editProductOpen}
        onClose={saveProductAction.pending ? undefined : () => setEditProductOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('investmentProducts:detail.editProductDialogTitle')}</DialogTitle>
        {product && categories && (
          <InvestmentProductForm
            dialog
            banner={errorBanner}
            categories={categories}
            segments={segments ?? []}
            initial={{
              name: product.name,
              categoryId: product.investmentCategoryId,
              subcategoryId: product.investmentSubcategoryId ?? '',
              additionalNotes: product.additionalNotes ?? '',
              ticker: product.ticker ?? '',
              segmentId: product.segmentId ?? '',
            }}
            submitLabel={t('common:actions.save')}
            submitting={saveProductAction.pending}
            onSubmit={(values) => void handleEditProduct(values)}
            onCancel={() => setEditProductOpen(false)}
          />
        )}
      </ResponsiveDialog>

      <ConfirmDialog
        open={deleteProductConfirm}
        title={t('investmentProducts:detail.deleteProductTitle', { name: product?.name })}
        body={t('investmentProducts:detail.deleteProductBody')}
        confirmLabel={t('investmentProducts:detail.deleteProductConfirm')}
        loading={deleteProductAction.pending}
        onConfirm={() => void handleDeleteProduct()}
        onCancel={() => setDeleteProductConfirm(false)}
      />

      <ResponsiveDialog
        open={addHoldingOpen}
        onClose={addHoldingAction.pending ? undefined : () => setAddHoldingOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('investmentProducts:detail.addHoldingDialogTitle')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            {errorBanner}
            <FormGrid>
              <Select
                size="small"
                displayEmpty
                value={addHoldingAccountId}
                onChange={(e) => setAddHoldingAccountId(e.target.value)}
                aria-label={t('investmentProducts:detail.accountLabel')}
              >
                <MenuItem value="" disabled>
                  {t('investmentProducts:detail.accountLabel')}
                </MenuItem>
                {availableAccountsForNewHolding.map((a) => (
                  <MenuItem key={a.id} value={a.id}>
                    {a.name}
                  </MenuItem>
                ))}
              </Select>
              <TextField
                label={t('investmentProducts:detail.notesLabel')}
                size="small"
                multiline
                value={addHoldingNotes}
                onChange={(e) => setAddHoldingNotes(e.target.value)}
                slotProps={{ htmlInput: { maxLength: 500 } }}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddHoldingOpen(false)} disabled={addHoldingAction.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={addHoldingAction.pending || addHoldingAccountId === ''}
            onClick={() => void handleAddHolding()}
          >
            {t('investmentProducts:detail.addHoldingButton')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ResponsiveDialog
        open={notesEdit !== null}
        onClose={notesAction.pending ? undefined : () => setNotesEdit(null)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('investmentProducts:detail.editHoldingNotesTitle')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            {errorBanner}
            <TextField
              label={t('investmentProducts:detail.notesLabel')}
              size="small"
              fullWidth
              multiline
              value={notesEdit?.value ?? ''}
              onChange={(e) =>
                setNotesEdit((prev) => (prev ? { ...prev, value: e.target.value } : prev))
              }
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setNotesEdit(null)} disabled={notesAction.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={notesAction.pending}
            onClick={() => void handleSaveNotes()}
          >
            {t('common:actions.save')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ConfirmDialog
        open={closeHoldingTarget !== null}
        title={t('investmentProducts:detail.closeHoldingTitle', {
          account: closeHoldingTarget ? accountName(closeHoldingTarget.accountId) : '',
        })}
        body={t('investmentProducts:detail.closeHoldingBody')}
        confirmLabel={t('investmentProducts:detail.closeHoldingConfirm')}
        loading={holdingConfirmAction.pending}
        onConfirm={() => void confirmCloseHolding()}
        onCancel={() => setCloseHoldingTarget(null)}
      />

      <ConfirmDialog
        open={deleteHoldingTarget !== null}
        title={t('investmentProducts:detail.deleteHoldingTitle', {
          account: deleteHoldingTarget ? accountName(deleteHoldingTarget.accountId) : '',
        })}
        body={t('investmentProducts:detail.deleteHoldingBody')}
        confirmLabel={t('investmentProducts:detail.deleteHoldingConfirm')}
        loading={holdingConfirmAction.pending}
        onConfirm={() => void confirmDeleteHolding()}
        onCancel={() => setDeleteHoldingTarget(null)}
      />
    </Box>
  )
}
