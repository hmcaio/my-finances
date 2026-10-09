package com.chm.myfinances.domain.transfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A read-only projection of one persisted {@link TradeConfirmationLine}, carrying enough of its
 * parent {@link Transfer} to resolve which accounts it settled between and to link back to the full
 * confirmation (F027 spec, ADR 0024): read directly off the {@code transfer_trade_lines} table by
 * {@link TransferTradeLineRepository}, rather than through the {@link Transfer} aggregate, for the
 * per-product queries (FII portfolio, value series, snapshot freshness, holding-history) that used
 * to scan every transfer tagged with a product one at a time.
 */
public record TransferTradeLine(
    UUID transferId,
    LocalDate date,
    UUID fromAccountId,
    UUID toAccountId,
    UUID productId,
    TradeSide side,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal resultingBalance) {}
