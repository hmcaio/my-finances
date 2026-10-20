package com.chm.myfinances.domain.transfer;

/**
 * Whether one {@link TradeConfirmationLine} of a {@link TradeConfirmation} buys or sells its
 * product (F027 spec, ADR 0024). Replaces the old implicit "direction derived from which account is
 * the transfer's {@code fromAccountId}/{@code toAccountId}" rule (ADR 0012) now that one
 * confirmation can mix both in the same settlement.
 */
public enum TradeSide {
  BUY,
  SELL
}
