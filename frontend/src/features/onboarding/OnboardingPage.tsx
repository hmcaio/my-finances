import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Paper, Step, StepLabel, Stepper, Typography } from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { AccountCreateForm } from '../accounts/AccountCreateForm'
import { CategorySuggestionsStep } from './CategorySuggestionsStep'

interface OnboardingPageProps {
  /**
   * Called once both steps are done (an account exists and the category step finished, whatever
   * it finished with). Optional: the app swaps onboarding for the shell by itself, because its
   * gate derives from the (then invalidated) accounts list.
   */
  onCompleted?: (account: Account) => void
}

/**
 * First-run screen (F011, PRD S6.7; two-step `Stepper` since F035/ADR 0028), shown instead of the
 * app shell while zero accounts exist: step 1 creates the first account (unchanged from F011),
 * step 2 offers a localized list of suggested starter categories the user can accept, reject or
 * supplement with their own (`CategorySuggestionsStep`). Payment methods are pre-seeded (F002) and
 * the institution defaults to "No institution" (F017); categories are no longer migration-seeded
 * beyond the four protected rows (F035/ADR 0028) - this step is how the regular ones get created.
 *
 * The trigger for showing this page at all stays exactly as it was under F011 (zero accounts
 * exist) - step 2 is only ever reached as part of that same first-run flow and never resurfaces
 * later, even if every category is later deleted. No new persisted flag is introduced.
 *
 * Responsive (F021): this page renders outside `Layout` (there is no nav yet), so it sets its own
 * padding; `AccountCreateForm`'s default (non-`dialog`) layout is a one-column-on-phone `FormGrid`
 * with the button below it, so the form genuinely stacks on a phone instead of just wrapping.
 */
export function OnboardingPage({ onCompleted }: OnboardingPageProps) {
  const { t } = useTranslation('onboarding')
  const [activeStep, setActiveStep] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [account, setAccount] = useState<Account | null>(null)

  function handleAccountCreated(created: Account) {
    setAccount(created)
    setError(null)
    setActiveStep(1)
  }

  function handleCategoriesCompleted() {
    if (account) onCompleted?.(account)
  }

  return (
    <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: { xs: 4, sm: 6 } }}>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('title')}
      </Typography>

      <Stepper activeStep={activeStep} sx={{ mb: 3 }}>
        <Step>
          <StepLabel>{t('steps.account')}</StepLabel>
        </Step>
        <Step>
          <StepLabel>{t('steps.categories')}</StepLabel>
        </Step>
      </Stepper>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {activeStep === 0 ? (
        <>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            {t('intro')}
          </Typography>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              {t('formHeading')}
            </Typography>
            <AccountCreateForm
              onCreated={handleAccountCreated}
              onError={setError}
              submitLabel={t('submitLabel')}
            />
          </Paper>
        </>
      ) : (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <CategorySuggestionsStep onError={setError} onCompleted={handleCategoriesCompleted} />
        </Paper>
      )}
    </Box>
  )
}
