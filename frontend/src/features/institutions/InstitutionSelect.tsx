import { useEffect, useMemo, useState } from 'react'
import { Autocomplete, Box, createFilterOptions, Skeleton, TextField } from '@mui/material'
import {
  createInstitution,
  getInstitutions,
  INSTITUTION_NAME_MAX_LENGTH,
  type Institution,
} from '../../api/institutions'
import { defaultErrorMessage } from '../../api/apiError'
import { LoadFailedNotice } from '../../components/LoadFailedNotice'
import { useAsyncData } from '../../hooks/useAsyncData'
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
   * Called with an institution created through the "Add “X”" entry, so a caller that keeps its own
   * institution list (to resolve ids to names) can add it too.
   */
  onCreated?: (institution: Institution) => void
  label?: string
  disabled?: boolean
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
}: InstitutionSelectProps) {
  const {
    data: institutions,
    setData: setInstitutions,
    loading,
    loadError,
    reload,
  } = useAsyncData(getInstitutions, [])
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
      const created = await createInstitution({ name: option.name })
      setInstitutions((prev) => [...(prev ?? []), created])
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
      <Skeleton variant="rounded" width={CONTROL_WIDTH} height={40} />
    ) : (
      <Box sx={{ width: CONTROL_WIDTH, height: 40 }} />
    )
  }

  return (
    <Autocomplete<Option, false, true>
      size="small"
      disableClearable
      disabled={disabled || creating}
      sx={{ width: CONTROL_WIDTH }}
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
