import { INVESTMENT_SEGMENT_NAME_MAX_LENGTH } from '../../api/investments/investmentSegments'
import { resolveErrorMessage } from '../../test/errorMessages'
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
  conflict: {
    message: resolveErrorMessage('urn:my-finances:error:investment-segment-in-use'),
    handler: investmentSegmentDeleteConflictHandler,
  },
  duplicateName: {
    message: resolveErrorMessage('urn:my-finances:error:investment-segment-name-already-exists'),
    handler: investmentSegmentCreateConflictHandler,
  },
  maxLength: INVESTMENT_SEGMENT_NAME_MAX_LENGTH,
  loadStates: { url: '/api/investment-segments', successBody: seedInvestmentSegments },
})
