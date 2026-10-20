import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  FormGroup,
  MenuItem,
  Select,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import { useVehicles } from '../../api/vehicles/vehiclesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import {
  REPORT_TYPES,
  reversedRangeMessage,
  type ReportFilter,
  type ReportFilterDimension,
  type ReportType,
} from '../../api/reports/reports'
import { useDownloadReports } from '../../api/reports/reportsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { MonthPicker } from '../../components/inputs/MonthPicker'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { useQueryState } from '../../hooks/queryState'

/**
 * Reports (F030 spec, PRD S6.9), replacing F013's all-entity export: a checkbox list of the
 * eleven curated report types plus one shared filter bar showing only the dimensions at least one
 * checked type actually uses. One type downloads its bare file (CSV or PDF); two or more, or a
 * single type whose own generator renders more than one file (FII portfolio, Budgets &amp;
 * spending), download a ZIP - the backend decides which, this page just triggers the request.
 */
export function ReportsPage() {
  const { t } = useTranslation('reports')
  const [selected, setSelected] = useState<ReportType[]>([])
  const [filter, setFilter] = useState<ReportFilter>({})
  const [error, setError] = useState<string | null>(null)
  const [downloaded, setDownloaded] = useState(false)

  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  useQueryState(accountsQuery, setError)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  useQueryState(categoriesQuery, setError)
  const vehiclesQuery = useVehicles()
  const vehicles = vehiclesQuery.data
  useQueryState(vehiclesQuery, setError)
  const downloadMutation = useDownloadReports()

  const activeDimensions = useMemo(() => {
    const dims = new Set<ReportFilterDimension>()
    for (const info of REPORT_TYPES) {
      if (selected.includes(info.type)) {
        info.dimensions.forEach((d) => dims.add(d))
      }
    }
    return dims
  }, [selected])

  function toggleType(type: ReportType, checked: boolean) {
    setDownloaded(false)
    setSelected((prev) => (checked ? [...prev, type] : prev.filter((t) => t !== type)))
  }

  function update(patch: Partial<ReportFilter>) {
    setFilter((prev) => ({ ...prev, ...patch }))
    setDownloaded(false)
  }

  function clearFilters() {
    setFilter({})
    setDownloaded(false)
  }

  const activeFilterCount = Object.values(filter).filter(Boolean).length

  async function handleGenerate() {
    setError(null)
    setDownloaded(false)
    if (filter.dateFrom && filter.dateTo && filter.dateFrom > filter.dateTo) {
      setError(reversedRangeMessage())
      return
    }
    try {
      await downloadMutation.mutateAsync({ types: selected, filter })
      setDownloaded(true)
    } catch (err) {
      setError(defaultErrorMessage(err))
    }
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('subtitle')}
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <FormGroup sx={{ mb: 3 }}>
        {REPORT_TYPES.map((info) => (
          <FormControlLabel
            key={info.type}
            control={
              <Checkbox
                checked={selected.includes(info.type)}
                onChange={(e) => toggleType(info.type, e.target.checked)}
              />
            }
            label={t(info.labelKey as never)}
          />
        ))}
      </FormGroup>

      {activeDimensions.size > 0 && (
        <ResponsiveFilterBar activeCount={activeFilterCount} onClear={clearFilters}>
          {activeDimensions.has('dateRange') && (
            <>
              <TextField
                label={t('filters.from')}
                type="date"
                size="small"
                value={filter.dateFrom ?? ''}
                onChange={(e) => update({ dateFrom: e.target.value || undefined })}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label={t('filters.to')}
                type="date"
                size="small"
                value={filter.dateTo ?? ''}
                onChange={(e) => update({ dateTo: e.target.value || undefined })}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </>
          )}
          {activeDimensions.has('asOfDate') && (
            <TextField
              label={t('filters.asOf')}
              type="date"
              size="small"
              value={filter.asOfDate ?? ''}
              onChange={(e) => update({ asOfDate: e.target.value || undefined })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          )}
          {activeDimensions.has('month') && (
            <MonthPicker
              label={t('filters.month')}
              value={filter.month ?? ''}
              onChange={(value) => update({ month: value || undefined })}
            />
          )}
          {activeDimensions.has('account') && (
            <Select
              size="small"
              displayEmpty
              value={filter.accountId ?? ''}
              onChange={(e) => update({ accountId: e.target.value || undefined })}
              aria-label={t('filters.accountAria')}
              sx={{ minWidth: 180 }}
            >
              <MenuItem value="">{t('filters.allAccounts')}</MenuItem>
              {accounts?.map((a) => (
                <MenuItem key={a.id} value={a.id}>
                  {a.name}
                </MenuItem>
              ))}
            </Select>
          )}
          {activeDimensions.has('category') && (
            <Select
              size="small"
              displayEmpty
              value={filter.categoryId ?? ''}
              onChange={(e) => update({ categoryId: e.target.value || undefined })}
              aria-label={t('filters.categoryAria')}
              sx={{ minWidth: 180 }}
            >
              <MenuItem value="">{t('filters.allCategories')}</MenuItem>
              {categories?.map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.name}
                </MenuItem>
              ))}
            </Select>
          )}
          {activeDimensions.has('vehicle') && (
            <Select
              size="small"
              displayEmpty
              value={filter.vehicleId ?? ''}
              onChange={(e) => update({ vehicleId: e.target.value || undefined })}
              aria-label={t('filters.vehicleAria')}
              sx={{ minWidth: 180 }}
            >
              <MenuItem value="">{t('filters.allVehicles')}</MenuItem>
              {vehicles?.map((v) => (
                <MenuItem key={v.id} value={v.id}>
                  {v.name}
                </MenuItem>
              ))}
            </Select>
          )}
          {activeDimensions.has('netWorthGranularity') && (
            <ToggleButtonGroup
              size="small"
              exclusive
              value={filter.netWorthGranularity ?? 'CHANGE_DATE'}
              aria-label={t('filters.netWorthGranularityAria')}
              onChange={(_e, next) => {
                if (next) update({ netWorthGranularity: next })
              }}
            >
              <ToggleButton value="MONTH">{t('filters.monthlyOption')}</ToggleButton>
              <ToggleButton value="CHANGE_DATE">{t('filters.everyChangeOption')}</ToggleButton>
            </ToggleButtonGroup>
          )}
        </ResponsiveFilterBar>
      )}

      <Button
        variant="contained"
        startIcon={<DownloadIcon />}
        onClick={handleGenerate}
        disabled={selected.length === 0 || downloadMutation.isPending}
      >
        {downloadMutation.isPending ? t('generatingButton') : t('generateButton')}
      </Button>
      {downloaded && (
        <Alert severity="success" sx={{ mt: 2 }}>
          {t('downloaded')}
        </Alert>
      )}
    </Box>
  )
}
