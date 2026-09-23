package com.chm.myfinances.domain.transfer;

import java.math.BigDecimal;

/**
 * Record-only details of a buy/sell {@link Transfer} (F009 spec, ADR 0012): units traded, price per
 * unit and taxes/fees. Nothing is computed from them - {@code Transfer.amount} stays the cash that
 * actually moved and the only figure balances use - so every field is optional (fixed income and
 * pension plans have no units).
 *
 * <p>Invariants: {@code quantity} and {@code unitPrice} are both present or both absent, each
 * {@code > 0}; {@code taxes}, when present, is {@code >= 0}.
 */
public record InvestmentTradeDetails(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxes) {

  private static final InvestmentTradeDetails EMPTY = new InvestmentTradeDetails(null, null, null);

  public InvestmentTradeDetails {
    if ((quantity == null) != (unitPrice == null)) {
      throw new IllegalArgumentException("quantity and unitPrice must be given together");
    }
    if (quantity != null && quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (unitPrice != null && unitPrice.signum() <= 0) {
      throw new IllegalArgumentException("unitPrice must be positive");
    }
    if (taxes != null && taxes.signum() < 0) {
      throw new IllegalArgumentException("taxes must not be negative");
    }
  }

  /** No trade details at all - a plain transfer, or a trade recorded by amount only. */
  public static InvestmentTradeDetails empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return quantity == null && unitPrice == null && taxes == null;
  }
}
