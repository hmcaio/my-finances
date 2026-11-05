import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { InvestmentProduct } from '../../../api/investments/products/investmentProducts'
import {
  useDeleteInvestmentProduct,
  useEditInvestmentProduct,
} from '../../../api/investments/products/investmentProductsQueries'
import { useAction } from '../../../hooks/useAction'
import type { InvestmentProductFormValues } from './InvestmentProductForm'

/**
 * State and handlers of the product's own writes: the edit dialog and the delete confirmation
 * (a successful delete returns to the Accounts page). `product` is the loaded product; the
 * handlers do nothing until it is.
 */
export function useProductActions({
  product,
  setError,
}: {
  product: InvestmentProduct | undefined
  setError: (message: string | null) => void
}) {
  const navigate = useNavigate()
  const editProduct = useEditInvestmentProduct()
  const deleteProduct = useDeleteInvestmentProduct()
  const [editOpen, setEditOpen] = useState(false)
  const saveAction = useAction(setError)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const deleteAction = useAction(setError)

  async function saveEdit(values: InvestmentProductFormValues) {
    if (!product) return
    const saved = await saveAction.run(() =>
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
    if (saved) setEditOpen(false)
  }

  async function confirmDelete() {
    if (!product) return
    if (await deleteAction.run(() => deleteProduct.mutateAsync(product.id))) {
      navigate('/accounts')
    } else {
      setDeleteOpen(false)
    }
  }

  return {
    edit: {
      open: editOpen,
      setOpen: setEditOpen,
      pending: saveAction.pending,
      save: saveEdit,
    },
    remove: {
      open: deleteOpen,
      setOpen: setDeleteOpen,
      pending: deleteAction.pending,
      confirm: confirmDelete,
    },
  }
}

export type ProductActions = ReturnType<typeof useProductActions>
