import { http, HttpResponse } from 'msw'
import type { Vehicle } from '../../api/vehicles/vehicles'
import { createStore } from '../store'

/**
 * Seed data returned by the default `GET /api/vehicles` handler below. Exported so tests can
 * assert against it directly instead of duplicating the fixture (F015 spec's F002 backfill
 * pattern).
 */
export const seedVehicles: Vehicle[] = [
  { id: 'veh-1', name: 'Civic' },
  { id: 'veh-2', name: 'Corolla' },
]

export const seedCivicVehicle = seedVehicles.find((v) => v.name === 'Civic')!
export const seedCorollaVehicle = seedVehicles.find((v) => v.name === 'Corolla')!

const VEHICLES_URL = '/api/vehicles'

interface VehicleRequestBody {
  name: string
}

const vehicles = createStore(seedVehicles)

/**
 * Default success-path handlers for every vehicles endpoint (F024's REST API), backed by an
 * in-memory store restored after each test (see `categories.ts`).
 */
export const vehiclesHandlers = [
  http.get(VEHICLES_URL, () => HttpResponse.json(vehicles.list())),

  http.post(VEHICLES_URL, async ({ request }) => {
    const body = (await request.json()) as VehicleRequestBody
    const created = vehicles.add({ id: vehicles.nextId('veh'), name: body.name })
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${VEHICLES_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as VehicleRequestBody
    const updated = vehicles.replace(params.id as string, (row) => ({ ...row, name: body.name }))
    return updated ? HttpResponse.json(updated) : new HttpResponse(null, { status: 404 })
  }),

  http.delete(`${VEHICLES_URL}/:id`, ({ params }) => {
    vehicles.remove(params.id as string)
    return new HttpResponse(null, { status: 204 })
  }),

  // Fuel history (`GET /api/vehicles/:id/fuel-history`) is registered in `transactions.ts`
  // instead: it returns Transaction-shaped rows filtered from the transactions store, which this
  // file has no access to.
]

/**
 * `409` variant for the delete-conflict case (F024 spec's `conflictMessage`, backed by
 * `VehicleInUseException`) - see `categoryDeleteConflictHandler` in `categories.ts`.
 */
export const vehicleDeleteConflictHandler = http.delete(`${VEHICLES_URL}/:id`, () =>
  HttpResponse.json({ message: 'Vehicle is in use' }, { status: 409 }),
)

/**
 * `409` variant for the duplicate-name case on create/rename (`VehicleNameAlreadyExistsException`)
 * - see `categoryCreateConflictHandler` in `categories.ts`.
 */
export const vehicleCreateConflictHandler = http.post(VEHICLES_URL, () =>
  HttpResponse.json({ message: 'Vehicle name already exists' }, { status: 409 }),
)

export const vehicleRenameConflictHandler = http.patch(`${VEHICLES_URL}/:id`, () =>
  HttpResponse.json({ message: 'Vehicle name already exists' }, { status: 409 }),
)
