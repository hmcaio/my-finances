import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, IconButton, MenuItem, TextField, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import type { Goal } from '../../../api/goals/goals'
import { useGoals, useReplaceGoalAllocations } from '../../../api/goals/goalsQueries'
import { useInvestmentProducts } from '../../../api/investments/products/investmentProductsQueries'
import { ErrorAlert } from '../../../components/feedback/ErrorAlert'
import { LoadFailedNotice } from '../../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../../hooks/queryState'
import { useAction } from '../../../hooks/useAction'
import { formatNumber } from '../../../i18n/format'
import { useLocale } from '../../../providers/locale/useLocale'
import { hasAtMostTwoDecimals } from '../lib/goalFormat'

/** One editable row: a product (empty until chosen) and the percentage as typed. */
interface Row {
  key: number
  productId: string
  percentage: string
}

function rowsOf(goal: Goal): Row[] {
  return goal.allocations.map((a, index) => ({
    key: index,
    productId: a.productId,
    percentage: String(a.percentage),
  }))
}

/** A string that changes exactly when the stored allocations do, to resync the draft. */
function allocationsKey(goal: Goal): string {
  return JSON.stringify(goal.allocations.map((a) => [a.productId, a.percentage]))
}

/**
 * The allocation editor of a goal (PRD S6.17, F039): the investment products that fund it and the
 * percentage of each, replaced as one set. Each row shows the share of that product still
 * available (100% minus what the *other* non-archived goals hold, which the goals list already
 * carries) and what stays unassigned after this goal's share; a percentage above the available
 * share is rejected here and again by the backend (`409`). An archived goal's allocation is
 * read-only until it is restored.
 */
export function GoalAllocationEditor({ goal }: { goal: Goal }) {
  const { t } = useTranslation(['goals', 'common'])
  const { locale } = useLocale()
  const [error, setError] = useState<string | null>(null)
  const serverKey = allocationsKey(goal)
  const [syncedKey, setSyncedKey] = useState(serverKey)
  const [rows, setRows] = useState<Row[]>(() => rowsOf(goal))
  const [nextKey, setNextKey] = useState(goal.allocations.length)
  if (syncedKey !== serverKey) {
    // The stored set changed (a save, or an edit elsewhere): start the draft from it again.
    setSyncedKey(serverKey)
    setRows(rowsOf(goal))
    setNextKey(goal.allocations.length)
  }

  const productsQuery = useInvestmentProducts()
  const products = productsQuery.data
  const productsState = useQueryState(productsQuery)
  const goalsQuery = useGoals(false)
  const capacityKnown = goalsQuery.data !== undefined
  const otherGoals = (goalsQuery.data ?? []).filter((g) => g.id !== goal.id)
  const replaceMutation = useReplaceGoalAllocations()
  const saveAction = useAction(setError)

  const archived = goal.status === 'ARCHIVED'
  // Until the product list arrives, the stored allocations already carry their products' names,
  // so a row can name (and a select can show) its product at once.
  const catalog =
    products ??
    goal.allocations.map((a) => ({ id: a.productId, name: a.productName, closed: false }))
  const nameOf = (productId: string) => catalog.find((p) => p.id === productId)?.name ?? ''

  /** What is left of a product once the other non-archived goals have taken their shares. */
  function availableOf(productId: string): number {
    const taken = otherGoals
      .flatMap((g) => g.allocations)
      .filter((a) => a.productId === productId)
      .reduce((sum, a) => sum + a.percentage, 0)
    return Math.max(0, 100 - taken)
  }

  const percent = (value: number) => formatNumber(value, locale, Number.isInteger(value) ? 0 : 2)

  if (archived) {
    return (
      <Box>
        <Typography color="text.secondary" sx={{ mb: 1 }}>
          {t('goals:allocation.archivedNote')}
        </Typography>
        {goal.allocations.map((a) => (
          <Typography key={a.productId}>
            {a.productName}: {percent(a.percentage)}%
          </Typography>
        ))}
      </Box>
    )
  }

  if (productsState.loadError) {
    return <LoadFailedNotice message={productsState.loadError} onRetry={productsState.reload} />
  }

  const chosen = new Set(rows.map((r) => r.productId).filter((id) => id !== ''))
  const optionsFor = (row: Row) =>
    catalog.filter((p) => p.id === row.productId || (!p.closed && !chosen.has(p.id)))

  function problemOf(row: Row): string | null {
    if (row.productId === '') return null
    const value = Number(row.percentage)
    if (
      row.percentage === '' ||
      !(value > 0) ||
      value > 100 ||
      !hasAtMostTwoDecimals(row.percentage)
    ) {
      return t('goals:allocation.invalidPercentage')
    }
    if (!capacityKnown) return null
    const available = availableOf(row.productId)
    return value > available
      ? t('goals:allocation.overCapacity', { percent: percent(available) })
      : null
  }

  const complete = rows.every((r) => r.productId !== '' && r.percentage !== '')
  const valid = capacityKnown && complete && rows.every((r) => problemOf(r) === null)
  const changed =
    JSON.stringify(rows.map((r) => [r.productId, Number(r.percentage)])) !==
    JSON.stringify(goal.allocations.map((a) => [a.productId, a.percentage]))

  function update(key: number, patch: Partial<Row>) {
    setRows((current) => current.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  }

  async function save() {
    await saveAction.run(() =>
      replaceMutation.mutateAsync({
        id: goal.id,
        allocations: rows.map((r) => ({
          productId: r.productId,
          percentage: Number(r.percentage),
        })),
      }),
    )
  }

  return (
    <Box>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {t('goals:allocation.description')}
      </Typography>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {rows.length === 0 && (
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          {t('goals:allocation.empty')}
        </Typography>
      )}
      <Box sx={{ display: 'grid', gap: 2, mb: 2 }}>
        {rows.map((row) => {
          const problem = problemOf(row)
          const name = row.productId ? nameOf(row.productId) : t('goals:allocation.newRow')
          const available = row.productId && capacityKnown ? availableOf(row.productId) : null
          const value = Number(row.percentage)
          return (
            <Box
              key={row.key}
              role="group"
              aria-label={name}
              sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap' }}
            >
              <TextField
                select
                size="small"
                label={t('goals:allocation.product')}
                value={row.productId}
                onChange={(e) => update(row.key, { productId: e.target.value })}
                sx={{ minWidth: 0, flex: '2 1 200px' }}
              >
                {optionsFor(row).map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.name}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                size="small"
                type="number"
                label={t('goals:allocation.percentage')}
                value={row.percentage}
                onChange={(e) => update(row.key, { percentage: e.target.value })}
                disabled={row.productId === ''}
                slotProps={{ htmlInput: { min: 0, max: 100, step: '0.01' } }}
                error={problem !== null}
                helperText={problem ?? undefined}
                sx={{ flex: '1 1 120px' }}
              />
              <IconButton
                aria-label={t('goals:allocation.removeAria', { name })}
                onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}
              >
                <DeleteIcon />
              </IconButton>
              {available !== null && (
                <Box sx={{ flexBasis: '100%' }}>
                  <Typography variant="caption" color="text.secondary" component="p">
                    {t('goals:allocation.available', { percent: percent(available) })}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" component="p">
                    {t('goals:allocation.unassigned', {
                      percent: percent(
                        Math.max(0, available - (Number.isFinite(value) ? value : 0)),
                      ),
                    })}
                  </Typography>
                </Box>
              )}
            </Box>
          )
        })}
      </Box>

      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Button
          onClick={() => {
            setRows((current) => [...current, { key: nextKey, productId: '', percentage: '' }])
            setNextKey((k) => k + 1)
          }}
        >
          {t('goals:allocation.addProduct')}
        </Button>
        <Button
          variant="contained"
          disabled={!changed || !valid || saveAction.pending}
          onClick={() => void save()}
        >
          {t('goals:allocation.save')}
        </Button>
      </Box>
    </Box>
  )
}
