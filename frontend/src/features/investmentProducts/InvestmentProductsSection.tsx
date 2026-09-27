import { useMemo, useState, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  DialogTitle,
  IconButton,
  Link as MuiLink,
  Paper,
  Tooltip,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import LockIcon from '@mui/icons-material/Lock'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import {
  useCloseInvestmentProduct,
  useCreateInvestmentProduct,
  useDeleteInvestmentProduct,
  useEditInvestmentProduct,
  useInvestmentProducts,
} from '../../api/investments/investmentProductsQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { nameLookup } from '../../utils/nameLookup'
import { InvestmentProductForm, type InvestmentProductFormValues } from './InvestmentProductForm'

interface InvestmentProductsSectionProps {
  /** The INVESTMENT account whose products are listed. */
  accountId: string
  /** A closed account takes no new products and its products can't be edited (F008 spec). */
  accountClosed: boolean
}

/**
 * The products of one INVESTMENT account (F008 spec): a table of name, category and sub-category
 * (names joined client-side from the categories list), an add dialog, an edit dialog and a close
 * action. Delete is offered only while the product has no history (`hasHistory`, F009) - once it
 * has some, closing is the only way out.
 *
 * Responsive (F021): `ResponsiveTable` embedded in the account detail page's own outlined "Products"
 * section (cards below `sm`, Sub-category hidden on tablet behind the row expander - the least
 * essential column, since some categories have none at all). The header's "Add product" button and
 * each row's/card's Edit open `InvestmentProductForm` in a `ResponsiveDialog` at every size (full
 * screen below `sm`); there is no form panel below the table any more.
 */
export function InvestmentProductsSection({
  accountId,
  accountClosed,
}: InvestmentProductsSectionProps) {
  const [error, setError] = useState<string | null>(null)
  const productsQuery = useInvestmentProducts(accountId)
  const products = productsQuery.data
  const productsState = useQueryState(productsQuery, setError)
  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const createMutation = useCreateInvestmentProduct()
  const editMutation = useEditInvestmentProduct()
  const closeMutation = useCloseInvestmentProduct()
  const deleteMutation = useDeleteInvestmentProduct()

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const subcategoryName = useMemo(
    () =>
      nameLookup(
        (categories ?? []).flatMap((c) => c.subcategories),
        (s) => s.name,
      ),
    [categories],
  )

  const [addDialogOpen, setAddDialogOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const [editTarget, setEditTarget] = useState<InvestmentProduct | null>(null)
  const [saving, setSaving] = useState(false)
  const [closeTarget, setCloseTarget] = useState<InvestmentProduct | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<InvestmentProduct | null>(null)
  const [confirming, setConfirming] = useState(false)
  // Bumped on each open so the dialog's form mounts with fresh state.
  const [formKey, setFormKey] = useState(0)

  // One load state for the table plus the category names behind it: rows show only once every
  // name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(categoriesState, productsState)
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
      })
      setAddDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  function openEditDialog(product: InvestmentProduct) {
    setError(null)
    setFormKey((n) => n + 1)
    setEditTarget(product)
  }

  function closeEditDialog() {
    setEditTarget(null)
    setError(null)
  }

  async function handleEdit(values: InvestmentProductFormValues) {
    if (!editTarget) return
    setError(null)
    setSaving(true)
    try {
      await editMutation.mutateAsync({
        id: editTarget.id,
        accountId,
        investmentCategoryId: values.categoryId,
        investmentSubcategoryId: values.subcategoryId || undefined,
        name: values.name,
      })
      setEditTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  async function confirmClose() {
    if (!closeTarget) return
    setError(null)
    setConfirming(true)
    try {
      await closeMutation.mutateAsync(closeTarget.id)
      setCloseTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setConfirming(false)
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    setError(null)
    setConfirming(true)
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      setDeleteTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
      setDeleteTarget(null)
    } finally {
      setConfirming(false)
    }
  }

  const dialogOpen = addDialogOpen || editTarget !== null

  function productLink(product: InvestmentProduct) {
    return (
      <MuiLink component={RouterLink} to={`/investment-products/${product.id}`} underline="hover">
        {product.name}
      </MuiLink>
    )
  }

  function statusChips(product: InvestmentProduct) {
    return (
      <>
        {product.closed ? (
          <Chip label="Closed" size="small" />
        ) : (
          <Chip label="Open" size="small" color="success" />
        )}
        {product.needsSnapshot && (
          <Tooltip title="A buy or sell is newer than the latest snapshot: the value may be out of date.">
            <Chip label="Needs snapshot" size="small" color="warning" sx={{ ml: 1 }} />
          </Tooltip>
        )}
      </>
    )
  }

  const columns: ResponsiveColumn<InvestmentProduct>[] = [
    { key: 'product', header: 'Product', render: productLink },
    { key: 'category', header: 'Category', render: (p) => categoryName(p.investmentCategoryId) },
    {
      key: 'subcategory',
      header: 'Sub-category',
      tabletPriority: 'low',
      render: (p) => (p.investmentSubcategoryId ? subcategoryName(p.investmentSubcategoryId) : '-'),
    },
    {
      key: 'latestValue',
      header: 'Latest value',
      align: 'right',
      render: (p) =>
        p.latestSnapshot ? (
          <Tooltip title={`Snapshot of ${p.latestSnapshot.date}`}>
            <span>{p.latestSnapshot.balance.toFixed(2)}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    { key: 'status', header: 'Status', render: statusChips },
  ]

  function rowActions(product: InvestmentProduct) {
    return (
      <>
        <IconButton
          size="small"
          aria-label="Edit"
          disabled={accountClosed}
          onClick={() => openEditDialog(product)}
        >
          <EditIcon fontSize="small" />
        </IconButton>
        <IconButton
          size="small"
          aria-label="Close"
          disabled={product.closed}
          onClick={() => setCloseTarget(product)}
        >
          <LockIcon fontSize="small" />
        </IconButton>
        {!product.hasHistory && (
          <IconButton size="small" aria-label="Delete" onClick={() => setDeleteTarget(product)}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        )}
      </>
    )
  }

  // The card leads with the product name (linked) and its status, then category/sub-category, then
  // the latest value; actions match the row.
  function renderCard(product: InvestmentProduct, actions: ReactNode) {
    return (
      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
            {productLink(product)}
          </Typography>
          {statusChips(product)}
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {categoryName(product.investmentCategoryId)}
          {product.investmentSubcategoryId
            ? ` · ${subcategoryName(product.investmentSubcategoryId)}`
            : ''}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {product.latestSnapshot
            ? `Latest value ${product.latestSnapshot.balance.toFixed(2)}`
            : 'No snapshot yet'}
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
            Add product
          </Button>
        )}
      </Box>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={dialogOpen ? null : error} onDismiss={() => setError(null)} />

      <ResponsiveTable
        aria-label="Investment products"
        embedded
        columns={columns}
        rows={products}
        getRowKey={(p) => p.id}
        state={tableState}
        onRetry={retry}
        actions={rowActions}
        renderCard={renderCard}
        emptyMessage="No products yet."
      />

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog} maxWidth="sm" fullWidth>
        <DialogTitle>Add product</DialogTitle>
        {categories && (
          <InvestmentProductForm
            key={formKey}
            dialog
            banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
            categories={categories}
            submitLabel="Add product"
            submitting={adding}
            onSubmit={(values) => void handleAdd(values)}
            onCancel={closeAddDialog}
          />
        )}
      </ResponsiveDialog>

      <ResponsiveDialog
        open={editTarget !== null}
        onClose={closeEditDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Edit product</DialogTitle>
        {editTarget && categories && (
          <InvestmentProductForm
            key={formKey}
            dialog
            banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
            categories={categories}
            initial={{
              name: editTarget.name,
              categoryId: editTarget.investmentCategoryId,
              subcategoryId: editTarget.investmentSubcategoryId ?? '',
            }}
            submitLabel="Save"
            submitting={saving}
            onSubmit={(values) => void handleEdit(values)}
            onCancel={closeEditDialog}
          />
        )}
      </ResponsiveDialog>

      <ConfirmDialog
        open={closeTarget !== null}
        title={`Close ${closeTarget?.name}?`}
        body="Closing a product is not reversible through this app - there is no reopen action. It stays in the list and keeps its history. A product that still has value can't be closed: record a zero snapshot (or sell the entire position) first."
        confirmLabel="Close product"
        loading={confirming}
        onConfirm={() => void confirmClose()}
        onCancel={() => setCloseTarget(null)}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        title={`Delete ${deleteTarget?.name}?`}
        body="The product has no history, so it can be deleted for good. This cannot be undone."
        confirmLabel="Delete product"
        loading={confirming}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  )
}
