package com.chm.myfinances.application.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for a structurally malformed trade confirmation (F027 spec, ADR 0024): the create/edit
 * mutual-exclusivity rule between the plain {@code {fromAccountId, toAccountId, amount}} shape and
 * the {@code {cashAccountId, investmentAccountId, tradeConfirmation}} shape, or a net settlement
 * that computes to exactly zero ({@link
 * com.chm.myfinances.domain.transfer.TradeConfirmation#netCost}). Maps to 400 - none of these
 * depend on persisted account/holding state (that's {@link InvestmentTransferInvalidException},
 * 409); they're wrong regardless of what's in the database, same spirit as {@link
 * SameAccountTransferException}.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTradeConfirmationException extends RuntimeException {

  public InvalidTradeConfirmationException(String reason) {
    super("Invalid trade confirmation: " + reason);
  }
}
