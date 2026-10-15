import { Fragment, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Chip,
  IconButton,
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { translateSeedName } from '../../i18n/seedNames'

/**
 * Read-only expandable list of the two-level investment taxonomy (F008 spec, F034/ADR 0028): every
 * row at both levels is fixed, migration-seeded content with no create/rename/delete affordance
 * anywhere - the backend's write endpoints (including the standalone sub-category ones) were
 * removed outright. Expand/collapse per category stays, since the nested sub-categories are still
 * useful to browse.
 *
 * Responsive (F021): the categories table has only two data columns (Name, Sub-categories count),
 * so per the spec's 1-2-column rule it stays a plain table at every size.
 */
export function InvestmentCategoriesPage() {
  const { t } = useTranslation(['investmentCategories', 'common'])
  const [error, setError] = useState<string | null>(null)
  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)

  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set())

  function toggle(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }

  // Clears the stale banner before retrying, so it doesn't outlive a successful retry.
  function retry() {
    setError(null)
    categoriesState.reload()
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
        {t('investmentCategories:title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('investmentCategories:description')}
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('investmentCategories:nameColumn')}</TableCell>
                <TableCell>{t('investmentCategories:subcategoriesColumn')}</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={categoriesState} onRetry={retry} columns={2}>
              {categories?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} align="center">
                    <Typography color="text.secondary">
                      {t('investmentCategories:emptyList')}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {categories?.map((category) => {
                const isOpen = expanded.has(category.id)
                return (
                  <Fragment key={category.id}>
                    <TableRow>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <IconButton
                            size="small"
                            aria-label={t(
                              isOpen
                                ? 'investmentCategories:collapse'
                                : 'investmentCategories:expand',
                              { name: category.name },
                            )}
                            aria-expanded={isOpen}
                            onClick={() => toggle(category.id)}
                          >
                            {isOpen ? (
                              <ExpandLessIcon fontSize="small" />
                            ) : (
                              <ExpandMoreIcon fontSize="small" />
                            )}
                          </IconButton>
                          {translateSeedName('investmentCategories', category.name, t)}
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={t('investmentCategories:subcategoryCount', {
                            count: category.subcategories.length,
                          })}
                        />
                      </TableCell>
                    </TableRow>
                    {isOpen &&
                      category.subcategories.map((subcategory) => (
                        <TableRow key={subcategory.id} sx={{ bgcolor: 'action.hover' }}>
                          <TableCell sx={{ pl: 7 }} colSpan={2}>
                            {translateSeedName('investmentSubcategories', subcategory.name, t)}
                          </TableCell>
                        </TableRow>
                      ))}
                  </Fragment>
                )
              })}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  )
}
