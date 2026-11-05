import { Suspense, useMemo, useState } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Box, CssBaseline, Skeleton, ThemeProvider } from '@mui/material'
import { ColorModeProvider } from './providers/colorMode/ColorModeProvider'
import { useColorMode } from './providers/colorMode/useColorMode'
import { LocaleProvider } from './providers/locale/LocaleProvider'
import { useHasAccounts } from './hooks/useHasAccounts'
import { getTheme } from './theme'
import { Layout } from './components/layout/Layout'
import { BackupStatusBanner } from './components/layout/BackupStatusBanner'
import { LoadFailedNotice } from './components/feedback/LoadFailedNotice'
import { fadeInSx } from './components/feedback/fadeIn'
import { useDelayedFlag } from './hooks/useDelayedFlag'
import { OnboardingPage } from './features/onboarding/OnboardingPage'
import { RequireAuth } from './features/auth/RequireAuth'
import { LoginPage } from './routes/lazyPages'
import { AuthenticatedRoutes } from './routes/AuthenticatedRoutes'
import { PageErrorBoundary } from './routes/PageErrorBoundary'
import { RouteFallback } from './routes/RouteFallback'

/**
 * Everything behind the login (F036, ADR 0029): mounted only once `RequireAuth` has a session, so
 * none of its data hooks (accounts gate, backup banner, pages) fire while signed out.
 */
function AuthenticatedApp() {
  // Onboarding (F011) is not part of the route tree: it's a top-level check gating whether
  // the router+layout shell renders at all. `hasAccounts` stays null while the check is loading
  // or failed, so neither the shell nor onboarding renders on a guess.
  const { hasAccounts, loading, loadError, reload } = useHasAccounts()
  const showSkeleton = useDelayedFlag(loading)

  // F035: onboarding is now two steps (account, then category suggestions), but creating the
  // account alone already flips `hasAccounts` to `true` - every mutation invalidates every query
  // (frontend CLAUDE.md), so the accounts list refetches and resolves non-empty the instant step 1
  // succeeds, well before `OnboardingPage`'s own `activeStep` state renders step 2. Gating purely
  // on `hasAccounts` would swap onboarding for the dashboard mid-flow, silently skipping step 2.
  // `inOnboarding` latches "we entered onboarding" the first time `hasAccounts` resolves `false`
  // and only releases on `OnboardingPage`'s own `onCompleted` (step 2 finishing, not `hasAccounts`
  // flipping) - not persisted anywhere, just render-session state, consistent with this feature's
  // "no new persisted flag" decision (ADR 0028): a reload mid-flow re-derives from `hasAccounts`
  // alone and lands on the dashboard, same as finishing onboarding normally.
  //
  // Adjusted during render (React's documented pattern for state derived from a prop/other state
  // change), not in a `useEffect`, so the latch is already set on the very render that observes
  // `hasAccounts` turn `false` - an effect would only run after that render committed, one tick
  // too late for `showOnboarding` below to reflect it on the same render.
  const [prevHasAccounts, setPrevHasAccounts] = useState(hasAccounts)
  const [inOnboarding, setInOnboarding] = useState(hasAccounts === false)
  if (hasAccounts !== prevHasAccounts) {
    setPrevHasAccounts(hasAccounts)
    if (hasAccounts === false) setInOnboarding(true)
  }
  const showOnboarding = inOnboarding || hasAccounts === false

  return (
    <>
      {/* F018: not behind the onboarding gate below, so it shows regardless of whether
          onboarding has happened yet or which page (if any) is open. */}
      <BackupStatusBanner />
      {hasAccounts === null && !inOnboarding ? (
        <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: 6 }}>
          {loadError !== null ? (
            <LoadFailedNotice message={loadError} onRetry={reload} />
          ) : (
            showSkeleton && (
              <Box sx={fadeInSx}>
                <Skeleton variant="text" width="60%" height={48} />
                <Skeleton variant="rounded" height={120} />
              </Box>
            )
          )}
        </Box>
      ) : !showOnboarding ? (
        <Layout>
          <PageErrorBoundary>
            <Suspense fallback={<RouteFallback />}>
              <AuthenticatedRoutes />
            </Suspense>
          </PageErrorBoundary>
        </Layout>
      ) : (
        <OnboardingPage onCompleted={() => setInOnboarding(false)} />
      )}
    </>
  )
}

function ThemedApp() {
  const { mode } = useColorMode()
  const theme = useMemo(() => getTheme(mode), [mode])
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          {/* The one route reachable without a session; everything else is behind the guard. */}
          <Route
            path="/login"
            element={
              <Suspense fallback={<RouteFallback />}>
                <LoginPage />
              </Suspense>
            }
          />
          <Route
            path="*"
            element={
              <RequireAuth>
                <AuthenticatedApp />
              </RequireAuth>
            }
          />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  )
}

function App() {
  return (
    <ColorModeProvider>
      <LocaleProvider>
        <ThemedApp />
      </LocaleProvider>
    </ColorModeProvider>
  )
}

export default App
