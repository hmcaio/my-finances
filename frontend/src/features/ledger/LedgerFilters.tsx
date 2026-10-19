import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  IconButton,
  InputAdornment,
  MenuItem,
  Select,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material'
import ClearIcon from '@mui/icons-material/Clear'
import type { Account } from '../../api/accounts/accounts'
import type { Category } from '../../api/categories/categories'
import type { PaymentMethod } from '../../api/paymentMethods/paymentMethods'
import { MonthNavigator } from '../../components/inputs/MonthNavigator'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { translateSeedName } from '../../i18n/seedNames'
import { currentMonth, monthRange } from '../../utils/localDate'
import {
  activeFilterCount,
  MAX_SEARCH_LENGTH,
  type LedgerTypeParam,
  type LedgerView,
} from './ledgerSearchParams'

/** How long typing must pause before the search text reaches the URL (and so the request). */
const SEARCH_DEBOUNCE_MS = 300

interface LedgerFiltersProps {
  view: LedgerView
  onChange: (patch: Partial<LedgerView>, options?: { replace?: boolean }) => void
  accounts: Account[] | undefined
  categories: Category[] | undefined
  paymentMethods: PaymentMethod[] | undefined
}

/**
 * The Ledger's period and filter controls (F037). The period row - a Month/Range toggle with the
 * `‹ month ›` navigator or from/to dates - is always visible, since stepping through months is the
 * main interaction. The rest (type, account, category, payment method, search) sit in a
 * `ResponsiveFilterBar`, a "Filters" sheet below `sm`. Choosing the Transfer type clears and
 * disables category and payment method, because a transfer has neither (PRD S5.5). The search box
 * keeps its own text and writes it to the URL (`replace`, not `push`) once typing pauses.
 */
export function LedgerFilters({
  view,
  onChange,
  accounts,
  categories,
  paymentMethods,
}: LedgerFiltersProps) {
  const { t } = useTranslation(['ledger', 'common', 'transactions'])
  const isTransfer = view.type === 'transfer'

  // --- search text: local while typing, debounced into the URL ---------------------------------
  const [text, setText] = useState(view.q)
  const debounced = useDebouncedValue(text, SEARCH_DEBOUNCE_MS)
  const latest = useRef({ q: view.q, onChange })
  useEffect(() => {
    latest.current = { q: view.q, onChange }
  })
  useEffect(() => {
    // Only the debounced text changing may write; reading the rest through the ref keeps an
    // external change of `q` (Clear, back button) from being overwritten by a stale debounced value.
    if (debounced !== latest.current.q) latest.current.onChange({ q: debounced }, { replace: true })
  }, [debounced])
  // An external change of `q` (Clear filters, history navigation) updates the box; our own write
  // arrives as `q === debounced` and must not clobber text typed since.
  const [syncedQ, setSyncedQ] = useState(view.q)
  if (view.q !== syncedQ) {
    setSyncedQ(view.q)
    if (view.q !== debounced) setText(view.q)
  }

  function changeScope(scope: 'month' | 'range' | null) {
    if (!scope || scope === view.scope) return
    if (scope === 'range') {
      const { from, to } = monthRange(view.month)
      onChange({ scope: 'range', from, to })
    } else {
      onChange({ scope: 'month', month: view.from ? view.from.slice(0, 7) : currentMonth() })
    }
  }

  function changeType(type: LedgerTypeParam) {
    onChange(type === 'transfer' ? { type, category: '', paymentMethod: '' } : { type })
  }

  function clearFilters() {
    setText('')
    onChange({ type: '', account: '', category: '', paymentMethod: '', q: '' })
  }

  return (
    <>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center', mb: 2 }}>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={view.scope}
          onChange={(_, value: 'month' | 'range' | null) => changeScope(value)}
          aria-label={t('ledger:period.scopeAria')}
        >
          <ToggleButton value="month">{t('ledger:period.month')}</ToggleButton>
          <ToggleButton value="range">{t('ledger:period.range')}</ToggleButton>
        </ToggleButtonGroup>
        {view.scope === 'month' ? (
          <MonthNavigator
            value={view.month}
            label={t('ledger:period.monthLabel')}
            onChange={(month) => onChange({ month })}
          />
        ) : (
          <>
            <TextField
              label={t('ledger:period.fromLabel')}
              type="date"
              size="small"
              value={view.from}
              onChange={(e) => onChange({ from: e.target.value })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label={t('ledger:period.toLabel')}
              type="date"
              size="small"
              value={view.to}
              onChange={(e) => onChange({ to: e.target.value })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </>
        )}
      </Box>

      <ResponsiveFilterBar activeCount={activeFilterCount(view)} onClear={clearFilters}>
        <Select
          size="small"
          displayEmpty
          value={view.type}
          onChange={(e) => changeType(e.target.value)}
          aria-label={t('ledger:filters.typeAria')}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">{t('ledger:filters.allTypes')}</MenuItem>
          <MenuItem value="income">{t('ledger:type.income')}</MenuItem>
          <MenuItem value="expense">{t('ledger:type.expense')}</MenuItem>
          <MenuItem value="transfer">{t('ledger:type.transfer')}</MenuItem>
        </Select>
        <Select
          size="small"
          displayEmpty
          value={accounts?.some((a) => a.id === view.account) ? view.account : ''}
          onChange={(e) => onChange({ account: e.target.value })}
          aria-label={t('ledger:filters.accountAria')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('ledger:filters.allAccounts')}</MenuItem>
          {accounts?.map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          displayEmpty
          disabled={isTransfer}
          value={categories?.some((c) => c.id === view.category) ? view.category : ''}
          onChange={(e) => onChange({ category: e.target.value })}
          aria-label={t('ledger:filters.categoryAria')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('ledger:filters.allCategories')}</MenuItem>
          {categories?.map((c) => (
            <MenuItem key={c.id} value={c.id}>
              {translateSeedName('categories', c.name, t)}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          displayEmpty
          disabled={isTransfer}
          value={paymentMethods?.some((p) => p.id === view.paymentMethod) ? view.paymentMethod : ''}
          onChange={(e) => onChange({ paymentMethod: e.target.value })}
          aria-label={t('ledger:filters.paymentMethodAria')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('ledger:filters.allPaymentMethods')}</MenuItem>
          {paymentMethods?.map((p) => (
            <MenuItem key={p.id} value={p.id}>
              {translateSeedName('paymentMethods', p.name, t)}
            </MenuItem>
          ))}
        </Select>
        <TextField
          size="small"
          label={t('ledger:filters.searchLabel')}
          placeholder={t('ledger:filters.searchPlaceholder')}
          value={text}
          onChange={(e) => setText(e.target.value)}
          slotProps={{
            htmlInput: { maxLength: MAX_SEARCH_LENGTH },
            input: {
              endAdornment: text ? (
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    edge="end"
                    aria-label={t('ledger:filters.clearSearch')}
                    onClick={() => {
                      setText('')
                      onChange({ q: '' }, { replace: true })
                    }}
                  >
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
            },
          }}
        />
      </ResponsiveFilterBar>
    </>
  )
}
