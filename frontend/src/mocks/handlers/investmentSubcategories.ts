import { http, HttpResponse } from 'msw'
import type { InvestmentSubcategory } from '../../api/investmentSubcategories'
import { seedInvestmentCategories } from './investmentCategories'

const SUBCATEGORIES_URL = '/api/investment-subcategories'

interface CreateSubcategoryRequestBody {
  investmentCategoryId: string
  name: string
}

interface RenameSubcategoryRequestBody {
  name: string
}

function parentOf(subcategoryId: string): string {
  return (
    seedInvestmentCategories.find((category) =>
      category.subcategories.some((s) => s.id === subcategoryId),
    )?.id ?? seedInvestmentCategories[0].id
  )
}

/**
 * Default success-path handlers for the investment sub-categories endpoints (F008's REST API),
 * request-echoing like the categories ones. The sub-categories themselves are seeded through
 * `seedInvestmentCategories` (they are read nested under their category).
 */
export const investmentSubcategoriesHandlers = [
  http.post(SUBCATEGORIES_URL, async ({ request }) => {
    const body = (await request.json()) as CreateSubcategoryRequestBody
    const created: InvestmentSubcategory = {
      id: 'isub-new',
      investmentCategoryId: body.investmentCategoryId,
      name: body.name,
    }
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch(`${SUBCATEGORIES_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as RenameSubcategoryRequestBody
    const updated: InvestmentSubcategory = {
      id: params.id as string,
      investmentCategoryId: parentOf(params.id as string),
      name: body.name,
    }
    return HttpResponse.json(updated)
  }),

  http.delete(`${SUBCATEGORIES_URL}/:id`, () => new HttpResponse(null, { status: 204 })),
]

/** `409` variant for deleting a sub-category a product still uses (`InvestmentSubcategoryInUseException`). */
export const investmentSubcategoryDeleteConflictHandler = http.delete(
  `${SUBCATEGORIES_URL}/:id`,
  () => HttpResponse.json({ message: 'Sub-category is in use' }, { status: 409 }),
)

/** `409` variants for the duplicate-name case on create/rename (unique per parent category). */
export const investmentSubcategoryCreateConflictHandler = http.post(SUBCATEGORIES_URL, () =>
  HttpResponse.json({ message: 'Name already exists' }, { status: 409 }),
)

export const investmentSubcategoryRenameConflictHandler = http.patch(
  `${SUBCATEGORIES_URL}/:id`,
  () => HttpResponse.json({ message: 'Name already exists' }, { status: 409 }),
)
