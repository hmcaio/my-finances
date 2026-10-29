import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
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

describe('InvestmentProductForm opening position section (F038)', () => {
  afterEach(() => vi.useRealTimers())

  const section = () => screen.queryByRole('button', { name: 'Opening position (optional)' })
  const save = () => screen.getByRole('button', { name: 'Save' })

  async function readyToSubmit(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByRole('textbox', { name: 'Product name' }), 'KNRI11 Test')
    await selectOption(user, 'Category', 'Variable Income')
    await selectOption(user, 'Sub-category', 'REITs (FIIs)')
  }

  it('is offered on create for every product, collapsed, and absent when editing', () => {
    renderForm()
    expect(section()).toBeInTheDocument()
    expect(section()).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByLabelText('Cotas')).not.toBeInTheDocument()
  })

  it('is not offered on the edit form', () => {
    renderForm(empty)
    expect(section()).not.toBeInTheDocument()
  })

  it('is optional: submitting without touching it sends no openingPosition', async () => {
    const { user, onSubmit } = renderForm()
    await readyToSubmit(user)
    await user.click(save())

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty('openingPosition')
  })

  it('defaults the as-of date to today', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 29, 22, 30))
    const { user } = renderForm()
    await user.click(section()!)

    expect(screen.getByLabelText('As of')).toHaveValue('2026-10-29')
  })

  it('once started, requires cotas and average price but not the current value', async () => {
    const { user, onSubmit } = renderForm()
    await readyToSubmit(user)
    await user.click(section()!)

    await user.type(screen.getByLabelText('Cotas'), '40')
    expect(save()).toBeDisabled()
    await user.type(screen.getByLabelText('Average price'), '150')
    expect(save()).toBeEnabled()
    expect(screen.getByText(/flagged as needing a snapshot/)).toBeInTheDocument()

    await user.click(save())
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        openingPosition: { asOfDate: expect.any(String), quantity: 40, unitPrice: 150 },
      }),
    )
    expect(onSubmit.mock.calls[0][0].openingPosition).not.toHaveProperty('currentValue')
  })

  it('a current value alone also starts the section and blocks Save', async () => {
    const { user } = renderForm()
    await readyToSubmit(user)
    await user.click(section()!)

    await user.type(screen.getByLabelText('Current value (optional)'), '6400')

    expect(save()).toBeDisabled()
  })

  it('submits the filled section with the current value', async () => {
    const { user, onSubmit } = renderForm()
    await readyToSubmit(user)
    await user.click(section()!)
    const region = within(screen.getByRole('region'))
    await user.type(region.getByLabelText('Cotas'), '40')
    await user.type(region.getByLabelText('Average price'), '150.5')
    await user.type(region.getByLabelText('Current value (optional)'), '6400')
    await user.click(save())

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        openingPosition: expect.objectContaining({
          quantity: 40,
          unitPrice: 150.5,
          currentValue: 6400,
        }),
      }),
    )
  })

  it('keeps what was typed when the section is collapsed again', async () => {
    const { user } = renderForm()
    await user.click(section()!)
    await user.type(screen.getByLabelText('Cotas'), '12')
    await user.click(section()!)
    await user.click(section()!)

    expect(screen.getByLabelText('Cotas')).toHaveValue(12)
  })
})
