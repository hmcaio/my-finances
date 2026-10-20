import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import {
  createCategory,
  deleteCategory,
  getCategories,
  renameCategory,
  type UpdateCategoryRequest,
} from './categories'

export const categoryKeys = {
  all: [API_KEY_ROOT, 'categories'] as const,
  list: () => [...categoryKeys.all, 'list'] as const,
}

/** Every category; shared by every page that resolves category names or offers a picker. */
export function useCategories() {
  return useQuery({
    queryKey: categoryKeys.list(),
    queryFn: getCategories,
    staleTime: STALE_TIME.reference,
  })
}

export function useCreateCategory() {
  return useMutation({ mutationFn: createCategory })
}

export function useRenameCategory() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateCategoryRequest & { id: string }) =>
      renameCategory(id, request),
  })
}

export function useDeleteCategory() {
  return useMutation({ mutationFn: deleteCategory })
}
