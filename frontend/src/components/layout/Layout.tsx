import { Fragment, useEffect, useState, type PropsWithChildren } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  AppBar,
  Box,
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
  useMediaQuery,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'
import MenuIcon from '@mui/icons-material/Menu'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import { useColorMode } from '../../hooks/colorMode/useColorMode'

const DRAWER_WIDTH = 240

interface NavItem {
  label: string
  path: string
}

const MAIN_NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', path: '/' },
  { label: 'Transactions', path: '/transactions' },
  { label: 'Accounts', path: '/accounts' },
  { label: 'Transfers', path: '/transfers' },
  { label: 'Budgets', path: '/budgets' },
  { label: 'Recurring', path: '/recurring' },
  { label: 'Investments', path: '/investments' },
]

const SETTINGS_NAV_ITEMS: NavItem[] = [
  { label: 'Categories', path: '/settings/categories' },
  { label: 'Investment Categories', path: '/settings/investment-categories' },
  { label: 'Institutions', path: '/settings/institutions' },
  { label: 'Payment Methods', path: '/settings/payment-methods' },
]

const EXPORT_NAV_ITEM: NavItem = { label: 'Export', path: '/export' }

function NavListItem({ item, pathname }: { item: NavItem; pathname: string }) {
  return (
    <ListItem disablePadding>
      <ListItemButton component={Link} to={item.path} selected={pathname === item.path}>
        <ListItemText primary={item.label} />
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
  const { pathname } = useLocation()
  const theme = useTheme()
  const isDesktop = useMediaQuery(theme.breakpoints.up('lg'))
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'))
  const [navOpen, setNavOpen] = useState(false)

  useEffect(() => {
    setNavOpen(false)
  }, [pathname])

  const nav = (
    <Fragment>
      <Toolbar />
      <Divider />
      <List>
        {MAIN_NAV_ITEMS.map((item) => (
          <NavListItem key={item.path} item={item} pathname={pathname} />
        ))}
        <ListSubheader component="div" disableSticky>
          Settings
        </ListSubheader>
        {SETTINGS_NAV_ITEMS.map((item) => (
          <NavListItem key={item.path} item={item} pathname={pathname} />
        ))}
        <Divider component="li" sx={{ my: 1 }} />
        <NavListItem item={EXPORT_NAV_ITEM} pathname={pathname} />
      </List>
    </Fragment>
  )

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          {!isDesktop && (
            <IconButton
              color="inherit"
              edge="start"
              onClick={() => setNavOpen(true)}
              aria-label="Open navigation"
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
            onClick={toggleMode}
            aria-label="Toggle dark mode"
            title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
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
          onClose={() => setNavOpen(false)}
          sx={{ [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
        >
          {nav}
        </Drawer>
      )}
      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, maxWidth: 1600, p: isMobile ? 2 : 3 }}>
        <Toolbar />
        {children}
      </Box>
    </Box>
  )
}
