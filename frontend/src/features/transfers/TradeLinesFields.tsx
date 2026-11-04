import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  IconButton,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import type { Account } from '../../api/accounts/accounts'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { netSettlementDirection, type TradeSide } from './tradeMath'
import type { FormState, LineState } from './transferFormState'
import { duplicateResultingBalanceProductIds, tradeNetPreview } from './transferValidation'
import type { LineActions } from './useTransferForm'

interface TradeLinesFieldsProps {
  form: FormState
  /** Every account, to name the cash and investment side in the settlement preview. */
  accounts: Account[]
  /** The holdings of the picked investment account (`undefined` until loaded). */
  holdings: InvestmentHolding[] | undefined
  products: InvestmentProduct[] | undefined
  lineActions: LineActions
}

/**
 * A trade confirmation's product lines (side, quantity, unit price, optional resulting balance,
 * "close this holding" on SELL), the add-line button and the read-only net-settlement preview.
 */
export function TradeLinesFields({
  form,
  accounts,
  holdings,
  products,
  lineActions,
}: TradeLinesFieldsProps) {
  const { t } = useTranslation(['transfers'])
  const { locale } = useLocale()

  const productName = (id: string) => products?.find((p) => p.id === id)?.name ?? ''
  // A closed holding takes no new line, but stays selectable on the line that already holds it.
  const holdingOptionsFor = (line: LineState) =>
    (holdings ?? []).filter((h) => !h.closed || h.productId === line.productId)
  const duplicateResultingBalanceIds = duplicateResultingBalanceProductIds(form.lines)

  const netPreview = tradeNetPreview(form)
  const direction = netSettlementDirection(netPreview)
  const cashAccountName =
    accounts.find((a) => a.id === form.cashAccountId)?.name ??
    t('transfers:form.cashAccountFallback')
  const investmentAccountName =
    accounts.find((a) => a.id === form.investmentAccountId)?.name ??
    t('transfers:form.investmentAccountFallback')

  const lineRows = form.lines.map((line) => {
    const holdingOptions = holdingOptionsFor(line)
    const hasDuplicateResultingBalance =
      line.resultingBalance.trim() !== '' && duplicateResultingBalanceIds.has(line.productId)
    return (
      <Box
        key={line.key}
        sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap', mb: 1 }}
      >
        <Select
          size="small"
          displayEmpty
          value={holdingOptions.some((h) => h.productId === line.productId) ? line.productId : ''}
          onChange={(e) => lineActions.update(line.key, { productId: e.target.value })}
          aria-label={t('transfers:form.productLabel')}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="" disabled>
            {t('transfers:form.productLabel')}
          </MenuItem>
          {holdingOptions.map((h) => (
            <MenuItem key={h.id} value={h.productId}>
              {productName(h.productId)}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          value={line.side}
          onChange={(e) => lineActions.changeSide(line, e.target.value as TradeSide)}
          aria-label={t('transfers:form.sideAria')}
        >
          <MenuItem value="BUY">{t('transfers:form.buyOption')}</MenuItem>
          <MenuItem value="SELL">{t('transfers:form.sellOption')}</MenuItem>
        </Select>
        <TextField
          label={t('transfers:form.quantityLabel')}
          type="number"
          size="small"
          value={line.quantity}
          onChange={(e) => lineActions.update(line.key, { quantity: e.target.value })}
          slotProps={{ htmlInput: { step: 'any', min: '0' } }}
        />
        <TextField
          label={t('transfers:form.unitPriceLabel')}
          type="number"
          size="small"
          value={line.unitPrice}
          onChange={(e) => lineActions.update(line.key, { unitPrice: e.target.value })}
          slotProps={{ htmlInput: { step: 'any', min: '0' } }}
        />
        <TextField
          label={t('transfers:form.resultingBalanceLabel')}
          type="number"
          size="small"
          value={line.resultingBalance}
          onChange={(e) => lineActions.update(line.key, { resultingBalance: e.target.value })}
          error={hasDuplicateResultingBalance}
          helperText={
            hasDuplicateResultingBalance
              ? t('transfers:form.resultingBalanceDuplicateHelper')
              : t('transfers:form.resultingBalanceHelper')
          }
          slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
        />
        {line.side === 'SELL' && (
          <FormControlLabel
            control={
              <Checkbox
                checked={line.closeHolding}
                onChange={(e) => lineActions.update(line.key, { closeHolding: e.target.checked })}
              />
            }
            label={t('transfers:form.closeHoldingLabel')}
          />
        )}
        {form.lines.length > 1 && (
          <IconButton
            size="small"
            aria-label={t('transfers:form.removeLineAria')}
            onClick={() => lineActions.remove(line.key)}
            sx={{ alignSelf: 'center' }}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        )}
      </Box>
    )
  })

  return (
    <Box sx={{ mt: 2 }} role="group" aria-label={t('transfers:form.tradeLinesGroupAria')}>
      <Typography variant="subtitle2" gutterBottom>
        {t('transfers:form.productLinesTitle')}
      </Typography>
      {lineRows}
      <Button size="small" startIcon={<AddIcon />} onClick={lineActions.add} sx={{ mt: 1 }}>
        {t('transfers:form.addLineButton')}
      </Button>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }} aria-live="polite">
        {netPreview === null
          ? t('transfers:form.previewEnterValues')
          : netPreview === 0
            ? t('transfers:form.previewZero')
            : direction === 'cost'
              ? t('transfers:form.previewSettlement', {
                  amount: formatMoney(Math.abs(netPreview), locale),
                  from: cashAccountName,
                  to: investmentAccountName,
                })
              : t('transfers:form.previewSettlement', {
                  amount: formatMoney(Math.abs(netPreview), locale),
                  from: investmentAccountName,
                  to: cashAccountName,
                })}
      </Typography>
    </Box>
  )
}
