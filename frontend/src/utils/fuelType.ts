import type { FuelType } from '../api/transactions/transactions'

/** Fixed fuel type set (F024 spec, ADR 0021) - no custom/user-editable fuel types. Shared by the
 * Transaction form's fuel type picker and the Fuel page's price-per-liter-by-type chart legend. */
export const FUEL_TYPES: FuelType[] = [
  'ETANOL',
  'ETANOL_ADITIVADO',
  'GASOLINA',
  'GASOLINA_ADITIVADA',
]

const FUEL_TYPE_LABELS: Record<NonNullable<FuelType>, string> = {
  ETANOL: 'Etanol',
  ETANOL_ADITIVADO: 'Etanol Aditivado',
  GASOLINA: 'Gasolina',
  GASOLINA_ADITIVADA: 'Gasolina Aditivada',
}

export function fuelTypeLabel(fuelType: FuelType | null | undefined): string {
  return fuelType ? FUEL_TYPE_LABELS[fuelType] : ''
}
