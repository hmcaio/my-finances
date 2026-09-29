import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  BUILT_IN_INSTITUTION_ID,
  institutionCreateConflictHandler,
  seedInstitutions,
} from '../../mocks/handlers/institutions'
import { DUPLICATE_NAME_MESSAGE, type Institution } from '../../api/institutions/institutions'
import { InstitutionSelect } from './InstitutionSelect'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

/** A parent that owns the value, like a form does. */
function Harness({
  initial,
  onChange,
  onCreated,
}: {
  initial?: string
  onChange?: (id: string) => void
  onCreated?: (institution: Institution) => void
}) {
  const [value, setValue] = useState<string | undefined>(initial)
  return (
    <InstitutionSelect
      value={value}
      onChange={(id) => {
        onChange?.(id)
        setValue(id)
      }}
      onCreated={onCreated}
    />
  )
}

const input = () => screen.getByRole('combobox', { name: 'Institution' })

describe('InstitutionSelect', () => {
  it('lists the built-in row first, then the rest by the list order', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<Harness initial="inst-1" />)
    await user.click(await screen.findByRole('combobox', { name: 'Institution' }))

    const options = await screen.findAllByRole('option')

    expect(options.map((o) => o.textContent)).toEqual(['No institution', 'Itau', 'Nubank'])
  })

  it('defaults to the built-in row when no value is passed, and reports it', async () => {
    const onChange = vi.fn()
    renderWithQueryClient(<Harness onChange={onChange} />)

    expect(await screen.findByRole('combobox', { name: 'Institution' })).toHaveValue(
      'No institution',
    )
    expect(onChange).toHaveBeenCalledWith(BUILT_IN_INSTITUTION_ID)
    expect(onChange).toHaveBeenCalledTimes(1)
  })

  it('shows the institution it is given instead of the default, and does not override it', async () => {
    const onChange = vi.fn()
    renderWithQueryClient(<Harness initial="inst-2" onChange={onChange} />)

    expect(await screen.findByRole('combobox', { name: 'Institution' })).toHaveValue('Nubank')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('selects another institution', async () => {
    const user = userEvent.setup({ delay: null })
    const onChange = vi.fn()
    renderWithQueryClient(<Harness onChange={onChange} />)
    await user.click(await screen.findByRole('combobox', { name: 'Institution' }))

    await user.click(await screen.findByRole('option', { name: 'Nubank' }))

    expect(onChange).toHaveBeenLastCalledWith('inst-2')
    expect(input()).toHaveValue('Nubank')
  })

  it('is not clearable', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<Harness initial="inst-1" />)
    await user.click(await screen.findByRole('combobox', { name: 'Institution' }))

    expect(screen.queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()
  })

  it('caps the typed name at the backend length limit', async () => {
    renderWithQueryClient(<Harness initial="inst-1" />)

    expect(await screen.findByRole('combobox', { name: 'Institution' })).toHaveAttribute(
      'maxlength',
      '100',
    )
  })

  it('offers "Add “X”" for a new name, creates it, then selects it', async () => {
    const user = userEvent.setup({ delay: null })
    const onChange = vi.fn()
    const onCreated = vi.fn()
    renderWithQueryClient(<Harness initial="inst-1" onChange={onChange} onCreated={onCreated} />)
    await user.click(await screen.findByRole('combobox', { name: 'Institution' }))

    await user.clear(input())
    await user.type(input(), 'Inter')
    await user.click(await screen.findByRole('option', { name: 'Add “Inter”' }))

    expect(await screen.findByDisplayValue('Inter')).toBeInTheDocument()
    expect(onCreated).toHaveBeenCalledWith({ id: 'inst-new', name: 'Inter', builtIn: false })
    expect(onChange).toHaveBeenLastCalledWith('inst-new')

    // The new institution is now one of the options.
    await user.click(input())
    expect(await screen.findByRole('option', { name: 'Inter' })).toBeInTheDocument()
  })

  it('does not offer to add a name that already exists', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<Harness initial="inst-1" />)
    await user.click(await screen.findByRole('combobox', { name: 'Institution' }))

    await user.clear(input())
    await user.type(input(), 'Nubank')

    expect(await screen.findByRole('option', { name: 'Nubank' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /^Add/ })).not.toBeInTheDocument()
  })

  it('shows an error and keeps the previous selection when creating fails', async () => {
    server.use(institutionCreateConflictHandler)
    const user = userEvent.setup({ delay: null })
    const onChange = vi.fn()
    renderWithQueryClient(<Harness initial="inst-1" onChange={onChange} />)
    await user.click(await screen.findByRole('combobox', { name: 'Institution' }))

    await user.clear(input())
    await user.type(input(), 'Inter')
    await user.click(await screen.findByRole('option', { name: 'Add “Inter”' }))

    expect(await screen.findByText(DUPLICATE_NAME_MESSAGE)).toBeInTheDocument()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('shows a retryable notice when the list cannot be loaded', async () => {
    let fail = true
    server.use(
      http.get('/api/institutions', () =>
        fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(seedInstitutions),
      ),
    )
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<Harness initial="inst-1" />)

    expect(await screen.findByText(/Could not load data/)).toBeInTheDocument()

    fail = false
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByRole('combobox', { name: 'Institution' })).toHaveValue('Itau')
  })
})
