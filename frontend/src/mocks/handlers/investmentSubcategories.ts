import { http, HttpResponse } from 'msw'
import type { InvestmentSubcategory } from '../../api/investments/investmentSubcategories'
import { investmentCategoriesStore } from './investmentCategories'

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
    investmentCategoriesStore
      .list()
      .find((category) => category.subcategories.some((s) => s.id === subcategoryId))?.id ??
    investmentCategoriesStore.list()[0].id
  )
}

let created = 0

/**
 * Default success-path handlers for the investment sub-categories endpoints (F008's REST API):
 * they edit the nested rows of the categories store, since sub-categories are read nested under
 * their category. `resetStores()` restores it after each test.
 */
export const investmentSubcategoriesHandlers = [
  http.post(SUBCATEGORIES_URL, async ({ request }) => {
    const body = (await request.json()) as CreateSubcategoryRequestBody
    created += 1
    const subcategory: InvestmentSubcategory = {
      id: created === 1 ? 'isub-new' : `isub-new-${created}`,
      investmentCategoryId: body.investmentCategoryId,
      name: body.name,
    }
    investmentCategoriesStore.replace(body.investmentCategoryId, (category) => ({
      ...category,
      subcategories: [...category.subcategories, { id: subcategory.id, name: subcategory.name }],
    }))
    return HttpResponse.json(subcategory, { status: 201 })
  }),

  http.patch(`${SUBCATEGORIES_URL}/:id`, async ({ request, params }) => {
    const body = (await request.json()) as RenameSubcategoryRequestBody
    const parentId = parentOf(params.id as string)
    investmentCategoriesStore.replace(parentId, (category) => ({
      ...category,
      subcategories: category.subcategories.map((s) =>
        s.id === params.id ? { ...s, name: body.name } : s,
      ),
    }))
    const updated: InvestmentSubcategory = {
      id: params.id as string,
      investmentCategoryId: parentId,
      name: body.name,
    }
    return HttpResponse.json(updated)
  }),

  http.delete(`${SUBCATEGORIES_URL}/:id`, ({ params }) => {
    investmentCategoriesStore.replace(parentOf(params.id as string), (category) => ({
      ...category,
      subcategories: category.subcategories.filter((s) => s.id !== params.id),
    }))
    return new HttpResponse(null, { status: 204 })
  }),
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
