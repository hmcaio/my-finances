import { expect } from 'vitest'
import {
  seedVehicles,
  vehicleCreateConflictHandler,
  vehicleDeleteConflictHandler,
  vehicleRenameConflictHandler,
} from '../../mocks/handlers/vehicles'
import { describeNamedEntityApi } from '../../test/apiContract'
import { resolveErrorMessage } from '../../test/errorMessages'
import {
  createVehicle,
  deleteVehicle,
  getVehicles,
  renameVehicle,
  type CreateVehicleRequest,
  type UpdateVehicleRequest,
  type Vehicle,
} from './vehicles'

describeNamedEntityApi<Vehicle, CreateVehicleRequest, UpdateVehicleRequest>({
  label: 'vehicles',
  api: {
    get: getVehicles,
    create: createVehicle,
    rename: renameVehicle,
    remove: deleteVehicle,
  },
  seedList: seedVehicles,
  create: {
    request: { name: 'Fiesta' },
    expect: { name: 'Fiesta', id: expect.any(String) },
  },
  rename: {
    id: 'veh-1',
    request: { name: 'Civic 2019' },
    expect: { id: 'veh-1', name: 'Civic 2019' },
  },
  remove: { id: 'veh-1' },
  conflict: {
    message: resolveErrorMessage('urn:my-finances:error:vehicle-in-use'),
    duplicateNameMessage: resolveErrorMessage('urn:my-finances:error:vehicle-name-already-exists'),
    createHandler: vehicleCreateConflictHandler,
    renameHandler: vehicleRenameConflictHandler,
    deleteHandler: vehicleDeleteConflictHandler,
    createRequest: { name: 'Civic' },
    renameId: 'veh-2',
    renameRequest: { name: 'Civic' },
  },
})
