import { useMemo, useState } from 'react'
import {
  Box,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import LockIcon from '@mui/icons-material/Lock'
import { defaultErrorMessage } from '../../api/apiError'
import { getInvestmentCategories } from '../../api/investmentCategories'
import {
  closeInvestmentProduct,
  createInvestmentProduct,
  deleteInvestmentProduct,
  editInvestmentProduct,
  getInvestmentProducts,
  type InvestmentProduct,
} from '../../api/investmentProducts'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { DataTableBody } from '../../components/DataTableBody'
import { ErrorAlert } from '../../components/ErrorAlert'
import { combineLoadState, useAsyncData } from '../../hooks/useAsyncData'
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
 * (names joined client-side from the categories list), an add form, an edit dialog and a close
 * action. Delete is offered only while the product has no history (`hasHistory`, F009) - once it
 * has some, closing is the only way out.
 */
export function InvestmentProductsSection({
  accountId,
  accountClosed,
}: InvestmentProductsSectionProps) {
  const [error, setError] = useState<string | null>(null)
  const {
    data: products,
    setData: setProducts,
    ...productsState
  } = useAsyncData(() => getInvestmentProducts(accountId), [accountId], { onError: setError })
  const { data: categories, ...categoriesState } = useAsyncData(getInvestmentCategories, [], {
    onError: setError,
  })

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const subcategoryName = useMemo(
    () =>
      nameLookup(
        (categories ?? []).flatMap((c) => c.subcategories),
        (s) => s.name,
      ),
    [categories],
  )

  const [adding, setAdding] = useState(false)
  const [editTarget, setEditTarget] = useState<InvestmentProduct | null>(null)
  const [saving, setSaving] = useState(false)
  const [closeTarget, setCloseTarget] = useState<InvestmentProduct | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<InvestmentProduct | null>(null)
  const [confirming, setConfirming] = useState(false)
  // Bumped after each successful add so the add form remounts empty.
  const [addFormKey, setAddFormKey] = useState(0)

  // One load state for the table plus the category names behind it: rows show only once every
  // name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(categoriesState, productsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  async function handleAdd(values: InvestmentProductFormValues) {
    setError(null)
    setAdding(true)
    try {
      const created = await createInvestmentProduct({
        accountId,
        investmentCategoryId: values.categoryId,
        investmentSubcategoryId: values.subcategoryId || undefined,
        name: values.name,
      })
      setProducts((prev) => [...(prev ?? []), created])
      setAddFormKey((n) => n + 1)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  async function handleEdit(values: InvestmentProductFormValues) {
    if (!editTarget) return
    setError(null)
    setSaving(true)
    try {
      const updated = await editInvestmentProduct(editTarget.id, {
        accountId,
        investmentCategoryId: values.categoryId,
        investmentSubcategoryId: values.subcategoryId || undefined,
        name: values.name,
      })
      setProducts((prev) => prev?.map((p) => (p.id === updated.id ? updated : p)) ?? null)
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
      const closed = await closeInvestmentProduct(closeTarget.id)
      setProducts((prev) => prev?.map((p) => (p.id === closed.id ? closed : p)) ?? null)
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
      await deleteInvestmentProduct(deleteTarget.id)
      setProducts((prev) => prev?.filter((p) => p.id !== deleteTarget.id) ?? null)
      setDeleteTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
      setDeleteTarget(null)
    } finally {
      setConfirming(false)
    }
  }

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 2 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Product</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Sub-category</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={tableState} onRetry={retry} columns={5} actionsColumn>
              {products?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} align="center">
                    <Typography color="text.secondary">No products yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {products?.map((product) => (
                <TableRow key={product.id}>
                  <TableCell>{product.name}</TableCell>
                  <TableCell>{categoryName(product.investmentCategoryId)}</TableCell>
                  <TableCell>
                    {product.investmentSubcategoryId
                      ? subcategoryName(product.investmentSubcategoryId)
                      : '-'}
                  </TableCell>
                  <TableCell>
                    {product.closed ? (
                      <Chip label="Closed" size="small" />
                    ) : (
                      <Chip label="Open" size="small" color="success" />
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      aria-label="Edit"
                      disabled={accountClosed}
                      onClick={() => setEditTarget(product)}
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
                      <IconButton
                        size="small"
                        aria-label="Delete"
                        onClick={() => setDeleteTarget(product)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      {!accountClosed && categories && (
        <Paper variant="outlined" sx={{ p: 2 }} role="group" aria-label="Add product">
          <Typography variant="subtitle1" gutterBottom>
            Add product
          </Typography>
          <InvestmentProductForm
            key={addFormKey}
            categories={categories}
            submitLabel="Add product"
            submitting={adding}
            onSubmit={(values) => void handleAdd(values)}
          />
        </Paper>
      )}

      <Dialog
        open={editTarget !== null}
        onClose={() => setEditTarget(null)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Edit product</DialogTitle>
        <DialogContent>
          {editTarget && categories && (
            <Box sx={{ pt: 1 }}>
              <InvestmentProductForm
                key={editTarget.id}
                categories={categories}
                initial={{
                  name: editTarget.name,
                  categoryId: editTarget.investmentCategoryId,
                  subcategoryId: editTarget.investmentSubcategoryId ?? '',
                }}
                submitLabel="Save"
                submitting={saving}
                onSubmit={(values) => void handleEdit(values)}
                onCancel={() => setEditTarget(null)}
              />
            </Box>
          )}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={closeTarget !== null}
        title={`Close ${closeTarget?.name}?`}
        body="Closing a product is not reversible through this app - there is no reopen action. It stays in the list and keeps its history."
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
