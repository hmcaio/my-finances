import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Paper, Typography } from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { AccountCreateForm } from '../accounts/AccountCreateForm'

interface OnboardingPageProps {
  /**
   * Called once the first account exists. Optional: the app swaps onboarding for the shell by
   * itself, because its gate derives from the (then invalidated) accounts list.
   */
  onCompleted?: (account: Account) => void
}

/**
 * First-run screen (F011, PRD S6.7), shown instead of the app shell while zero accounts exist:
 * the account-creation form with framing copy. Nothing else is set up here - categories and
 * payment methods are pre-seeded (F002) and the institution defaults to "No institution" (F017).
 *
 * Responsive (F021): this page renders outside `Layout` (there is no nav yet), so it sets its own
 * padding; `AccountCreateForm`'s default (non-`dialog`) layout is a one-column-on-phone `FormGrid`
 * with the button below it, so the form genuinely stacks on a phone instead of just wrapping.
 */
export function OnboardingPage({ onCompleted }: OnboardingPageProps) {
  const { t } = useTranslation('onboarding')
  const [error, setError] = useState<string | null>(null)

  return (
    <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: { xs: 4, sm: 6 } }}>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('intro')}
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>
          {t('formHeading')}
        </Typography>
        <AccountCreateForm
          onCreated={(account) => onCompleted?.(account)}
          onError={setError}
          submitLabel={t('submitLabel')}
        />
      </Paper>
    </Box>
  )
}
