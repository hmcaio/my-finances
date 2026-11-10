import { Fragment, useRef, useState, type MouseEvent, type PropsWithChildren } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { ParseKeys, TFunction } from 'i18next'
import {
  AppBar,
  Box,
  Collapse,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  ListSubheader,
  Toolbar,
  Typography,
  type Theme,
} from '@mui/material'
import MenuIcon from '@mui/icons-material/Menu'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { useLogout } from '../../api/auth/authQueries'
import { useColorMode } from '../../providers/colorMode/useColorMode'
import { useLocale } from '../../providers/locale/useLocale'
import { NotificationHost } from '../../providers/notifications/NotificationHost'
import { useBreakpointBand } from '../../hooks/useBreakpointBand'
import { DRAWER_WIDTH } from './layoutSx'

type NavLabelKey = ParseKeys<'common'>

interface NavItem {
  labelKey: NavLabelKey
  path: string
}

const MAIN_NAV_ITEMS: NavItem[] = [
  { labelKey: 'nav.dashboard', path: '/' },
  { labelKey: 'nav.ledger', path: '/ledger' },
  { labelKey: 'nav.fuel', path: '/fuel' },
  { labelKey: 'nav.accounts', path: '/accounts' },
  { labelKey: 'nav.budgets', path: '/budgets' },
  { labelKey: 'nav.recurring', path: '/recurring' },
  { labelKey: 'nav.investments', path: '/investments' },
  { labelKey: 'nav.goals', path: '/goals' },
  { labelKey: 'nav.fiiPortfolio', path: '/fii' },
]

// Categories/Institutions/Vehicles stay flat, fully-editable nav items - only specific rows on
// those are protected (F034/ADR 0028), the rest of each list is still real user data.
const SETTINGS_NAV_ITEMS: NavItem[] = [
  { labelKey: 'nav.categories', path: '/settings/categories' },
  { labelKey: 'nav.institutions', path: '/settings/institutions' },
  { labelKey: 'nav.vehicles', path: '/settings/vehicles' },
]

// F034/ADR 0028: these three lists lost every mutation affordance - every row is fixed,
// migration-seeded content - grouped together under a collapsed-by-default "System values"
// section so the flat nav doesn't grow for lists nobody edits anymore. Still useful to browse
// (the app still picks from them elsewhere), just not worth a top-level flat entry each.
const SYSTEM_VALUES_NAV_ITEMS: NavItem[] = [
  { labelKey: 'nav.paymentMethods', path: '/settings/payment-methods' },
  { labelKey: 'nav.investmentCategories', path: '/settings/investment-categories' },
  { labelKey: 'nav.investmentSegments', path: '/settings/investment-segments' },
]

const MAIN_CONTENT_ID = 'main-content'

/** The Settings group label: a section heading, not a disabled-looking item (F040). */
const SETTINGS_HEADING_SX = {
  color: 'text.primary',
  fontSize: '0.75rem',
  fontWeight: 700,
  letterSpacing: '0.08em',
  lineHeight: '36px',
  textTransform: 'uppercase',
} as const

/** Hidden until it takes keyboard focus, then drawn above the AppBar (F040). The clip-based
 * hiding keeps it a real tab stop; `1px`, not a bare number, because `width: 1` means 100%. */
const SKIP_LINK_SX = (theme: Theme) => ({
  '&:not(:focus)': {
    position: 'absolute',
    width: '1px',
    height: '1px',
    overflow: 'hidden',
    clip: 'rect(0 0 0 0)',
    clipPath: 'inset(50%)',
    whiteSpace: 'nowrap',
  },
  '&:focus': {
    position: 'fixed',
    top: theme.spacing(1),
    left: theme.spacing(1),
    zIndex: theme.zIndex.drawer + 2,
    px: 2,
    py: 1,
    borderRadius: 1,
    boxShadow: theme.shadows[4],
    bgcolor: 'background.paper',
    color: 'text.primary',
    fontWeight: 500,
    textDecoration: 'none',
  },
})

const ACTIVITY_NAV_ITEM: NavItem = { labelKey: 'nav.activity', path: '/activity' }
const REPORTS_NAV_ITEM: NavItem = { labelKey: 'nav.reports', path: '/reports' }

function NavListItem({
  item,
  pathname,
  t,
  indent,
}: {
  item: NavItem
  pathname: string
  t: TFunction<'common'>
  indent?: boolean
}) {
  return (
    <ListItem disablePadding>
      <ListItemButton
        component={Link}
        to={item.path}
        selected={pathname === item.path}
        sx={indent ? { pl: 4 } : undefined}
      >
        <ListItemText primary={t(item.labelKey)} />
      </ListItemButton>
    </ListItem>
  )
}

/**
 * Shared AppBar + Drawer navigation wrapping every route except onboarding (F001 spec's
 * "shared Layout component" section). The dark-mode toggle lives in the AppBar so it's
 * available on every screen. Responsive (F021): a permanent drawer from `lg` (1200px) up, a
 * temporary drawer behind an AppBar hamburger below it that closes when the route changes.
 */
export function Layout({ children }: PropsWithChildren) {
  const { mode, toggleMode } = useColorMode()
  const { locale, toggleLocale } = useLocale()
  const { t } = useTranslation()
  const logout = useLogout()
  const { pathname } = useLocation()
  const band = useBreakpointBand()
  const isDesktop = band === 'desktop'
  const isMobile = band === 'mobile'
  // The drawer closes as soon as the pathname changes from whatever it was open on, without an
  // effect: comparing against the *current* pathname (not a remembered one) means a later
  // navigation back to that same pathname can't make it reopen by itself.
  const [drawerState, setDrawerState] = useState({ pathname, open: false })
  if (drawerState.pathname !== pathname) {
    setDrawerState({ pathname, open: false })
  }
  const navOpen = drawerState.open
  const mainRef = useRef<HTMLElement>(null)

  // The anchor's own jump would add a hash to the URL and, on a `tabIndex={-1}` target, is not
  // reliable about moving focus: focus the region directly.
  function skipToContent(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault()
    mainRef.current?.focus()
  }
  // Collapsed by default, local state only - not persisted to localStorage, same as every other
  // nav state in this app (F034/ADR 0028). Resets on reload.
  const [systemValuesOpen, setSystemValuesOpen] = useState(false)

  const nav = (
    <Fragment>
      <Toolbar />
      <Divider />
      <List>
        {MAIN_NAV_ITEMS.map((item) => (
          <NavListItem key={item.path} item={item} pathname={pathname} t={t} />
        ))}
        <ListSubheader component="div" disableSticky sx={SETTINGS_HEADING_SX}>
          {t('nav.settingsHeading')}
        </ListSubheader>
        {SETTINGS_NAV_ITEMS.map((item) => (
          <NavListItem key={item.path} item={item} pathname={pathname} t={t} />
        ))}
        <ListItem disablePadding>
          <ListItemButton
            onClick={() => setSystemValuesOpen((prev) => !prev)}
            aria-expanded={systemValuesOpen}
          >
            <ListItemText primary={t('nav.systemValues')} />
            {systemValuesOpen ? (
              <ExpandLessIcon fontSize="small" />
            ) : (
              <ExpandMoreIcon fontSize="small" />
            )}
          </ListItemButton>
        </ListItem>
        <Collapse in={systemValuesOpen} unmountOnExit>
          <List component="div" disablePadding>
            {SYSTEM_VALUES_NAV_ITEMS.map((item) => (
              <NavListItem key={item.path} item={item} pathname={pathname} t={t} indent />
            ))}
          </List>
        </Collapse>
        <Divider component="li" sx={{ my: 1 }} />
        <NavListItem item={ACTIVITY_NAV_ITEM} pathname={pathname} t={t} />
        <NavListItem item={REPORTS_NAV_ITEM} pathname={pathname} t={t} />
      </List>
    </Fragment>
  )

  return (
    <Box sx={{ display: 'flex' }}>
      <Box component="a" href={`#${MAIN_CONTENT_ID}`} onClick={skipToContent} sx={SKIP_LINK_SX}>
        {t('nav.skipToContent')}
      </Box>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
          {!isDesktop && (
            <IconButton
              color="inherit"
              edge="start"
              onClick={() => setDrawerState({ pathname, open: true })}
              aria-label={t('layout.openNavigation')}
              sx={{ mr: 1 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            My Finances
          </Typography>
          <IconButton
            color="inherit"
            onClick={toggleLocale}
            aria-label={
              locale === 'pt-BR' ? t('language.switchToEnglish') : t('language.switchToPortuguese')
            }
            title={
              locale === 'pt-BR' ? t('language.switchToEnglish') : t('language.switchToPortuguese')
            }
          >
            <Typography variant="button" sx={{ fontWeight: 700, lineHeight: 1 }}>
              {locale === 'pt-BR' ? 'EN' : 'PT'}
            </Typography>
          </IconButton>
          <IconButton
            color="inherit"
            onClick={toggleMode}
            aria-label={t('layout.toggleDarkMode')}
            title={mode === 'dark' ? t('layout.switchToLightMode') : t('layout.switchToDarkMode')}
          >
            {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
          </IconButton>
          {/* F036: ends the session; the route guard then sends the user to /login. */}
          <IconButton
            color="inherit"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            aria-label={t('layout.logout')}
            title={t('layout.logout')}
          >
            <LogoutIcon />
          </IconButton>
        </Toolbar>
      </AppBar>
      {isDesktop ? (
        <Drawer
          variant="permanent"
          sx={{
            width: DRAWER_WIDTH,
            flexShrink: 0,
            [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: 'border-box' },
          }}
        >
          {nav}
        </Drawer>
      ) : (
        <Drawer
          variant="temporary"
          open={navOpen}
          onClose={() => setDrawerState({ pathname, open: false })}
          sx={{ [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
        >
          {nav}
        </Drawer>
      )}
      <Box
        component="main"
        id={MAIN_CONTENT_ID}
        tabIndex={-1}
        ref={mainRef}
        sx={{
          flexGrow: 1,
          minWidth: 0,
          maxWidth: 1600,
          p: isMobile ? 2 : 3,
          // Focused by the skip link only: a ring around the whole page would be noise.
          '&:focus': { outline: 'none' },
        }}
      >
        <Toolbar />
        {children}
      </Box>
      <NotificationHost />
    </Box>
  )
}
