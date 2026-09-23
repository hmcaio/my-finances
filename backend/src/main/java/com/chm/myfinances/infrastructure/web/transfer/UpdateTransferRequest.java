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
 * Request body for {@code PATCH /api/transfers/{id}}. Every editable field is required (a
 * full-replace body), not a partial patch - same convention as F002/F003/F004's own PATCH endpoints
 * (F005 spec: "No versioning, no editing after creation beyond a plain field update"). The
 * investment fields (F009) are full-replace too: omitting {@code investmentProductId} clears the
 * tag. There is no {@code resultingBalance} here - editing never touches snapshots.
 */
public record UpdateTransferRequest(
    @NotNull LocalDate date,
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes,
    UUID investmentProductId,
    @Positive @Digits(integer = 11, fraction = 8) BigDecimal quantity,
    @Positive @Digits(integer = 11, fraction = 8) BigDecimal unitPrice,
    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal taxes) {

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
}
