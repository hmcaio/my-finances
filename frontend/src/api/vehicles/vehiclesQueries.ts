import { useMutation, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT, STALE_TIME } from '../core/queryClient'
import {
  createVehicle,
  deleteVehicle,
  getVehicleFuelHistory,
  getVehicles,
  renameVehicle,
  type FuelHistoryFilter,
  type UpdateVehicleRequest,
} from './vehicles'

export const vehicleKeys = {
  all: [API_KEY_ROOT, 'vehicles'] as const,
  list: () => [...vehicleKeys.all, 'list'] as const,
  fuelHistory: (vehicleId: string, filter: FuelHistoryFilter) =>
    [...vehicleKeys.all, 'fuel-history', vehicleId, filter] as const,
}

export function useVehicles() {
  return useQuery({
    queryKey: vehicleKeys.list(),
    queryFn: getVehicles,
    staleTime: STALE_TIME.reference,
  })
}

/** Fuel history for one vehicle (F024 spec's Fuel page); disabled until a vehicle is selected. */
export function useVehicleFuelHistory(vehicleId: string | null, filter: FuelHistoryFilter = {}) {
  return useQuery({
    queryKey: vehicleKeys.fuelHistory(vehicleId ?? '', filter),
    queryFn: () => getVehicleFuelHistory(vehicleId!, filter),
    enabled: vehicleId !== null,
  })
}

export function useCreateVehicle() {
  return useMutation({
    meta: { successMessage: 'vehicles:toast.saved' },
    mutationFn: createVehicle,
  })
}

export function useRenameVehicle() {
  return useMutation({
    meta: { successMessage: 'vehicles:toast.updated' },
    mutationFn: ({ id, ...request }: UpdateVehicleRequest & { id: string }) =>
      renameVehicle(id, request),
  })
}

export function useDeleteVehicle() {
  return useMutation({
    meta: { successMessage: 'vehicles:toast.deleted' },
    mutationFn: deleteVehicle,
  })
}
