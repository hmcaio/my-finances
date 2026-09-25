import { useMutation } from '@tanstack/react-query'
import {
  createInvestmentSubcategory,
  deleteInvestmentSubcategory,
  renameInvestmentSubcategory,
  type UpdateInvestmentSubcategoryRequest,
} from './investmentSubcategories'

// Sub-categories are read nested under their category (`useInvestmentCategories`), so this module
// only has mutations; every success invalidates that list through the global rule.

export function useCreateInvestmentSubcategory() {
  return useMutation({ mutationFn: createInvestmentSubcategory })
}

export function useRenameInvestmentSubcategory() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateInvestmentSubcategoryRequest & { id: string }) =>
      renameInvestmentSubcategory(id, request),
  })
}

export function useDeleteInvestmentSubcategory() {
  return useMutation({ mutationFn: deleteInvestmentSubcategory })
}
