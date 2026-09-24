import { useState } from 'react'
import { Box, Paper, Typography } from '@mui/material'
import type { Account } from '../../api/accounts'
import { ErrorAlert } from '../../components/ErrorAlert'
import { AccountCreateForm } from '../accounts/AccountCreateForm'

interface OnboardingPageProps {
  /** Called once the first account exists; the app then swaps onboarding for the normal shell. */
  onCompleted: (account: Account) => void
}

/**
 * First-run screen (F011, PRD S6.7), shown instead of the app shell while zero accounts exist:
 * the account-creation form with framing copy. Nothing else is set up here - categories and
 * payment methods are pre-seeded (F002) and the institution defaults to "No institution" (F017).
 */
export function OnboardingPage({ onCompleted }: OnboardingPageProps) {
  const [error, setError] = useState<string | null>(null)

  return (
    <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Welcome to My Finances
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Start by adding your first account, for example your main checking account. Its opening
        balance and date are the starting point for tracking: everything you record afterwards is
        added on top of it. You can add more accounts later, and pick a real institution now or keep
        "No institution".
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>
          Create your first account
        </Typography>
        <AccountCreateForm
          onCreated={onCompleted}
          onError={setError}
          submitLabel="Create account"
        />
      </Paper>
    </Box>
  )
}
