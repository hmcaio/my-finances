package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.transfer.TradeSide;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a {@code tradeConfirmation} request body (F027 spec, ADR 0024). Every invariant
 * {@link com.chm.myfinances.domain.transfer.TradeConfirmationLine}'s constructor itself checks is
 * replicated here so a validated request can never trip that domain constructor's {@code
 * IllegalArgumentException} (which would otherwise surface as a generic 500 - {@code
 * GlobalExceptionHandler} only maps {@code @ResponseStatus} exceptions).
 */
public record TradeConfirmationLineRequest(
    @NotNull UUID productId,
    @NotNull TradeSide side,
    @NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal quantity,
    @NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal unitPrice,
    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal resultingBalance,
    Boolean closeHolding) {

  /**
   * {@code closeHolding} defaults to {@code false} when omitted - boxed (not a primitive {@code
   * boolean}) so an absent field deserializes to {@code null} instead of tripping Jackson's {@code
   * FAIL_ON_NULL_FOR_PRIMITIVES}.
   */
  public boolean closeHoldingOrDefault() {
    return Boolean.TRUE.equals(closeHolding);
  }

  @JsonIgnore
  @AssertTrue(message = "closeHolding requires side == SELL")
  public boolean isCloseHoldingOnlyOnSell() {
    return !closeHoldingOrDefault() || side == TradeSide.SELL;
  }
}
