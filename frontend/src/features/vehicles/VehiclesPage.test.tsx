import {
  seedCivicVehicle,
  seedCorollaVehicle,
  seedVehicles,
  vehicleCreateConflictHandler,
  vehicleDeleteConflictHandler,
} from '../../mocks/handlers/vehicles'
import { VEHICLE_NAME_MAX_LENGTH } from '../../api/vehicles/vehicles'
import { PROBLEM_TYPE_MESSAGES } from '../../api/core/apiError'
import { describeSettingsPageOnly } from '../../test/settingsPageContract'
import { VehiclesPage } from './VehiclesPage'

describeSettingsPageOnly('VehiclesPage', {
  page: <VehiclesPage />,
  seedRows: seedVehicles,
  renameTarget: seedCivicVehicle,
  deleteTarget: seedCorollaVehicle,
  newName: 'Fiesta',
  addButtonLabel: 'Add vehicle',
  conflict: {
    message: PROBLEM_TYPE_MESSAGES['urn:my-finances:error:vehicle-in-use'],
    handler: vehicleDeleteConflictHandler,
  },
  duplicateName: {
    message: PROBLEM_TYPE_MESSAGES['urn:my-finances:error:vehicle-name-already-exists'],
    handler: vehicleCreateConflictHandler,
  },
  maxLength: VEHICLE_NAME_MAX_LENGTH,
  loadStates: { url: '/api/vehicles', successBody: seedVehicles },
})
