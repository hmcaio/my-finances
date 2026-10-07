import { apiClient } from '../core/client'
import { unwrap } from '../core/apiError'
import type { components } from '../generated/schema'
import type { Transaction } from '../transactions/transactions'

/** A Vehicle as returned by the API (PRD S5.11, F024 spec). Flat taxonomy, name only. */
export interface Vehicle {
  id: string
  name: string
}

export type CreateVehicleRequest = components['schemas']['CreateVehicleRequest']
export type UpdateVehicleRequest = components['schemas']['UpdateVehicleRequest']

/** Matches the backend's `TextFieldConstraints.MAX_NAME_LENGTH`, so inputs can cap what is typed. */
export const VEHICLE_NAME_MAX_LENGTH = 100

export const CONFLICT_MESSAGE =
  'This vehicle is used by existing fuel transactions — reassign them before deleting it.'

export const DUPLICATE_NAME_MESSAGE = 'A vehicle with this name already exists.'

/** Fetches every vehicle. Used as a dropdown-options source by the Transaction form and the
 * Fuel page's vehicle selector (F024 spec). */
export async function getVehicles(): Promise<Vehicle[]> {
  return unwrap(apiClient.get<Vehicle[]>('/vehicles'))
}

export async function createVehicle(request: CreateVehicleRequest): Promise<Vehicle> {
  return unwrap(apiClient.post<Vehicle>('/vehicles', request), DUPLICATE_NAME_MESSAGE)
}

export async function renameVehicle(id: string, request: UpdateVehicleRequest): Promise<Vehicle> {
  return unwrap(apiClient.patch<Vehicle>(`/vehicles/${id}`, request), DUPLICATE_NAME_MESSAGE)
}

export async function deleteVehicle(id: string): Promise<void> {
  await unwrap(apiClient.delete<void>(`/vehicles/${id}`), CONFLICT_MESSAGE)
}

/** Optional date bounds for {@link getVehicleFuelHistory}; either or both may be omitted
 * (unbounded on that side). */
export interface FuelHistoryFilter {
  from?: string
  to?: string
}

/**
 * Fuel-purchase history for one vehicle, with fuel details and computed ratios, ordered by date -
 * backs the Fuel page's list and charts (F024 spec).
 */
export async function getVehicleFuelHistory(
  vehicleId: string,
  filter: FuelHistoryFilter = {},
): Promise<Transaction[]> {
  return unwrap(
    apiClient.get<Transaction[]>(`/vehicles/${vehicleId}/fuel-history`, { params: filter }),
  )
}
