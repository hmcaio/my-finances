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
 * Request body for {@code POST /api/transactions}. {@code type} is derived server-side from the
 * target category's own type (F004 spec) - never accepted as input here.
 *
 * <p>F024 (ADR 0021) adds the optional fuel fields: {@code vehicleId}/{@code fuelType}/{@code
 * liters}/{@code pricePerLiter} must be given all together or not at all (checked here, so a
 * partial payload is a clean 400 rather than a domain {@code NullPointerException}); whether they
 * line up with the target category being the dedicated fuel category is a {@code
 * TransactionService} concern ({@code FuelDetailsCategoryMismatchException}). {@code liters}/{@code
 * pricePerLiter} ({@code numeric(19,3)}) and {@code kmSinceLastFill}/{@code odometer} ({@code
 * numeric(19,1)}) carry {@code @Digits} matching those column scales (root {@code CLAUDE.md}'s
 * precision-exception list), so an out-of-range number is a 400, not a database error.
 */
public record CreateTransactionRequest(
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
    @Positive @Digits(integer = 18, fraction = 1) BigDecimal odometer,
    UUID investmentHoldingId) {

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
