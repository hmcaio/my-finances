import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Chip, IconButton, Paper, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import type { LedgerEntry } from '../../api/ledger/ledger'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import type { LoadState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatEntryAmount, ledgerEntryStyle } from './ledgerEntryStyle'
import type { LedgerLookups } from './useLedgerLookups'

/** Buy/Sell chips shown on a trade confirmation before collapsing into "+N more" (F027). */
const MAX_TRADE_CHIPS = 3

interface LedgerTableProps {
  entries: LedgerEntry[] | undefined
  /** Skeleton / failure + Retry, as `ResponsiveTable` takes it (usually `combineLoadState`). */
  state: LoadState
  onRetry?: () => void
  lookups: LedgerLookups
  /** Row actions. Omit both for a read-only table (the account page's embed). */
  onEdit?: (entry: LedgerEntry) => void
  onDelete?: (entry: LedgerEntry) => void
  /** Drops the table's outlined frame for use inside an already-outlined section. */
  embedded?: boolean
  emptyMessage?: string
  'aria-label'?: string
}

const rowKey = (entry: LedgerEntry) =>
  `${entry.type === 'TRANSFER' ? 'transfer' : 'tx'}-${entry.id}`

/**
 * The Ledger's list (F037): transactions and transfers as one table (cards below `sm`), shared by
 * the Ledger page and the account page's read-only embed. Income, expense and transfer rows are
 * told apart by icon, sign and colour together (never colour alone); a transfer shows
 * `From -> To` in the account cell, `-` for category and payment method, and an unsigned neutral
 * amount. A row's edit and delete buttons call `onEdit`/`onDelete`; without them the table is
 * read-only.
 */
export function LedgerTable({
  entries,
  state,
  onRetry,
  lookups,
  onEdit,
  onDelete,
  embedded,
  emptyMessage,
  'aria-label': ariaLabel,
}: LedgerTableProps) {
  const { t } = useTranslation(['ledger', 'transfers', 'common'])
  const { locale } = useLocale()

  /** A "Buy X"/"Sell Y" label per distinct line of a trade confirmation, collapsed past a few. */
  function tradeLabels(entry: LedgerEntry): string[] {
    const labels = [
      ...new Set(
        entry.tradeLines.map((line) =>
          t(line.side === 'BUY' ? 'transfers:tradeLabelBuy' : 'transfers:tradeLabelSell', {
            product: lookups.productName(line.productId),
          }),
        ),
      ),
    ]
    if (labels.length <= MAX_TRADE_CHIPS) return labels
    return [
      ...labels.slice(0, MAX_TRADE_CHIPS),
      t('transfers:moreChips', { count: labels.length - MAX_TRADE_CHIPS }),
    ]
  }

  const typeIcon = (entry: LedgerEntry) => {
    const { Icon, color, labelKey } = ledgerEntryStyle(entry.type)
    return (
      <Box
        component="span"
        role="img"
        aria-label={t(labelKey)}
        sx={{ display: 'inline-flex', color: `${color}.main`, verticalAlign: 'middle' }}
      >
        <Icon fontSize="small" />
      </Box>
    )
  }

  const amountText = (entry: LedgerEntry) => formatEntryAmount(entry.type, entry.amount, locale)

  const amountColor = (entry: LedgerEntry) => {
    const { color } = ledgerEntryStyle(entry.type)
    return entry.type === 'TRANSFER' ? 'text.primary' : `${color}.main`
  }

  const dash = (
    <Box component="span" sx={{ color: 'text.secondary' }}>
      —
    </Box>
  )

  const accountCell = (entry: LedgerEntry): ReactNode => {
    if (entry.type !== 'TRANSFER') return lookups.accountName(entry.accountId ?? '')
    return (
      <>
        {`${lookups.accountName(entry.fromAccountId ?? '')} → ${lookups.accountName(entry.toAccountId ?? '')}`}
        {tradeLabels(entry).map((label, i) => (
          <Chip key={i} label={label} size="small" sx={{ ml: 1, mt: 0.5 }} />
        ))}
      </>
    )
  }

  const columns: ResponsiveColumn<LedgerEntry>[] = [
    { key: 'date', header: t('ledger:columns.date'), render: (entry) => entry.date },
    { key: 'type', header: t('ledger:columns.type'), render: typeIcon },
    {
      key: 'description',
      header: t('ledger:columns.description'),
      render: (entry) => entry.description,
    },
    {
      key: 'category',
      header: t('ledger:columns.category'),
      render: (entry) => (entry.categoryId ? lookups.categoryName(entry.categoryId) : dash),
    },
    { key: 'account', header: t('ledger:columns.account'), render: accountCell },
    {
      key: 'paymentMethod',
      header: t('ledger:columns.paymentMethod'),
      render: (entry) =>
        entry.paymentMethodId ? lookups.paymentMethodName(entry.paymentMethodId) : dash,
      tabletPriority: 'low',
    },
    {
      key: 'amount',
      header: t('ledger:columns.amount'),
      align: 'right',
      render: (entry) => (
        <Box
          component="span"
          sx={{ color: amountColor(entry), fontWeight: entry.type === 'TRANSFER' ? 400 : 600 }}
        >
          {amountText(entry)}
        </Box>
      ),
    },
  ]

  const actions =
    onEdit || onDelete
      ? (entry: LedgerEntry) => (
          <>
            {onEdit && (
              <IconButton
                size="small"
                aria-label={t('common:actions.edit')}
                onClick={() => onEdit(entry)}
              >
                <EditIcon fontSize="small" />
              </IconButton>
            )}
            {onDelete && (
              <IconButton
                size="small"
                aria-label={t('common:actions.delete')}
                onClick={() => onDelete(entry)}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            )}
          </>
        )
      : undefined

  // The card is ordered for a phone: what and how much first, then when and where, with a left
  // border in the row's colour so income, expense and transfer read apart at a glance.
  function renderCard(entry: LedgerEntry, rowActions: ReactNode) {
    const { color } = ledgerEntryStyle(entry.type)
    const isTransfer = entry.type === 'TRANSFER'
    return (
      <Paper
        variant="outlined"
        sx={{ p: 1.5, borderLeftWidth: 4, borderLeftColor: `${color}.main` }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Typography
            variant="subtitle1"
            component="div"
            sx={{ overflowWrap: 'anywhere', display: 'flex', gap: 0.75, alignItems: 'flex-start' }}
          >
            {typeIcon(entry)}
            <span>{entry.description}</span>
          </Typography>
          <Typography
            variant="subtitle1"
            component="div"
            sx={{
              flexShrink: 0,
              fontWeight: isTransfer ? 400 : 600,
              color: amountColor(entry),
            }}
          >
            {amountText(entry)}
          </Typography>
        </Box>
        <Typography
          variant="body2"
          component="div"
          color="text.secondary"
          sx={{ overflowWrap: 'anywhere' }}
        >
          {isTransfer
            ? entry.date
            : `${entry.date} · ${lookups.categoryName(entry.categoryId ?? '')}`}
        </Typography>
        <Typography
          variant="body2"
          component="div"
          color="text.secondary"
          sx={{ overflowWrap: 'anywhere' }}
        >
          {isTransfer
            ? accountCell(entry)
            : `${lookups.accountName(entry.accountId ?? '')} · ${lookups.paymentMethodName(entry.paymentMethodId ?? '')}`}
        </Typography>
        {rowActions && (
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 0.5 }}>{rowActions}</Box>
        )}
      </Paper>
    )
  }

  return (
    <ResponsiveTable
      aria-label={ariaLabel ?? t('ledger:aria')}
      columns={columns}
      rows={entries}
      getRowKey={rowKey}
      state={state}
      onRetry={onRetry}
      actions={actions}
      renderCard={renderCard}
      embedded={embedded}
      emptyMessage={emptyMessage ?? t('ledger:empty.noEntries')}
    />
  )
}
