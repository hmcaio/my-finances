import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { DialogTitle } from '@mui/material'
import type { InvestmentCategory } from '../../../api/investments/taxonomy/investmentCategories'
import type { InvestmentProduct } from '../../../api/investments/products/investmentProducts'
import type { InvestmentSegment } from '../../../api/investments/taxonomy/investmentSegments'
import { ConfirmDialog } from '../../../components/feedback/ConfirmDialog'
import { ResponsiveDialog } from '../../../components/feedback/ResponsiveDialog'
import { InvestmentProductForm } from './InvestmentProductForm'
import type { ProductActions } from './useProductActions'

interface ProductDialogsProps {
  actions: ProductActions
  product: InvestmentProduct | undefined
  categories: InvestmentCategory[] | undefined
  segments: InvestmentSegment[] | undefined
  /** The page's error banner, shown inside the edit dialog (the page one is behind it). */
  banner: ReactNode
}

/** The edit-product dialog and the delete-product confirmation. */
export function ProductDialogs({
  actions,
  product,
  categories,
  segments,
  banner,
}: ProductDialogsProps) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const { edit, remove } = actions
  return (
    <>
      <ResponsiveDialog
        open={edit.open}
        onClose={edit.pending ? undefined : () => edit.setOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('investmentProducts:detail.editProductDialogTitle')}</DialogTitle>
        {product && categories && (
          <InvestmentProductForm
            dialog
            banner={banner}
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
            submitting={edit.pending}
            onSubmit={(values) => void edit.save(values)}
            onCancel={() => edit.setOpen(false)}
          />
        )}
      </ResponsiveDialog>

      <ConfirmDialog
        open={remove.open}
        title={t('investmentProducts:detail.deleteProductTitle', { name: product?.name })}
        body={t('investmentProducts:detail.deleteProductBody')}
        confirmLabel={t('investmentProducts:detail.deleteProductConfirm')}
        loading={remove.pending}
        onConfirm={() => void remove.confirm()}
        onCancel={() => remove.setOpen(false)}
      />
    </>
  )
}
