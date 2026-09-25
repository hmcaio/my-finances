import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import {
  createInstitution,
  deleteInstitution,
  getInstitutions,
  renameInstitution,
  type UpdateInstitutionRequest,
} from './institutions'

export const institutionKeys = {
  all: [API_KEY_ROOT, 'institutions'] as const,
  list: () => [...institutionKeys.all, 'list'] as const,
}

/** Shared by the institutions page, the accounts page and every `InstitutionSelect`. */
export function useInstitutions() {
  return useQuery({
    queryKey: institutionKeys.list(),
    queryFn: getInstitutions,
    staleTime: STALE_TIME.reference,
  })
}

export function useCreateInstitution() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createInstitution,
    // The caller selects the new row right away (`InstitutionSelect`), so hold the mutation open
    // until the list has it. `cancelRefetch: false` joins the refetch the global invalidation
    // already started instead of restarting it.
    onSuccess: () =>
      queryClient.refetchQueries({ queryKey: institutionKeys.list() }, { cancelRefetch: false }),
  })
}

export function useRenameInstitution() {
  return useMutation({
    mutationFn: ({ id, ...request }: UpdateInstitutionRequest & { id: string }) =>
      renameInstitution(id, request),
  })
}

export function useDeleteInstitution() {
  return useMutation({ mutationFn: deleteInstitution })
}
