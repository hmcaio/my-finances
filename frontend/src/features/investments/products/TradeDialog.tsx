import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { DialogTitle } from '@mui/material'
import type { Account } from '../../../api/accounts/accounts'
import { ResponsiveDialog } from '../../../components/feedback/ResponsiveDialog'
import { TransferForm } from '../../transfers/TransferForm'
import type { TradeDialogState } from './useTradeDialog'

interface TradeDialogProps {
  trade: TradeDialogState
  productName: string | undefined
  accounts: Account[] | undefined
  setError: (message: string | null) => void
  /** The page's error banner, shown inside the dialog (the page one is behind it). */
  banner: ReactNode
}

/** The Buy/Sell transfer form for the selected holding, preset to this product. */
export function TradeDialog({ trade, productName, accounts, setError, banner }: TradeDialogProps) {
  const { t } = useTranslation('investmentProducts')
  const { preset } = trade
  return (
    <ResponsiveDialog open={preset !== null} onClose={trade.close} maxWidth="md" fullWidth>
      <DialogTitle>
        {t('detail.tradeDialogTitle', {
          direction: preset?.direction === 'sell' ? t('detail.sellButton') : t('detail.buyButton'),
          product: productName,
        })}
      </DialogTitle>
      {preset && accounts && (
        <TransferForm
          key={trade.formKey}
          dialog
          banner={banner}
          accounts={accounts}
          preset={preset}
          onSaved={trade.close}
          onError={setError}
          onCancel={trade.close}
        />
      )}
    </ResponsiveDialog>
  )
}
