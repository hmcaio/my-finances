import { useState } from 'react'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import type { TransferFormPreset } from '../transfers/TransferForm'

/**
 * State of the Buy/Sell dialog for the selected holding: `preset` is the transfer form's preset
 * (`null` while closed) and `formKey` changes per `open`, so the form remounts fresh for each
 * Buy/Sell click. `open` does nothing until both the holding and the product are loaded.
 */
export function useTradeDialog({
  holding,
  product,
  setError,
}: {
  holding: InvestmentHolding | undefined
  product: InvestmentProduct | undefined
  setError: (message: string | null) => void
}) {
  const [preset, setPreset] = useState<TransferFormPreset | null>(null)
  const [formKey, setFormKey] = useState(0)

  function open(direction: 'buy' | 'sell') {
    if (!holding || !product) return
    setError(null)
    setFormKey((n) => n + 1)
    setPreset({
      direction,
      investmentAccountId: holding.accountId,
      productId: product.id,
      productName: product.name,
    })
  }

  return { preset, formKey, open, close: () => setPreset(null) }
}

export type TradeDialogState = ReturnType<typeof useTradeDialog>
