package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/transfers} (F005 spec). {@code fromAccountId != toAccountId}
 * can't be expressed as a per-field bean validation constraint, so it's checked at the application
 * layer ({@code TransferService}) instead, mapped to a 400 via {@code
 * SameAccountTransferException}.
 *
 * <p>F009 makes a transfer able to be a buy/sell: {@code investmentProductId} tags it, {@code
 * quantity}/{@code unitPrice}/{@code taxes} record the trade (record-only; {@code quantity} and
 * {@code unitPrice} come together, both scale 8), and {@code resultingBalance} (create only) also
 * records a snapshot of the product on the transfer date. Trade fields and {@code resultingBalance}
 * without a product are a {@code 400} here; whether the product fits the accounts is a {@code 409}
 * in the service.
 */
public record CreateTransferRequest(
    @NotNull LocalDate date,
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes,
    UUID investmentProductId,
    @Positive @Digits(integer = 11, fraction = 8) BigDecimal quantity,
    @Positive @Digits(integer = 11, fraction = 8) BigDecimal unitPrice,
    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal taxes,
    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal resultingBalance) {

  @JsonIgnore
  @AssertTrue(message = "quantity, unitPrice and taxes require an investmentProductId")
  public boolean isTradeDetailsWithProduct() {
    return investmentProductId != null || (quantity == null && unitPrice == null && taxes == null);
  }

  @JsonIgnore
  @AssertTrue(message = "quantity and unitPrice must be given together")
  public boolean isQuantityWithUnitPrice() {
    return (quantity == null) == (unitPrice == null);
  }

  @JsonIgnore
  @AssertTrue(message = "resultingBalance requires an investmentProductId")
  public boolean isResultingBalanceWithProduct() {
    return investmentProductId != null || resultingBalance == null;
  }
}
