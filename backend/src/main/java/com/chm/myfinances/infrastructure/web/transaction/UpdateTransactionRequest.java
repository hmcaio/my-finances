package com.chm.myfinances.infrastructure.web.transaction;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.domain.transaction.FuelType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/transactions/{id}}. Every editable field is required (a
 * full-replace body), not a partial patch - same convention as F002's {@code
 * UpdateCategoryRequest}/F003's {@code UpdateAccountRequest}: F004 spec says every one of these
 * fields (amount/date/category/account/payment method/description/additional notes) is plainly
 * editable in place, so there's no immutable-subset split like Account's type/openingBalance to
 * carve out of the DTO. {@code recurringTemplateVersionId} has no field here at all - it's set once
 * at creation and never touched by edit (PRD S5.7).
 *
 * <p>F024 (ADR 0021): same optional fuel fields and "given together" check as {@code
 * CreateTransactionRequest}. Omitting them (all {@code null}) clears any previously recorded fuel
 * details - editing a fuel transaction's category away is rejected by {@code TransactionService}
 * until the fuel fields are cleared this way.
 */
public record UpdateTransactionRequest(
    @NotNull LocalDate date,
    @NotNull @Positive BigDecimal amount,
    @NotNull UUID categoryId,
    @NotNull UUID accountId,
    @NotNull UUID paymentMethodId,
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes,
    UUID vehicleId,
    FuelType fuelType,
    @Positive @Digits(integer = 16, fraction = 3) BigDecimal liters,
    @Positive @Digits(integer = 16, fraction = 3) BigDecimal pricePerLiter,
    @Positive @Digits(integer = 18, fraction = 1) BigDecimal kmSinceLastFill,
    @Positive @Digits(integer = 18, fraction = 1) BigDecimal odometer) {

  @JsonIgnore
  @AssertTrue(message = "vehicleId, fuelType, liters and pricePerLiter must be given together")
  public boolean isFuelFieldsGivenTogether() {
    int presentCount =
        (vehicleId != null ? 1 : 0)
            + (fuelType != null ? 1 : 0)
            + (liters != null ? 1 : 0)
            + (pricePerLiter != null ? 1 : 0);
    return presentCount == 0 || presentCount == 4;
  }
}
