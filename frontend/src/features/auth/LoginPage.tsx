import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Box, Button, Paper, TextField, Typography } from '@mui/material'
import { useLogin, useSession } from '../../api/auth/authQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import type { LoginRedirectState } from './RequireAuth'

// Mirror the backend's LoginRequest bounds (username 100, password 256).
const MAX_USERNAME_LENGTH = 100
const MAX_PASSWORD_LENGTH = 256

/** Only ever an in-app path: a `from` that is not one falls back to the dashboard. */
function safeDestination(from: string | undefined): string {
  return from && from.startsWith('/') && !from.startsWith('//') && !from.startsWith('/login')
    ? from
    : '/'
}

/**
 * The `/login` route (F036, ADR 0029): the one page reachable without a session. Renders outside
 * `Layout` (no nav to show yet). A visit while already signed in goes straight to the app.
 *
 * Wrong credentials come back as a 401 whose body deliberately doesn't say which field was wrong,
 * so the message is one fixed sentence; a 429 (failed-attempt cooldown) uses the central
 * `PROBLEM_TYPE_MESSAGES` wording.
 */
export function LoginPage() {
  const { t } = useTranslation('auth')
  const location = useLocation()
  const navigate = useNavigate()
  const session = useSession()
  const login = useLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const destination = safeDestination((location.state as LoginRedirectState | null)?.from)

  if (session.data?.authenticated) return <Navigate to={destination} replace />

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await login.mutateAsync({ username, password })
      void navigate(destination, { replace: true })
    } catch (err) {
      setError(defaultErrorMessage(err, { 401: t('login.invalidCredentials') }))
    }
  }

  return (
    <Box sx={{ maxWidth: 400, mx: 'auto', px: 2, py: { xs: 6, sm: 10 } }}>
      <Typography variant="h4" component="h1" align="center" gutterBottom>
        My Finances
      </Typography>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          {t('login.heading')}
        </Typography>
        <ErrorAlert message={error} onDismiss={() => setError(null)} />
        <Box
          component="form"
          onSubmit={(event) => void handleSubmit(event)}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
        >
          <TextField
            label={t('login.usernameLabel')}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            autoFocus
            required
            slotProps={{ htmlInput: { maxLength: MAX_USERNAME_LENGTH } }}
          />
          <TextField
            label={t('login.passwordLabel')}
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
            slotProps={{ htmlInput: { maxLength: MAX_PASSWORD_LENGTH } }}
          />
          <Button type="submit" variant="contained" disabled={login.isPending}>
            {login.isPending ? t('login.submitting') : t('login.submit')}
          </Button>
        </Box>
      </Paper>
    </Box>
  )
}
