import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, Button, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { CategorySuggestionsStep } from '../onboarding/CategorySuggestionsStep'

/**
 * Empty-state prompt on `CategoriesPage` (issue #168): offers onboarding's suggested-categories
 * step (F035) to a user who missed it - reloading or signing in again after creating the first
 * account lands on the dashboard, because onboarding is derived from "zero accounts" alone (ADR
 * 0028, no persisted flag). The page shows this only while no regular category exists, so it
 * derives from the data like the onboarding gate does and can't drift from it. The step is the
 * onboarding one, unchanged: it creates through the ordinary `POST /api/categories`.
 */
export function StarterCategoriesPrompt() {
  const { t } = useTranslation(['categories', 'common'])
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function close() {
    setOpen(false)
    setError(null)
  }

  return (
    <>
      <Alert
        severity="info"
        sx={{ mb: 3 }}
        action={
          <Button color="inherit" size="small" onClick={() => setOpen(true)}>
            {t('categories:starterButton')}
          </Button>
        }
      >
        {t('categories:starterPrompt')}
      </Alert>

      <ResponsiveDialog open={open} onClose={close} fullWidth maxWidth="sm">
        <DialogTitle>{t('categories:starterDialogTitle')}</DialogTitle>
        <DialogContent>
          <ErrorAlert message={error} onDismiss={() => setError(null)} />
          <CategorySuggestionsStep onError={setError} onCompleted={close} />
        </DialogContent>
        <DialogActions>
          <Button onClick={close}>{t('common:actions.cancel')}</Button>
        </DialogActions>
      </ResponsiveDialog>
    </>
  )
}
