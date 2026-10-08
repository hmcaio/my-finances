import { expect } from 'vitest'
import {
  investmentSegmentCreateConflictHandler,
  investmentSegmentDeleteConflictHandler,
  investmentSegmentRenameConflictHandler,
  seedInvestmentSegments,
} from '../../mocks/handlers/investmentSegments'
import { describeNamedEntityApi } from '../../test/apiContract'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  createInvestmentSegment,
  deleteInvestmentSegment,
  getInvestmentSegments,
  renameInvestmentSegment,
  type CreateInvestmentSegmentRequest,
  type InvestmentSegment,
  type UpdateInvestmentSegmentRequest,
} from './investmentSegments'

describeNamedEntityApi<
  InvestmentSegment,
  CreateInvestmentSegmentRequest,
  UpdateInvestmentSegmentRequest
>({
  label: 'investment segments',
  api: {
    get: getInvestmentSegments,
    create: createInvestmentSegment,
    rename: renameInvestmentSegment,
    remove: deleteInvestmentSegment,
  },
  seedList: seedInvestmentSegments,
  create: {
    request: { name: 'Papel' },
    expect: { name: 'Papel', id: expect.any(String) },
  },
  rename: {
    id: 'iseg-shoppings',
    request: { name: 'Shopping Centers' },
    expect: { id: 'iseg-shoppings', name: 'Shopping Centers' },
  },
  remove: { id: 'iseg-logistica' },
  conflict: {
    message: CONFLICT_MESSAGE,
    duplicateNameMessage: DUPLICATE_NAME_MESSAGE,
    createHandler: investmentSegmentCreateConflictHandler,
    renameHandler: investmentSegmentRenameConflictHandler,
    deleteHandler: investmentSegmentDeleteConflictHandler,
    createRequest: { name: 'Shoppings' },
    renameId: 'iseg-logistica',
    renameRequest: { name: 'Shoppings' },
  },
})
