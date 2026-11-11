import { useRef, type KeyboardEvent, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { TextField, Typography } from '@mui/material'
import type { SnapshotSheetRow } from '../../../../api/investments/products/investmentSnapshotSheet'
import {
  ResponsiveTable,
  type ResponsiveColumn,
} from '../../../../components/table/ResponsiveTable'
import type { LoadState } from '../../../../hooks/queryState'
import { formatDate, formatMoney } from '../../../../i18n/format'
import { useLocale } from '../../../../providers/locale/useLocale'
import { checkBalance, differenceOf, type SheetInputs } from '../lib/snapshotSheet'

/** A plain `YYYY-MM-DD` date: formatted in UTC so no local time zone shifts it a day. */
const DATE_OPTIONS: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeZone: 'UTC' }

interface SnapshotSheetTableProps {
  rows: SnapshotSheetRow[] | undefined
  state: LoadState
  inputs: SheetInputs
  onInputChange: (holdingId: string, value: string) => void
  /** Why a row is flagged (its own bad input, or what the server said), if it is. */
  rowError: (holdingId: string) => string | undefined
  /** Where Enter in the last row's balance goes. */
  saveButtonRef: RefObject<HTMLButtonElement | null>
}

/**
 * The sheet's one row per open holding (F040): Product, Account, Previous balance, the Balance
 * input and the live Difference. A table from `sm`, cards on a phone (`ResponsiveTable`); the
 * balance input renders in both layouts, each with an `aria-label` naming its product and account
 * (many identical "Balance" fields would otherwise be indistinguishable). Enter in a balance moves
 * to the next row's balance and, in the last row, to Save.
 */
export function SnapshotSheetTable({
  rows,
  state,
  inputs,
  onInputChange,
  rowError,
  saveButtonRef,
}: SnapshotSheetTableProps) {
  const { t } = useTranslation('investmentSnapshots')
  const { locale } = useLocale()
  const inputRefs = useRef(new Map<string, HTMLInputElement>())

  function moveFocusFrom(holdingId: string) {
    const index = (rows ?? []).findIndex((row) => row.holdingId === holdingId)
    const next = (rows ?? [])[index + 1]
    if (next) inputRefs.current.get(next.holdingId)?.focus()
    else saveButtonRef.current?.focus()
  }

  function handleKeyDown(event: KeyboardEvent, holdingId: string) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
    // Not a form submit: Enter walks down the rows, and Save is its own deliberate action.
    event.preventDefault()
    moveFocusFrom(holdingId)
  }

  const columns: ResponsiveColumn<SnapshotSheetRow>[] = [
    {
      key: 'product',
      header: t('sheet.columns.product'),
      role: 'primary',
      render: (row) => (
        <>
          {row.productName}
          {row.ticker && (
            <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
              {row.ticker}
            </Typography>
          )}
        </>
      ),
    },
    {
      key: 'account',
      header: t('sheet.columns.account'),
      role: 'secondary',
      render: (row) => row.accountName,
    },
    {
      key: 'previous',
      header: t('sheet.columns.previous'),
      align: 'right',
      render: (row) =>
        row.latestSnapshot ? (
          <>
            {formatMoney(row.latestSnapshot.balance, locale)}
            <Typography variant="caption" color="text.secondary" component="div">
              {formatDate(row.latestSnapshot.date, locale, DATE_OPTIONS)}
            </Typography>
          </>
        ) : (
          <Typography variant="body2" color="text.secondary" component="span">
            {t('sheet.noPrevious')}
          </Typography>
        ),
    },
    {
      key: 'balance',
      header: t('sheet.columns.balance'),
      align: 'right',
      render: (row) => {
        const error = rowError(row.holdingId)
        return (
          <TextField
            size="small"
            type="number"
            value={inputs[row.holdingId] ?? ''}
            onChange={(event) => onInputChange(row.holdingId, event.target.value)}
            onKeyDown={(event) => handleKeyDown(event, row.holdingId)}
            placeholder={
              row.latestSnapshot ? formatMoney(row.latestSnapshot.balance, locale) : undefined
            }
            error={error !== undefined}
            helperText={error}
            inputRef={(element: HTMLInputElement | null) => {
              if (element) inputRefs.current.set(row.holdingId, element)
              else inputRefs.current.delete(row.holdingId)
            }}
            slotProps={{
              htmlInput: {
                step: '0.01',
                min: '0',
                inputMode: 'decimal',
                'aria-label': t('sheet.balanceAria', {
                  product: row.productName,
                  account: row.accountName,
                }),
              },
            }}
            sx={{ width: { xs: '100%', sm: 160 } }}
          />
        )
      },
    },
    {
      key: 'difference',
      header: t('sheet.columns.difference'),
      align: 'right',
      render: (row) => {
        const check = checkBalance(inputs[row.holdingId] ?? '')
        const difference =
          check.kind === 'valid'
            ? differenceOf(check.value, row.latestSnapshot?.balance ?? null)
            : null
        if (difference === null) {
          return (
            <Typography variant="body2" color="text.secondary" component="span">
              {t('sheet.noDifference')}
            </Typography>
          )
        }
        const sign = difference > 0 ? '+' : ''
        return (
          <Typography
            variant="body2"
            component="span"
            sx={{
              color:
                difference > 0 ? 'success.main' : difference < 0 ? 'error.main' : 'text.secondary',
            }}
          >
            {sign + formatMoney(difference, locale)}
          </Typography>
        )
      },
    },
  ]

  return (
    <ResponsiveTable
      columns={columns}
      rows={rows}
      getRowKey={(row) => row.holdingId}
      state={state}
      aria-label={t('sheet.tableAria')}
    />
  )
}
