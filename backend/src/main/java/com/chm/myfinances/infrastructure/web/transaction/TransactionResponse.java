package com.chm.myfinances.infrastructure.web.transaction;

import com.chm.myfinances.application.transaction.FuelRatios;
import com.chm.myfinances.application.transaction.FuelRatiosQuery;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of a {@link Transaction} (F004 spec). The fuel fields (F024, ADR 0021) are
 * {@code null} for an ordinary transaction; {@code kmPerLiter}/{@code amountPerKm}/{@code
 * litersPerKm} are computed on read via {@link FuelRatiosQuery}, never stored.
 */
public record TransactionResponse(
    UUID id,
    LocalDate date,
    BigDecimal amount,
    UUID categoryId,
    CategoryType type,
    UUID accountId,
    UUID paymentMethodId,
    UUID recurringTemplateVersionId,
    String description,
    String additionalNotes,
    UUID vehicleId,
    FuelType fuelType,
    BigDecimal liters,
    BigDecimal pricePerLiter,
    BigDecimal kmSinceLastFill,
    BigDecimal odometer,
    BigDecimal kmPerLiter,
    BigDecimal amountPerKm,
    BigDecimal litersPerKm) {

  public static TransactionResponse from(Transaction transaction) {
    FuelDetails fuel = transaction.getFuelDetails();
    FuelRatios ratios = FuelRatiosQuery.ratiosFor(transaction);
    return new TransactionResponse(
        transaction.getId(),
        transaction.getDate(),
        transaction.getAmount(),
        transaction.getCategoryId(),
        transaction.getType(),
        transaction.getAccountId(),
        transaction.getPaymentMethodId(),
        transaction.getRecurringTemplateVersionId(),
        transaction.getDescription(),
        transaction.getAdditionalNotes(),
        fuel == null ? null : fuel.vehicleId(),
        fuel == null ? null : fuel.fuelType(),
        fuel == null ? null : fuel.liters(),
        fuel == null ? null : fuel.pricePerLiter(),
        fuel == null ? null : fuel.kmSinceLastFill(),
        fuel == null ? null : fuel.odometer(),
        ratios.kmPerLiter(),
        ratios.amountPerKm(),
        ratios.litersPerKm());
  }
}
