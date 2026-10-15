import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  IconButton,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { CATEGORY_NAME_MAX_LENGTH, type CategoryType } from '../../api/categories/categories'
import { useCreateCategory } from '../../api/categories/categoriesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { SUGGESTED_CATEGORIES } from './suggestedCategories'

interface CustomEntry {
  id: number
  name: string
  type: CategoryType
}

interface CategorySuggestionsStepProps {
  /** Called with a message on failure, and with `null` when a new attempt starts. */
  onError: (message: string | null) => void
  /** Called once every desired category has been created (including zero of them). */
  onCompleted: () => void
}

/**
 * Onboarding's second step (F035/ADR 0028): a localized list of 11 suggested starter categories
 * (`suggestedCategories.ts`), pre-checked and grouped expense-then-income like `CategoriesPage`,
 * plus a repeatable "add your own" row. Finish is always enabled - zero selections is a valid,
 * fully-skippable outcome - and inserts every checked suggestion and every custom entry through
 * the ordinary `POST /api/categories` (F002), exactly like typing them in later would.
 *
 * Each created name is remembered locally (`createdNamesRef`) so that clicking Finish again after
 * a failure (the banner+retry pattern `AccountCreateForm` uses) only retries what didn't make it
 * in yet, rather than re-attempting an already-created name and hitting a 409 on it.
 */
export function CategorySuggestionsStep({ onError, onCompleted }: CategorySuggestionsStepProps) {
  const { t } = useTranslation(['onboarding', 'categories', 'common'])
  const [checked, setChecked] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(SUGGESTED_CATEGORIES.map((s) => [s.translationKey, true])),
  )
  const [customEntries, setCustomEntries] = useState<CustomEntry[]>([])
  const [customName, setCustomName] = useState('')
  const [customType, setCustomType] = useState<CategoryType>('EXPENSE')
  const [finishing, setFinishing] = useState(false)
  const nextCustomId = useRef(0)
  const createdNamesRef = useRef<Set<string>>(new Set())
  const createMutation = useCreateCategory()

  function toggle(translationKey: string) {
    setChecked((prev) => ({ ...prev, [translationKey]: !prev[translationKey] }))
  }

  function addCustomEntry() {
    const name = customName.trim()
    if (!name) return
    setCustomEntries((prev) => [...prev, { id: nextCustomId.current++, name, type: customType }])
    setCustomName('')
  }

  function removeCustomEntry(id: number) {
    setCustomEntries((prev) => prev.filter((entry) => entry.id !== id))
  }

  async function handleFinish() {
    onError(null)
    setFinishing(true)
    const desired = [
      ...SUGGESTED_CATEGORIES.filter((s) => checked[s.translationKey]).map((s) => ({
        name: t(s.translationKey as never),
        type: s.type,
      })),
      ...customEntries.map((entry) => ({ name: entry.name, type: entry.type })),
    ]
    try {
      for (const entry of desired) {
        if (createdNamesRef.current.has(entry.name)) continue
        await createMutation.mutateAsync({ name: entry.name, type: entry.type })
        createdNamesRef.current.add(entry.name)
      }
      onCompleted()
    } catch (err) {
      onError(defaultErrorMessage(err))
    } finally {
      setFinishing(false)
    }
  }

  const expenseSuggestions = SUGGESTED_CATEGORIES.filter((s) => s.type === 'EXPENSE')
  const incomeSuggestions = SUGGESTED_CATEGORIES.filter((s) => s.type === 'INCOME')

  return (
    <Box>
      <Typography variant="subtitle1" gutterBottom>
        {t('onboarding:categoriesHeading')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {t('onboarding:categoriesIntro')}
      </Typography>

      <Typography variant="subtitle2" sx={{ mt: 1 }}>
        {t('categories:typeExpense')}
      </Typography>
      <Stack sx={{ mb: 1 }}>
        {expenseSuggestions.map((suggestion) => (
          <FormControlLabel
            key={suggestion.translationKey}
            control={
              <Checkbox
                checked={checked[suggestion.translationKey] ?? false}
                onChange={() => toggle(suggestion.translationKey)}
              />
            }
            label={t(suggestion.translationKey as never)}
          />
        ))}
      </Stack>

      <Typography variant="subtitle2">{t('categories:typeIncome')}</Typography>
      <Stack sx={{ mb: 2 }}>
        {incomeSuggestions.map((suggestion) => (
          <FormControlLabel
            key={suggestion.translationKey}
            control={
              <Checkbox
                checked={checked[suggestion.translationKey] ?? false}
                onChange={() => toggle(suggestion.translationKey)}
              />
            }
            label={t(suggestion.translationKey as never)}
          />
        ))}
      </Stack>

      <Typography variant="subtitle2" gutterBottom>
        {t('onboarding:addCustomHeading')}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-start', mb: 1 }}>
        <TextField
          label={t('common:actions.name')}
          size="small"
          value={customName}
          onChange={(e) => setCustomName(e.target.value)}
          slotProps={{ htmlInput: { maxLength: CATEGORY_NAME_MAX_LENGTH } }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') addCustomEntry()
          }}
        />
        <Select
          size="small"
          value={customType}
          onChange={(e) => setCustomType(e.target.value as CategoryType)}
          aria-label={t('onboarding:customCategoryTypeLabel')}
        >
          <MenuItem value="EXPENSE">{t('categories:typeExpense')}</MenuItem>
          <MenuItem value="INCOME">{t('categories:typeIncome')}</MenuItem>
        </Select>
        <Button onClick={addCustomEntry} disabled={!customName.trim()}>
          {t('onboarding:addCustomButton')}
        </Button>
      </Box>

      {customEntries.length > 0 && (
        <Stack sx={{ mb: 2 }}>
          {customEntries.map((entry) => (
            <Box
              key={entry.id}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                justifyContent: 'space-between',
              }}
            >
              <Typography>
                {entry.name} (
                {entry.type === 'EXPENSE'
                  ? t('categories:typeExpense')
                  : t('categories:typeIncome')}
                )
              </Typography>
              <IconButton
                size="small"
                aria-label={t('onboarding:removeCustomCategory', { name: entry.name })}
                onClick={() => removeCustomEntry(entry.id)}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Box>
          ))}
        </Stack>
      )}

      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {t('onboarding:editLaterNote')}
      </Typography>

      <Button variant="contained" disabled={finishing} onClick={() => void handleFinish()}>
        {t('onboarding:finishButton')}
      </Button>
    </Box>
  )
}
