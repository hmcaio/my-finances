import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import pluginQuery from '@tanstack/eslint-plugin-query'
import prettierConfig from 'eslint-config-prettier'

export default tseslint.config(
  { ignores: ['dist', 'src/api/generated', 'test-results', 'coverage'] },
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
    // Layering (F019, ADR 0016): feature code reaches the backend only through the `<area>Queries`
    // hooks, never by calling an `src/api/<area>` HTTP function itself. Types, constants and the
    // conflict messages can still be imported from the area module.
    files: ['src/features/**/*.{ts,tsx}'],
    ignores: [
      '**/*.test.{ts,tsx}',
      // Areas not yet migrated to their <area>Queries hooks; each migration commit removes its
      // folder from this list, and the last one deletes the list (F019 plan, Phase 3).
    ],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/api/*', '!**/api/*Queries', '!**/api/apiError', '!**/api/queryClient'],
              importNamePattern:
                '^(get|create|rename|delete|edit|close|set|record|stop|reactivate|dismiss|confirm|download)[A-Z]',
              message:
                'Call the area hooks from src/api/<area>Queries instead of the HTTP function (F019).',
            },
          ],
        },
      ],
    },
  },
  prettierConfig,
)
