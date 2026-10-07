import {
  seedCivicVehicle,
  seedCorollaVehicle,
  seedVehicles,
  vehicleCreateConflictHandler,
  vehicleDeleteConflictHandler,
} from '../../mocks/handlers/vehicles'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  VEHICLE_NAME_MAX_LENGTH,
} from '../../api/vehicles/vehicles'
import { describeSettingsPageOnly } from '../../test/settingsPageContract'
import { VehiclesPage } from './VehiclesPage'

describeSettingsPageOnly('VehiclesPage', {
  page: <VehiclesPage />,
  seedRows: seedVehicles,
  renameTarget: seedCivicVehicle,
  deleteTarget: seedCorollaVehicle,
  newName: 'Fiesta',
  addButtonLabel: 'Add vehicle',
  conflict: { message: CONFLICT_MESSAGE, handler: vehicleDeleteConflictHandler },
  duplicateName: { message: DUPLICATE_NAME_MESSAGE, handler: vehicleCreateConflictHandler },
  maxLength: VEHICLE_NAME_MAX_LENGTH,
  loadStates: { url: '/api/vehicles', successBody: seedVehicles },
})
