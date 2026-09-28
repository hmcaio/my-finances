import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { JsonBodyType, RequestHandler } from 'msw'
import type { ReactElement } from 'react'
import { server } from '../mocks/server'
import { findRow } from './testUtils'
import { renderWithQueryClient } from './renderWithQueryClient'
import { expectLoadStates } from './loadStates'

/** Config for {@link describeSettingsPage}. */
export interface SettingsPageContractConfig<Entity extends { id: string; name: string }> {
  /** The page component to render, e.g. `<PaymentMethodsPage />`. Rendered fresh per `it()`. */
  page: ReactElement
  /** Every seeded row expected to render on first load. */
  seedRows: Entity[]
  /** An existing row renamed inline in the "renames" case. */
  renameTarget: Entity
  /** An existing row deleted in both the "deletes" case and the delete-conflict case (each `it()`
   * gets its own render/MSW state, so reusing one row across the two is safe). */
  deleteTarget: Entity
  /** Name typed into the add form for the "adds a new row" case. */
  newName: string
  /**
   * Accessible name of the header button that opens the add dialog (F021: the add form moved off
   * a below-table panel into a `ResponsiveDialog`, e.g. "Add category", "Add institution"). The
   * dialog's own submit button is always the generic "Add".
   */
  addButtonLabel: string
  conflict: {
    /** The delete-conflict message (`CONFLICT_MESSAGE` in the entity's api client). */
    message: string
    handler: RequestHandler
  }
  duplicateName: {
    /** The duplicate-name message (`DUPLICATE_NAME_MESSAGE` in the entity's api client). */
    message: string
    handler: RequestHandler
  }
  /**
   * Only set for a page whose Name input is actually capped (`INSTITUTION_NAME_MAX_LENGTH` etc.) -
   * omit it rather than fail the generated case against a page that doesn't implement one yet
   * (`PaymentMethodsPage`/`CategoriesPage` don't; see this PR's report).
   */
  maxLength?: number
  /**
   * Set to also generate the F2 load-state pair (slow-first-load skeleton, failed-load notice +
   * working Retry) for this page, instead of writing it out again in the page's own file.
   */
  loadStates?: {
    url: string
    successBody: JsonBodyType
  }
}

/**
 * Runs the case set every settings-style CRUD page shares (renders seeded rows, adds, renames
 * inline, deletes, and the delete/duplicate-name 409s) against the given config, so a page missing
 * one of them (F015 audit's F5: duplicate-name-on-add wasn't tested for `CategoriesPage`/
 * `PaymentMethodsPage`) gets it automatically. `InvestmentCategoriesPage` doesn't use this - its
 * two-level category/sub-category structure and different field labels don't fit this shape.
 * Bespoke behaviour a page has on top of this contract (built-in-row protection, type immutability)
 * stays as additional `it()`s in that page's own file, layered on top of what this generates.
 */
export function describeSettingsPage<Entity extends { id: string; name: string }>(
  config: SettingsPageContractConfig<Entity>,
): void {
  const {
    page,
    seedRows,
    renameTarget,
    deleteTarget,
    newName,
    addButtonLabel,
    conflict,
    duplicateName,
    maxLength,
  } = config

  it('renders the seeded rows', async () => {
    renderWithQueryClient(page)

    for (const row of seedRows) {
      expect(await screen.findByText(row.name)).toBeInTheDocument()
    }
  })

  it('adds a new row through the header dialog', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(page)
    await screen.findByText(seedRows[0].name)

    await user.click(screen.getByRole('button', { name: addButtonLabel }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Name'), newName)
    await user.click(within(dialog).getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(newName)).toBeInTheDocument()
  })

  it('renames a row inline', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(page)
    await screen.findByText(renameTarget.name)
    const renamed = `${renameTarget.name} (renamed)`

    const row = await findRow(renameTarget.name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox')
    await user.clear(input)
    await user.type(input, renamed)
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText(renamed)).toBeInTheDocument()
    expect(screen.queryByText(renameTarget.name)).not.toBeInTheDocument()
  })

  it('deletes a row', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(page)
    await screen.findByText(deleteTarget.name)

    const row = await findRow(deleteTarget.name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText(deleteTarget.name)).not.toBeInTheDocument())
  })

  it('surfaces the 409 conflict message when delete fails', async () => {
    server.use(conflict.handler)
    const user = userEvent.setup()
    renderWithQueryClient(page)
    await screen.findByText(deleteTarget.name)

    const row = await findRow(deleteTarget.name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(conflict.message)).toBeInTheDocument()
    // The row is still there - a 409 must not optimistically remove it.
    expect(screen.getByText(deleteTarget.name)).toBeInTheDocument()
  })

  it('surfaces the duplicate-name message when adding fails', async () => {
    server.use(duplicateName.handler)
    const user = userEvent.setup()
    renderWithQueryClient(page)
    await screen.findByText(seedRows[0].name)

    await user.click(screen.getByRole('button', { name: addButtonLabel }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Name'), seedRows[0].name)
    await user.click(within(dialog).getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(duplicateName.message)).toBeInTheDocument()
  })

  if (maxLength !== undefined) {
    it('caps the name inputs at the backend length limit', async () => {
      const user = userEvent.setup()
      renderWithQueryClient(page)
      await screen.findByText(seedRows[0].name)

      await user.click(screen.getByRole('button', { name: addButtonLabel }))
      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByLabelText('Name')).toHaveAttribute('maxlength', String(maxLength))
      // Close the dialog before touching the row below: it's aria-hidden while the dialog is open.
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

      const row = await findRow(seedRows[0].name)
      await user.click(row.getByRole('button', { name: 'Rename' }))
      expect(row.getByRole('textbox')).toHaveAttribute('maxlength', String(maxLength))
    })
  }

  if (config.loadStates) {
    expectLoadStates({
      render: () => renderWithQueryClient(page),
      url: config.loadStates.url,
      successBody: config.loadStates.successBody,
      loadedText: seedRows[0].name,
    })
  }
}

/** Wraps {@link describeSettingsPage} in its own `describe(...)` for a page test file that has no
 * other bespoke cases to add alongside it (`PaymentMethodsPage`). Pages with bespoke cases
 * (`CategoriesPage`, `InstitutionsPage`) call `describeSettingsPage` directly inside their own
 * `describe(...)` instead, so the bespoke `it()`s sit in the same block. */
export function describeSettingsPageOnly<Entity extends { id: string; name: string }>(
  label: string,
  config: SettingsPageContractConfig<Entity>,
): void {
  describe(label, () => describeSettingsPage(config))
}
