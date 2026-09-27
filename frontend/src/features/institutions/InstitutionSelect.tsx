import { useEffect, useMemo, useState } from 'react'
import { Autocomplete, Box, createFilterOptions, Skeleton, TextField } from '@mui/material'
import { INSTITUTION_NAME_MAX_LENGTH, type Institution } from '../../api/institutions/institutions'
import { useCreateInstitution, useInstitutions } from '../../api/institutions/institutionsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { sortInstitutions } from './sortInstitutions'

/** A real institution, or the synthetic "Add “X”" entry offered for a name that doesn't exist. */
type Option = Institution & { isNew?: boolean }

const filter = createFilterOptions<Option>()

const CONTROL_WIDTH = 240

interface InstitutionSelectProps {
  /** The chosen institution id; `undefined` until the caller has one, which selects the default. */
  value: string | undefined
  onChange: (institutionId: string) => void
  /**
   * Called with an institution created through the "Add “X”" entry. Other views of the list need
   * nothing more: the shared institutions query refetches after the create.
   */
  onCreated?: (institution: Institution) => void
  label?: string
  disabled?: boolean
  /** Fill the parent's width (a form grid cell) instead of the fixed inline width. */
  fullWidth?: boolean
}

/**
 * Picker for the mandatory institution of an account (F017 spec): choose an existing one, or type a
 * new name and pick "Add “X”" to create and select it in one step. Not clearable - every account
 * has an institution. While `value` is `undefined` the built-in "No institution" row is selected
 * (and reported through `onChange`), so creating an account costs no extra step.
 *
 * Fetches the institution list itself, so any form can drop it in: the account form (F017), the
 * onboarding form (F011) and the investment-account form (F008).
 */
export function InstitutionSelect({
  value,
  onChange,
  onCreated,
  label = 'Institution',
  disabled = false,
  fullWidth = false,
}: InstitutionSelectProps) {
  const institutionsQuery = useInstitutions()
  const institutions = institutionsQuery.data
  const { loading, loadError, reload } = useQueryState(institutionsQuery)
  const createMutation = useCreateInstitution()
  const controlWidth = fullWidth ? '100%' : CONTROL_WIDTH
  const showSkeleton = useDelayedFlag(loading)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const options = useMemo(() => sortInstitutions(institutions ?? []), [institutions])
  const defaultId = (options.find((i) => i.builtIn) ?? options[0])?.id
  const selected = options.find((i) => i.id === value) ?? options.find((i) => i.id === defaultId)

  useEffect(() => {
    if (value === undefined && defaultId !== undefined) onChange(defaultId)
  }, [value, defaultId, onChange])

  async function handleChange(option: Option) {
    setCreateError(null)
    if (!option.isNew) {
      onChange(option.id)
      return
    }
    setCreating(true)
    try {
      const created = await createMutation.mutateAsync({ name: option.name })
      onCreated?.(created)
      onChange(created.id)
    } catch (err) {
      setCreateError(defaultErrorMessage(err))
    } finally {
      setCreating(false)
    }
  }

  if (loadError) {
    return <LoadFailedNotice message={loadError} onRetry={reload} />
  }
  if (!selected) {
    // Still loading (the skeleton is held back so a fast response never flashes it).
    return showSkeleton ? (
      <Skeleton variant="rounded" width={controlWidth} height={40} />
    ) : (
      <Box sx={{ width: controlWidth, height: 40 }} />
    )
  }

  return (
    <Autocomplete<Option, false, true>
      size="small"
      disableClearable
      disabled={disabled || creating}
      sx={{ width: controlWidth }}
      options={options}
      value={selected}
      isOptionEqualToValue={(option, current) => option.id === current.id}
      getOptionLabel={(option) => (option.isNew ? `Add “${option.name}”` : option.name)}
      filterOptions={(all, params) => {
        const filtered = filter(all, params)
        const typed = params.inputValue.trim()
        // Offer creation only for a name that isn't there yet (exact, like the backend's rule).
        if (typed && !all.some((option) => option.name === typed)) {
          filtered.push({ id: `new:${typed}`, name: typed, builtIn: false, isNew: true })
        }
        return filtered
      }}
      onChange={(_event, option) => void handleChange(option)}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          error={createError !== null}
          helperText={createError}
          slotProps={{
            ...params.slotProps,
            htmlInput: { ...params.slotProps.htmlInput, maxLength: INSTITUTION_NAME_MAX_LENGTH },
          }}
        />
      )}
    />
  )
}
