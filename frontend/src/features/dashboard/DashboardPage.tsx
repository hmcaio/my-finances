import { useEffect, useState } from 'react'
import { Alert, Box, CircularProgress, Paper, Typography } from '@mui/material'
import { getHealth, type HealthResponse } from '../../api/health'
import { NetWorthTrendChart } from '../netWorth/NetWorthTrendChart'

type Status = 'loading' | 'up' | 'error'

/**
 * Stands in for the real Dashboard (F012, not built yet). Also serves as F001's
 * frontend-to-backend connectivity check: calls the health-check endpoint and displays the
 * result (see F001 spec's "Add a placeholder page..." requirement). Also hosts F010's net worth
 * trend widget until F012 replaces this component entirely (and embeds the widget itself).
 */
export function DashboardPage() {
  const [status, setStatus] = useState<Status>('loading')
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    getHealth()
      .then((result) => {
        if (cancelled) return
        setHealth(result)
        setStatus('up')
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Unknown error')
        setStatus('error')
      })

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Dashboard
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        The real dashboard (spend by category, budgets, net worth trend, ...) arrives with F012. For
        now, this page confirms the frontend can reach the backend.
      </Typography>

      <Paper variant="outlined" sx={{ p: 3, maxWidth: 720, mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Net worth
        </Typography>
        <NetWorthTrendChart />
      </Paper>

      <Paper variant="outlined" sx={{ p: 3, maxWidth: 480 }}>
        <Typography variant="subtitle1" gutterBottom>
          Backend connectivity
        </Typography>
        {status === 'loading' && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={20} />
            <Typography>Checking backend health...</Typography>
          </Box>
        )}
        {status === 'up' && health && (
          <Alert severity="success">
            Backend is {health.status} as of {new Date(health.timestamp).toLocaleString()}
          </Alert>
        )}
        {status === 'error' && (
          <Alert severity="error">
            Could not reach the backend at http://localhost:8080 ({error}). Is it running (
            ./gradlew bootRun)?
          </Alert>
        )}
      </Paper>
    </Box>
  )
}
