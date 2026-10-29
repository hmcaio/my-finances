import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { InvestmentCategory } from '../../api/investments/investmentCategories'
import type { InvestmentSegment } from '../../api/investments/investmentSegments'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { selectOption } from '../../test/testUtils'
import { InvestmentProductForm, type InvestmentProductFormValues } from './InvestmentProductForm'

const categories: InvestmentCategory[] = [
  {
    id: 'c-fixed',
    name: 'Fixed Income',
    subcategories: [{ id: 's-cdb', name: 'CDB' }],
  },
  {
    id: 'c-var',
    name: 'Variable Income',
    subcategories: [
      { id: 's-etfs', name: 'ETFs' },
      { id: 's-fii', name: 'REITs (FIIs)' },
    ],
  },
]
const segments: InvestmentSegment[] = [{ id: 'seg-shop', name: 'Shoppings' }]

const empty: InvestmentProductFormValues = {
  name: 'KNRI11 Test',
  categoryId: 'c-var',
  subcategoryId: 's-fii',
  additionalNotes: '',
  ticker: '',
  segmentId: '',
}

function renderForm(initial?: InvestmentProductFormValues) {
  const onSubmit = vi.fn()
  renderWithQueryClient(
    <InvestmentProductForm
      categories={categories}
      segments={segments}
      initial={initial}
      submitLabel="Save"
      submitting={false}
      onSubmit={onSubmit}
    />,
  )
  return { user: userEvent.setup({ delay: null }), onSubmit }
}

const ticker = () => screen.queryByRole('textbox', { name: 'Ticker' })
const segment = () => screen.queryByRole('combobox', { name: 'Segment' })

describe('InvestmentProductForm ticker and segment visibility (#230)', () => {
  it('hides both until a relevant sub-category is chosen', async () => {
    const { user } = renderForm({ ...empty, categoryId: '', subcategoryId: '' })
    expect(ticker()).not.toBeInTheDocument()
    expect(segment()).not.toBeInTheDocument()

    await selectOption(user, 'Category', 'Fixed Income')
    await selectOption(user, 'Sub-category', 'CDB')
    expect(ticker()).not.toBeInTheDocument()
    expect(segment()).not.toBeInTheDocument()

    await selectOption(user, 'Category', 'Variable Income')
    await selectOption(user, 'Sub-category', 'ETFs')
    expect(ticker()).toBeInTheDocument()
    expect(segment()).not.toBeInTheDocument()

    await selectOption(user, 'Sub-category', 'REITs (FIIs)')
    expect(ticker()).toBeInTheDocument()
    expect(segment()).toBeInTheDocument()
  })

  it('submits the ticker and segment for an FII', async () => {
    const { user, onSubmit } = renderForm({ ...empty, categoryId: '', subcategoryId: '' })
    await selectOption(user, 'Category', 'Variable Income')
    await selectOption(user, 'Sub-category', 'REITs (FIIs)')
    await user.type(screen.getByRole('textbox', { name: 'Ticker' }), 'KNRI11')
    await selectOption(user, 'Segment', 'Shoppings')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ ticker: 'KNRI11', segmentId: 'seg-shop' }),
    )
  })

  it('keeps a typed value while hidden but does not submit it, and restores it on switching back', async () => {
    const { user, onSubmit } = renderForm({ ...empty, categoryId: '', subcategoryId: '' })
    await selectOption(user, 'Category', 'Variable Income')
    await selectOption(user, 'Sub-category', 'ETFs')
    await user.type(screen.getByRole('textbox', { name: 'Ticker' }), 'BOVA11')

    await selectOption(user, 'Category', 'Fixed Income')
    await selectOption(user, 'Sub-category', 'CDB')
    expect(ticker()).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ ticker: '', segmentId: '' }))

    await selectOption(user, 'Category', 'Variable Income')
    await selectOption(user, 'Sub-category', 'ETFs')
    expect(screen.getByRole('textbox', { name: 'Ticker' })).toHaveValue('BOVA11')
  })

  it('keeps a stored ticker and segment visible and submitted on a non-matching product', async () => {
    const { user, onSubmit } = renderForm({
      ...empty,
      categoryId: 'c-fixed',
      subcategoryId: 's-cdb',
      ticker: 'OLD11',
      segmentId: 'seg-shop',
    })
    expect(screen.getByRole('textbox', { name: 'Ticker' })).toHaveValue('OLD11')
    expect(segment()).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ ticker: 'OLD11', segmentId: 'seg-shop' }),
    )
  })
})
