package com.chm.myfinances.domain.transfer;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of a {@link TradeConfirmation} (F027 spec, ADR 0024): one product, bought or sold, at a
 * quantity and unit price, optionally recording the holding's post-trade {@code resultingBalance}
 * and/or closing it ({@code closeHolding}, SELL-only). Replaces the old all-optional {@code
 * InvestmentTradeDetails} triple - once lines are explicit, a line with no quantity/price doesn't
 * make sense, so both are mandatory here (unlike the old record-only, all-optional shape).
 *
 * <p>Invariants: {@code quantity > 0} and {@code unitPrice > 0}, both mandatory; {@code
 * resultingBalance}, when given, is {@code >= 0}; {@code closeHolding} requires {@code side ==
 * SELL} (closing a holding you're adding to makes no sense, spec's Decisions).
 */
public record TradeConfirmationLine(
    UUID productId,
    TradeSide side,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal resultingBalance,
    boolean closeHolding) {

  public TradeConfirmationLine {
    Objects.requireNonNull(productId, "productId must not be null");
    Objects.requireNonNull(side, "side must not be null");
    Objects.requireNonNull(quantity, "quantity must not be null");
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    Objects.requireNonNull(unitPrice, "unitPrice must not be null");
    if (unitPrice.signum() <= 0) {
      throw new IllegalArgumentException("unitPrice must be positive");
    }
    if (resultingBalance != null && resultingBalance.signum() < 0) {
      throw new IllegalArgumentException("resultingBalance must not be negative");
    }
    if (closeHolding && side != TradeSide.SELL) {
      throw new IllegalArgumentException("closeHolding requires side == SELL");
    }
  }

  /** {@code quantity * unitPrice}, at full precision - not rounded (only the final net is). */
  public BigDecimal total() {
    return quantity.multiply(unitPrice);
  }
}
