import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  INVESTMENT_SEGMENT_NAME_MAX_LENGTH,
} from '../../api/investments/investmentSegments'
import {
  investmentSegmentCreateConflictHandler,
  investmentSegmentDeleteConflictHandler,
  seedInvestmentSegments,
} from '../../mocks/handlers/investmentSegments'
import { describeSettingsPageOnly } from '../../test/settingsPageContract'
import { InvestmentSegmentsPage } from './InvestmentSegmentsPage'

describeSettingsPageOnly('InvestmentSegmentsPage', {
  page: <InvestmentSegmentsPage />,
  seedRows: seedInvestmentSegments,
  renameTarget: seedInvestmentSegments[0],
  deleteTarget: seedInvestmentSegments[1],
  newName: 'Papel',
  addButtonLabel: 'Add segment',
  conflict: { message: CONFLICT_MESSAGE, handler: investmentSegmentDeleteConflictHandler },
  duplicateName: {
    message: DUPLICATE_NAME_MESSAGE,
    handler: investmentSegmentCreateConflictHandler,
  },
  maxLength: INVESTMENT_SEGMENT_NAME_MAX_LENGTH,
  loadStates: { url: '/api/investment-segments', successBody: seedInvestmentSegments },
})
