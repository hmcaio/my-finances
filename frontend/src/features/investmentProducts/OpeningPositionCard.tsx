import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, IconButton, Skeleton, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import type { OpeningPositionRequest } from '../../api/investments/investmentOpeningPositions'
import {
  useDeleteOpeningPosition,
  useOpeningPosition,
  useSaveOpeningPosition,
} from '../../api/investments/investmentOpeningPositionsQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { useAction } from '../../hooks/useAction'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { formatDate, formatMoney, formatQuantity, formatUnitPrice } from '../../i18n/format'
import { OpeningPositionDialog } from './OpeningPositionDialog'
import { PageSection } from '../../components/layout/PageSection'

interface OpeningPositionCardProps {
  /** The selected holding of the product detail page. */
  holding: InvestmentHolding
}

/** A plain `YYYY-MM-DD` date: formatted in UTC so no local time zone shifts it a day. */
const DATE_OPTIONS: Intl.DateTimeFormatOptions = { dateStyle: 'long', timeZone: 'UTC' }

/**
 * The selected holding's opening position (F038, ADR 0033), next to its current-value card: as-of
 * date, cotas, average price and the amount contributed, with Edit and Delete (confirmation), or an
 * "Add opening position" action when it has none. The actions are hidden for a closed holding (the
 * backend answers `409` to a change there). The empty state is data, not an error: the query maps
 * the backend's not-found problem type to `null`. Mutations refetch everything (ADR 0016), so the
 * holding's flag, snapshots, series and FII totals follow without any coordination here.
 */
export function OpeningPositionCard({ holding }: OpeningPositionCardProps) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const { locale } = useLocale()
  const query = useOpeningPosition(holding.id)
  const position = query.data
  const state = useQueryState(query)
  const showSkeleton = useDelayedFlag(state.loading)
  const save = useSaveOpeningPosition()
  const remove = useDeleteOpeningPosition()
  const [dialogOpen, setDialogOpen] = useState(false)
  // Fixed when the dialog opens: the refetch after a save must not flip an add dialog into an edit
  // one while it is still animating closed.
  const [editingExisting, setEditingExisting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const saveAction = useAction(setError)
  const deleteAction = useAction(setError)

  function openDialog() {
    setError(null)
    setEditingExisting(position != null)
    setDialogOpen(true)
  }

  async function handleSave(request: OpeningPositionRequest) {
    if (await saveAction.run(() => save.mutateAsync({ holdingId: holding.id, ...request }))) {
      setDialogOpen(false)
    }
  }

  async function handleDelete() {
    await deleteAction.run(() => remove.mutateAsync(holding.id))
    setConfirmDelete(false)
  }

  const editable = !holding.closed

  return (
    <PageSection>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="overline" color="text.secondary" sx={{ flexGrow: 1 }}>
          {t('investmentProducts:openingPosition.title')}
        </Typography>
        {editable && position && (
          <>
            <IconButton
              size="small"
              aria-label={t('investmentProducts:openingPosition.editAria')}
              onClick={openDialog}
            >
              <EditIcon fontSize="small" />
            </IconButton>
            <IconButton
              size="small"
              aria-label={t('investmentProducts:openingPosition.deleteAria')}
              onClick={() => {
                setError(null)
                setConfirmDelete(true)
              }}
            >
              <DeleteIcon fontSize="small" />
            </IconButton>
          </>
        )}
      </Box>

      <ErrorAlert message={dialogOpen ? null : error} onDismiss={() => setError(null)} />

      {state.loadError ? (
        <LoadFailedNotice message={state.loadError} onRetry={state.reload} />
      ) : state.loading ? (
        showSkeleton && (
          <Skeleton
            variant="rounded"
            height={96}
            role="status"
            aria-label={t('investmentProducts:openingPosition.loading')}
          />
        )
      ) : position ? (
        <Box component="dl" sx={{ ...fadeInSx, m: 0, display: 'grid', gap: 1 }}>
          <Row
            label={t('investmentProducts:openingPosition.asOfDate')}
            value={formatDate(position.asOfDate, locale, DATE_OPTIONS)}
          />
          <Row
            label={t('investmentProducts:openingPosition.quantity')}
            value={formatQuantity(position.quantity, locale)}
          />
          <Row
            label={t('investmentProducts:openingPosition.unitPrice')}
            value={formatUnitPrice(position.unitPrice, locale)}
          />
          <Row
            label={t('investmentProducts:openingPosition.amountContributed')}
            value={formatMoney(position.amountContributed, locale)}
          />
        </Box>
      ) : (
        <Box sx={fadeInSx}>
          <Typography variant="body2" color="text.secondary" sx={{ my: 1 }}>
            {t('investmentProducts:openingPosition.empty')}
          </Typography>
          {editable && (
            <Button variant="outlined" size="small" onClick={openDialog}>
              {t('investmentProducts:openingPosition.addButton')}
            </Button>
          )}
        </Box>
      )}

      <OpeningPositionDialog
        open={dialogOpen}
        position={editingExisting ? (position ?? null) : null}
        saving={saveAction.pending}
        error={error}
        onDismissError={() => setError(null)}
        onSave={(request) => void handleSave(request)}
        onClose={() => {
          setDialogOpen(false)
          setError(null)
        }}
      />

      <ConfirmDialog
        open={confirmDelete}
        title={t('investmentProducts:openingPosition.deleteTitle')}
        body={t('investmentProducts:openingPosition.deleteBody', {
          date: position ? formatDate(position.asOfDate, locale, DATE_OPTIONS) : '',
        })}
        confirmLabel={t('investmentProducts:openingPosition.deleteConfirm')}
        loading={deleteAction.pending}
        onConfirm={() => void handleDelete()}
        onCancel={() => setConfirmDelete(false)}
      />
    </PageSection>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
      <Typography component="dt" variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography component="dd" variant="body1" sx={{ m: 0, textAlign: 'right' }}>
        {value}
      </Typography>
    </Box>
  )
}
