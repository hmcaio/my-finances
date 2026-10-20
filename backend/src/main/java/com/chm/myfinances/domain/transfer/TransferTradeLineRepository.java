package com.chm.myfinances.domain.transfer;

import java.util.List;
import java.util.UUID;

/**
 * Repository port for reading persisted {@link TradeConfirmationLine}s across every {@link
 * Transfer} (F027 spec, ADR 0024), superseding {@code TransferRepository}'s old {@code
 * findByInvestmentProductId}/{@code findAllInvestmentTrades}/{@code existsByInvestmentProductId}
 * now that one transfer can carry several lines. Implemented by an adapter in {@code
 * infrastructure/persistence/transfer}, backed by the {@code transfer_trade_lines} table.
 */
public interface TransferTradeLineRepository {

  /** Every line of every confirmation trading this product, in no particular order. */
  List<TransferTradeLine> findByProductId(UUID productId);

  /** Every persisted line - the snapshot-freshness staleness scan; trades are few. */
  List<TransferTradeLine> findAll();

  /**
   * Whether any line trades this product at this account - either endpoint of its parent transfer.
   * The holding-history check (F027, superseding F009/F022's transfer-based check).
   */
  boolean existsByProductIdAndAccountId(UUID productId, UUID accountId);
}
