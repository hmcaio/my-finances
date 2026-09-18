import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Table, TableBody } from '@mui/material'
import { LoadingTableRow } from './LoadingTableRow'

describe('LoadingTableRow', () => {
  it('renders a spinner by default', () => {
    render(
      <Table>
        <TableBody>
          <LoadingTableRow colSpan={4} />
        </TableBody>
      </Table>,
    )

    expect(screen.getByRole('progressbar')).toBeInTheDocument()
  })

  it('renders "Loading…" text when variant is text', () => {
    render(
      <Table>
        <TableBody>
          <LoadingTableRow colSpan={4} variant="text" />
        </TableBody>
      </Table>,
    )

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })
})
