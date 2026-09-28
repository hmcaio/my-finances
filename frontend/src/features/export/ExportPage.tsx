import { useState } from 'react'
import { Alert, Box, Button, MenuItem, Select, TextField, Typography } from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { REVERSED_RANGE_MESSAGE, type ExportFilter } from '../../api/export/export'
import { useDownloadExport } from '../../api/export/exportQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { useQueryState } from '../../hooks/queryState'

/**
 * Data export (F013, PRD S6.9): optional date range, account and category filters, then one ZIP
 * of twelve CSVs. Each filter only narrows the files that have that dimension; the reference
 * files are always exported in full, which the page says so the user isn't surprised.
 *
 * Responsive (F021): the four filters are the same shape as Transactions'/Transfers' filter row,
 * so they share `ResponsiveFilterBar` (inline from `sm`, a "Filters" sheet with an active-count
 * badge below it) rather than the old standalone "Filters (optional)" panel.
 */
export function ExportPage() {
  const [filter, setFilter] = useState<ExportFilter>({})
  const [downloading, setDownloading] = useState(false)
  const [downloaded, setDownloaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Closed and investment accounts are included: their history is part of the export.
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  useQueryState(accountsQuery, setError)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  useQueryState(categoriesQuery, setError)
  const downloadMutation = useDownloadExport()

  function update(patch: Partial<ExportFilter>) {
    setFilter((prev) => ({ ...prev, ...patch }))
    setDownloaded(false)
  }

  function clearFilters() {
    setFilter({})
    setDownloaded(false)
  }

  const activeFilterCount = Object.values(filter).filter(Boolean).length

  async function handleDownload() {
    setError(null)
    setDownloaded(false)
    if (filter.dateFrom && filter.dateTo && filter.dateFrom > filter.dateTo) {
      setError(REVERSED_RANGE_MESSAGE)
      return
    }
    setDownloading(true)
    try {
      await downloadMutation.mutateAsync(filter)
      setDownloaded(true)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Export
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Download all your data as a ZIP of CSV files, one per entity, with names next to every id so
        each file opens directly in a spreadsheet. Every filter below is optional.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ResponsiveFilterBar activeCount={activeFilterCount} onClear={clearFilters}>
        <TextField
          label="From"
          type="date"
          size="small"
          value={filter.dateFrom ?? ''}
          onChange={(e) => update({ dateFrom: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="To"
          type="date"
          size="small"
          value={filter.dateTo ?? ''}
          onChange={(e) => update({ dateTo: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <Select
          size="small"
          displayEmpty
          value={filter.accountId ?? ''}
          onChange={(e) => update({ accountId: e.target.value || undefined })}
          aria-label="Account filter"
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All accounts</MenuItem>
          {accounts?.map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          displayEmpty
          value={filter.categoryId ?? ''}
          onChange={(e) => update({ categoryId: e.target.value || undefined })}
          aria-label="Category filter"
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All categories</MenuItem>
          {categories?.map((c) => (
            <MenuItem key={c.id} value={c.id}>
              {c.name}
            </MenuItem>
          ))}
        </Select>
      </ResponsiveFilterBar>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        The date range applies to transactions, transfers, investment snapshots and the budget and
        recurring template versions. The account filter applies to transactions, transfers (either
        side) and recurring templates; the category filter to transactions, budgets and recurring
        templates. Everything else (categories, payment methods, institutions, accounts and
        investment products) is always exported in full.
      </Typography>

      <Button
        variant="contained"
        startIcon={<DownloadIcon />}
        onClick={handleDownload}
        disabled={downloading}
      >
        {downloading ? 'Preparing…' : 'Download'}
      </Button>
      {downloaded && (
        <Alert severity="success" sx={{ mt: 2 }}>
          Export downloaded.
        </Alert>
      )}
    </Box>
  )
}
