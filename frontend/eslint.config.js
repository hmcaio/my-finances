import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import pluginQuery from '@tanstack/eslint-plugin-query'
import prettierConfig from 'eslint-config-prettier'

const useBreakpointMessage =
  'Use hooks/useBreakpointBand (or theme.breakpoints in sx) instead of a raw useMediaQuery.'

const rawMediaQueryImports = [
  { name: '@mui/material', importNames: ['useMediaQuery'], message: useBreakpointMessage },
  { name: '@mui/material/useMediaQuery', message: useBreakpointMessage },
]

const rawQueryImport = {
  name: '@tanstack/react-query',
  importNames: ['useQuery', 'useQueries', 'useInfiniteQuery', 'useSuspenseQuery', 'useMutation'],
  message:
    'Use the area hooks from src/api/<area>/<area>Queries instead of a raw query hook (F019).',
}

export default tseslint.config(
  {
    ignores: [
      'dist',
      'src/api/generated',
      'test-results',
      'coverage',
      'playwright-report',
      'e2e-results',
    ],
  },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // Everything logs through src/utils/logger.ts (F016, ADR 0011) - the override below is the
      // only place allowed to touch console.
      'no-console': 'error',
    },
  },
  {
    files: ['src/utils/logger.ts'],
    rules: { 'no-console': 'off' },
  },
  ...pluginQuery.configs['flat/recommended'],
  {
    // Breakpoint logic (F021, ADR 0019): `hooks/useBreakpointBand` is the one place that reads
    // viewport width; `Layout` (the nav switch at `lg`) is the one other reader. Everything else
    // uses the hook or `theme.breakpoints` in `sx`. Features get this same restriction again
    // below, because a later `no-restricted-imports` entry replaces an earlier one for its files.
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/hooks/useBreakpointBand.ts', 'src/components/layout/Layout.tsx'],
    rules: {
      'no-restricted-imports': ['error', { paths: rawMediaQueryImports }],
    },
  },
  {
    // Layering (F019, ADR 0016): feature code reaches the backend only through the `<area>Queries`
    // hooks, never by calling an `src/api/<area>/<area>` HTTP function itself and never with a raw
    // `useQuery`/`useMutation` (a hand-built key breaks request sharing). Types and constants can
    // still be imported from the area module. `importNamePattern` lists the verbs the area
    // modules' HTTP functions start with, plus `login`/`logout`: a new verb must be added here.
    files: ['src/features/**/*.{ts,tsx}'],
    ignores: ['**/*.test.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [...rawMediaQueryImports, rawQueryImport],
          patterns: [
            {
              group: [
                '**/api/*/*',
                '!**/api/*/*Queries',
                '!**/api/core/apiError',
                '!**/api/core/queryClient',
              ],
              importNamePattern:
                '^(get|create|rename|delete|edit|update|close|set|record|stop|reactivate|dismiss|confirm|download)[A-Z]|^(login|logout)$',
              message:
                'Call the area hooks from src/api/<area>/<area>Queries instead of the HTTP function (F019).',
            },
          ],
        },
      ],
    },
  },
  {
    // Shared components get the same raw-query ban as features: a component that needs data takes
    // it from an area hook. `Layout` stays exempt from the media-query ban (see above), so it is
    // left to that earlier block.
    files: ['src/components/**/*.{ts,tsx}'],
    ignores: ['**/*.test.{ts,tsx}', 'src/components/layout/Layout.tsx'],
    rules: {
      'no-restricted-imports': ['error', { paths: [...rawMediaQueryImports, rawQueryImport] }],
    },
  },
  prettierConfig,
)
