import { useMemo, useState } from 'react'
import { Box, Button, Paper, TextField, Typography } from '@mui/material'
import { defaultErrorMessage } from '../../api/core/apiError'
import { INVALID_ENTRIES_MESSAGE, NOT_FII_MESSAGE } from '../../api/investments/allocationPlan'
import {
  useCurrentAllocationPlan,
  useSetAllocationPlan,
} from '../../api/investments/allocationPlanQueries'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { currentMonth } from '../../utils/localDate'

interface AllocationPlanEditorProps {
  /** Every product classified under the "REITs (FIIs)" sub-category. */
  fiiProducts: InvestmentProduct[]
}

/**
 * The target allocation-plan editor (F026 spec, ADR 0023): a percentage input per FII product,
 * a live running total, and a month to apply it from - editing the current month twice replaces
 * the same version (same-month correction), editing a future month creates a new one. Submit is
 * disabled unless the entries sum to exactly 100, so the planned pie chart always fills the
 * circle once saved.
 */
export function AllocationPlanEditor({ fiiProducts }: AllocationPlanEditorProps) {
  const [error, setError] = useState<string | null>(null)
  const currentPlanQuery = useCurrentAllocationPlan()
  const setAllocation = useSetAllocationPlan()

  const [effectiveFrom, setEffectiveFrom] = useState(currentMonth())
  // Only the entries the user has actually touched - the current effective version's own values
  // (below) are the displayed default for everything else, so there is nothing to sync via an
  // effect (React's "you might not need an effect": derive during render instead).
  const [edited, setEdited] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const defaults = useMemo(() => {
    const map: Record<string, string> = {}
    for (const entry of currentPlanQuery.data?.entries ?? []) {
      map[entry.investmentProductId] = String(entry.targetPercentage)
    }
    return map
  }, [currentPlanQuery.data])

  function valueOf(productId: string): string {
    return edited[productId] ?? defaults[productId] ?? ''
  }

  const total = fiiProducts.reduce((sum, p) => sum + (Number(valueOf(p.id)) || 0), 0)
  const roundedTotal = Math.round(total * 100) / 100

  function setPercentage(productId: string, value: string) {
    setSaved(false)
    setEdited((prev) => ({ ...prev, [productId]: value }))
  }

  async function handleSave() {
    setError(null)
    setSaved(false)
    setSaving(true)
    try {
      const entries = fiiProducts
        .map((p) => ({ investmentProductId: p.id, targetPercentage: Number(valueOf(p.id)) || 0 }))
        .filter((e) => e.targetPercentage > 0)
      await setAllocation.mutateAsync({ entries, effectiveFrom })
      setSaved(true)
    } catch (err) {
      setError(defaultErrorMessage(err, { 400: INVALID_ENTRIES_MESSAGE, 409: NOT_FII_MESSAGE }))
    } finally {
      setSaving(false)
    }
  }

  const sumIsValid = roundedTotal === 100

  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        Target allocation
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        A percentage per FII, effective from the month below. Entries must sum to exactly 100%.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
        <TextField
          label="Effective from"
          type="month"
          size="small"
          value={effectiveFrom}
          onChange={(e) => {
            setEffectiveFrom(e.target.value)
            setSaved(false)
          }}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Box>

      {fiiProducts.length === 0 ? (
        <Typography color="text.secondary">
          No FII products yet - classify a product under the "REITs (FIIs)" sub-category first.
        </Typography>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mb: 2 }}>
          {fiiProducts.map((product) => (
            <Box key={product.id} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Typography sx={{ minWidth: 140 }}>{product.ticker ?? product.name}</Typography>
              <TextField
                size="small"
                type="number"
                label="Target %"
                value={valueOf(product.id)}
                onChange={(e) => setPercentage(product.id, e.target.value)}
                slotProps={{ htmlInput: { min: 0, max: 100, step: '0.01' } }}
                sx={{ width: 140 }}
              />
            </Box>
          ))}
        </Box>
      )}

      <Typography sx={{ mb: 2 }} color={sumIsValid ? 'success.main' : 'error.main'}>
        Total: {roundedTotal.toFixed(2)}% {sumIsValid ? '' : '(must be exactly 100%)'}
      </Typography>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Button
          variant="contained"
          disabled={saving || !sumIsValid || fiiProducts.length === 0}
          onClick={() => void handleSave()}
        >
          Save allocation
        </Button>
        {saved && <Typography color="success.main">Saved.</Typography>}
      </Box>
    </Paper>
  )
}
