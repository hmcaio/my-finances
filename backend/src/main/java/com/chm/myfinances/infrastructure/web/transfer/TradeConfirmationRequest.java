package com.chm.myfinances.infrastructure.web.transfer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@code tradeConfirmation} body of {@code CreateTransferRequest}/{@code UpdateTransferRequest}
 * (F027 spec, ADR 0024): a settlement's single aggregate tax figure and its one-or-more product
 * lines. {@code amount}/{@code fromAccountId}/{@code toAccountId} are never part of this shape -
 * the backend derives them from {@link
 * com.chm.myfinances.domain.transfer.TradeConfirmation#netCost}.
 *
 * <p>{@link #isAtMostOneResultingBalancePerProduct} replicates {@code TradeConfirmation.of}'s
 * cross-line invariant at the DTO layer (same rationale as {@code TradeConfirmationLineRequest}'s
 * own {@code @AssertTrue}s) so a validated request can't trip that constructor's {@code
 * IllegalArgumentException}. The net-settlement-isn't-zero invariant is NOT replicated here - it
 * needs the same rounding arithmetic as the domain, so duplicating it into a DTO would be
 * duplicating domain logic; it's instead caught at the application layer ({@code TransferService},
 * translated to {@code InvalidTradeConfirmationException}, 400).
 */
public record TradeConfirmationRequest(
    @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal taxes,
    @NotEmpty @Valid List<TradeConfirmationLineRequest> lines) {

  @JsonIgnore
  @AssertTrue(message = "at most one line per product may carry a resultingBalance")
  public boolean isAtMostOneResultingBalancePerProduct() {
    if (lines == null) {
      return true;
    }
    Set<Object> seen = new HashSet<>();
    for (TradeConfirmationLineRequest line : lines) {
      if (line.resultingBalance() != null && !seen.add(line.productId())) {
        return false;
      }
    }
    return true;
  }
}
