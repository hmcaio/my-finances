import { useRef, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import { Box, Button, TextField, Typography } from '@mui/material'
import { ErrorAlert } from '../../../components/feedback/ErrorAlert'
import { PageContainer } from '../../../components/layout/PageContainer'
import { PageHeader } from '../../../components/layout/PageHeader'
import { PageSection } from '../../../components/layout/PageSection'
import { SnapshotSheetTable } from './components/SnapshotSheetTable'
import { useSnapshotSheet } from './hooks/useSnapshotSheet'

/**
 * The snapshot sheet (F040, PRD S6.6) at `/investments/snapshots`: one row per open holding with a
 * blank balance input, one date for every row. Blank rows are skipped; the filled ones are saved
 * together through one atomic request (all or none), so a failure is always one more click and never
 * retyping. The rows, validation and error mapping are in `lib/snapshotSheet.ts`; the state in
 * `hooks/useSnapshotSheet.ts`.
 */
export function InvestmentSnapshotSheetPage() {
  const { t } = useTranslation('investmentSnapshots')
  const { t: tCommon } = useTranslation()
  const sheet = useSnapshotSheet()
  const saveButtonRef = useRef<HTMLButtonElement>(null)
  const empty = sheet.rows?.length === 0

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (sheet.canSave) void sheet.save()
  }

  return (
    <PageContainer>
      <PageHeader title={t('sheet.title')}>
        <Button component={RouterLink} to="/investments" variant="text">
          {t('sheet.backToInvestments')}
        </Button>
      </PageHeader>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('sheet.description')}
      </Typography>

      <ErrorAlert message={sheet.error} onDismiss={sheet.dismissError} />

      {empty ? (
        <PageSection>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            {t('sheet.empty')}
          </Typography>
          <Button component={RouterLink} to="/investments?tab=products" variant="outlined">
            {t('sheet.emptyLink')}
          </Button>
        </PageSection>
      ) : (
        <Box component="form" noValidate onSubmit={handleSubmit}>
          <TextField
            label={t('sheet.dateLabel')}
            type="date"
            required
            value={sheet.date}
            onChange={(event) => sheet.setDate(event.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ mb: 1 }}
          />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
            {tCommon('form.requiredHint')}
          </Typography>

          <SnapshotSheetTable
            rows={sheet.rows}
            state={sheet.loadState}
            inputs={sheet.inputs}
            onInputChange={sheet.setInput}
            rowError={sheet.rowError}
            saveButtonRef={saveButtonRef}
          />

          <Button
            ref={saveButtonRef}
            type="submit"
            variant="contained"
            disabled={!sheet.canSave}
            sx={{ mt: 3 }}
          >
            {t('sheet.save')}
          </Button>
        </Box>
      )}
    </PageContainer>
  )
}
