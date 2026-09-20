import { type PropsWithChildren } from 'react'
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
  Toolbar,
  Typography,
} from '@mui/material'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import { useColorMode } from '../hooks/useColorMode'

const DRAWER_WIDTH = 240

const NAV_ITEMS = [
  { label: 'Dashboard', path: '/' },
  { label: 'Transactions', path: '/transactions' },
  { label: 'Accounts', path: '/accounts' },
  { label: 'Transfers', path: '/transfers' },
  { label: 'Budgets', path: '/budgets' },
  { label: 'Recurring', path: '/recurring' },
  { label: 'Investments', path: '/investments' },
  { label: 'Categories', path: '/settings/categories' },
  { label: 'Institutions', path: '/settings/institutions' },
  { label: 'Payment Methods', path: '/settings/payment-methods' },
  { label: 'Export', path: '/export' },
]

/**
 * Shared AppBar + Drawer navigation wrapping every route except onboarding (F001 spec's
 * "shared Layout component" section). The dark-mode toggle lives in the AppBar so it's
 * available on every screen.
 */
export function Layout({ children }: PropsWithChildren) {
  const { mode, toggleMode } = useColorMode()
  const location = useLocation()

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
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
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <Divider />
        <List>
          {NAV_ITEMS.map((item) => (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                component={Link}
                to={item.path}
                selected={location.pathname === item.path}
              >
                <ListItemText primary={item.label} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        {children}
      </Box>
    </Box>
  )
}
