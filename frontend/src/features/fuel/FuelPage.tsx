import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Box, Button, MenuItem, Paper, Select, Typography, useTheme } from '@mui/material'
import type { Theme } from '@mui/material/styles'
import { useCategories } from '../../api/categories/categoriesQueries'
import { useVehicleFuelHistory, useVehicles } from '../../api/vehicles/vehiclesQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney, formatNumber } from '../../i18n/format'
import { fuelTypeLabel } from '../../utils/fuelType'
import type { Transaction } from '../../api/transactions/transactions'
import { FuelTimeSeriesChart, type FuelSeries } from './FuelTimeSeriesChart'

/** One color per fuel type (F024 spec: "price/liter chart shows separate lines per fuel type
 * used"), drawn from the theme's existing palette slots rather than inventing new brand colors. */
function fuelTypeColors(theme: Theme): Record<string, string> {
  return {
    ETANOL: theme.palette.primary.main,
    ETANOL_ADITIVADO: theme.palette.secondary.main,
    GASOLINA: theme.palette.warning.main,
    GASOLINA_ADITIVADA: theme.palette.success.main,
  }
}

/**
 * Fuel page (F024 spec, PRD S6.11): a vehicle selector, that vehicle's fuel-purchase list, and
 * three hand-rolled per-fill time-series charts (price/liter by fuel type, km/L, spend/km) - raw
 * points, no date aggregation, always scoped to one vehicle (ADR 0021: km-based ratios are
 * meaningless mixed across vehicles). "Add fuel transaction" hands off to the Transactions page
 * with the fuel category pre-selected via navigation state.
 */
export function FuelPage() {
  const { t } = useTranslation('fuel')
  const { locale } = useLocale()
  const theme = useTheme()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [vehicleId, setVehicleId] = useState<string>('')

  const vehiclesQuery = useVehicles()
  const vehicles = vehiclesQuery.data
  const vehiclesState = useQueryState(vehiclesQuery, setError)
  const categoriesQuery = useCategories()
  const fuelCategory = categoriesQuery.data?.find((c) => c.fuelCategory)

  const selectedVehicleId = vehicleId || vehicles?.[0]?.id || null
  const historyQuery = useVehicleFuelHistory(selectedVehicleId)
  const history = historyQuery.data
  const historyState = useQueryState(historyQuery, setError)

  const tableState = combineLoadState(vehiclesState, historyState)

  const historyNewestFirst = useMemo(
    () =>
      history
        ? [...history].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
        : history,
    [history],
  )

  function addFuelTransaction() {
    navigate('/transactions', { state: { presetCategoryId: fuelCategory?.id } })
  }

  const colors = fuelTypeColors(theme)

  const priceByFuelType: FuelSeries[] = useMemo(() => {
    const byType = new Map<string, FuelSeries>()
    for (const t of history ?? []) {
      if (!t.fuelType || t.pricePerLiter === null) continue
      if (!byType.has(t.fuelType)) {
        byType.set(t.fuelType, {
          label: fuelTypeLabel(t.fuelType),
          color: colors[t.fuelType] ?? theme.palette.primary.main,
          points: [],
        })
      }
      byType.get(t.fuelType)!.points.push({ date: t.date, value: t.pricePerLiter })
    }
    return [...byType.values()]
  }, [history, colors, theme])

  const kmPerLiterSeries: FuelSeries[] = useMemo(
    () => [
      {
        label: t('kmPerLiterSeriesLabel'),
        color: theme.palette.primary.main,
        points: (history ?? [])
          .filter((t) => t.kmPerLiter !== null)
          .map((t) => ({ date: t.date, value: t.kmPerLiter! })),
      },
    ],
    [history, theme, t],
  )

  const spendPerKmSeries: FuelSeries[] = useMemo(
    () => [
      {
        label: t('spendPerKmSeriesLabel'),
        color: theme.palette.primary.main,
        points: (history ?? [])
          .filter((t) => t.amountPerKm !== null)
          .map((t) => ({ date: t.date, value: t.amountPerKm! })),
      },
    ],
    [history, theme, t],
  )

  const columns: ResponsiveColumn<Transaction>[] = [
    { key: 'date', header: t('columns.date'), render: (tx) => tx.date, role: 'secondary' },
    {
      key: 'fuelType',
      header: t('columns.fuelType'),
      render: (tx) => fuelTypeLabel(tx.fuelType),
      role: 'primary',
    },
    {
      key: 'liters',
      header: t('columns.liters'),
      render: (tx) => (tx.liters !== null ? formatNumber(tx.liters, locale, 3) : ''),
    },
    {
      key: 'pricePerLiter',
      header: t('columns.pricePerLiter'),
      render: (tx) => (tx.pricePerLiter !== null ? formatNumber(tx.pricePerLiter, locale, 3) : ''),
    },
    {
      key: 'amount',
      header: t('columns.amount'),
      align: 'right',
      render: (tx) => formatMoney(tx.amount, locale),
    },
    {
      key: 'kmPerLiter',
      header: t('columns.kmPerLiter'),
      render: (tx) => (tx.kmPerLiter !== null ? formatNumber(tx.kmPerLiter, locale, 2) : '—'),
      tabletPriority: 'low',
    },
    {
      key: 'amountPerKm',
      header: t('columns.amountPerKm'),
      render: (tx) => (tx.amountPerKm !== null ? formatMoney(tx.amountPerKm, locale) : '—'),
      tabletPriority: 'low',
    },
    {
      key: 'kmSinceLastFill',
      header: t('columns.kmSinceLastFill'),
      render: (tx) =>
        tx.kmSinceLastFill !== null ? formatNumber(tx.kmSinceLastFill, locale, 1) : '—',
      tabletPriority: 'low',
    },
    {
      key: 'odometer',
      header: t('columns.odometer'),
      render: (tx) => (tx.odometer !== null ? formatNumber(tx.odometer, locale, 1) : '—'),
      tabletPriority: 'low',
    },
  ]

  function retry() {
    setError(null)
    tableState.reload()
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 1,
          flexWrap: 'wrap',
        }}
      >
        <Typography variant="h4" component="h1">
          {t('title')}
        </Typography>
        <Button variant="contained" onClick={addFuelTransaction} disabled={!fuelCategory}>
          {t('addFuelTransactionButton')}
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('description')}
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {vehiclesState.loadError ? (
        <LoadFailedNotice message={vehiclesState.loadError} onRetry={retry} />
      ) : vehicles?.length === 0 ? (
        <Typography color="text.secondary">{t('emptyVehicles')}</Typography>
      ) : (
        <>
          <Select
            size="small"
            value={selectedVehicleId ?? ''}
            onChange={(e) => setVehicleId(e.target.value)}
            aria-label={t('vehicleAria')}
            sx={{ minWidth: 200, mb: 3 }}
          >
            {vehicles?.map((v) => (
              <MenuItem key={v.id} value={v.id}>
                {v.name}
              </MenuItem>
            ))}
          </Select>

          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            <Typography variant="h6" sx={{ mb: 1 }}>
              {t('pricePerLiterTitle')}
            </Typography>
            <FuelTimeSeriesChart
              series={priceByFuelType}
              label={t('pricePerLiterChartLabel')}
              formatValue={(v) => formatMoney(v, locale)}
              axisColor={theme.palette.text.secondary}
              gridColor={theme.palette.divider}
              zeroBaseline={false}
            />
          </Paper>

          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            <Typography variant="h6" sx={{ mb: 1 }}>
              {t('kmPerLiterTitle')}
            </Typography>
            <FuelTimeSeriesChart
              series={kmPerLiterSeries}
              label={t('kmPerLiterChartLabel')}
              formatValue={(v) => formatNumber(v, locale, 1)}
              axisColor={theme.palette.text.secondary}
              gridColor={theme.palette.divider}
              zeroBaseline={false}
            />
          </Paper>

          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            <Typography variant="h6" sx={{ mb: 1 }}>
              {t('spendPerKmTitle')}
            </Typography>
            <FuelTimeSeriesChart
              series={spendPerKmSeries}
              label={t('spendPerKmChartLabel')}
              formatValue={(v) => formatMoney(v, locale)}
              axisColor={theme.palette.text.secondary}
              gridColor={theme.palette.divider}
              zeroBaseline={false}
            />
          </Paper>

          <ResponsiveTable
            aria-label={t('historyAria')}
            columns={columns}
            rows={historyNewestFirst}
            getRowKey={(tx) => tx.id}
            state={tableState}
            onRetry={retry}
            emptyMessage={t('emptyHistory')}
          />
        </>
      )}
    </Box>
  )
}
