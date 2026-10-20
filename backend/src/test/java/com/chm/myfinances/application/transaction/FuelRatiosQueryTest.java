package com.chm.myfinances.application.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FuelRatiosQuery} (F024 spec, ADR 0021): km/L, spend/km and L/km computed
 * correctly when {@code kmSinceLastFill} is present, all {@code null} when absent - written first
 * (ADR 0004), pure JUnit, no Spring context (needs no repository).
 */
class FuelRatiosQueryTest {

  private static final UUID CATEGORY_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private static final UUID PAYMENT_METHOD_ID = UUID.randomUUID();
  private static final UUID VEHICLE_ID = UUID.randomUUID();

  private static Transaction fuelTransaction(BigDecimal amount, FuelDetails fuelDetails) {
    return Transaction.create(
        UUID.randomUUID(),
        LocalDate.now(),
        amount,
        CATEGORY_ID,
        CategoryType.EXPENSE,
        ACCOUNT_ID,
        PAYMENT_METHOD_ID,
        null,
        "Fill up",
        null,
        fuelDetails);
  }

  @Test
  void computesEveryRatioWhenKmSinceLastFillIsPresent() {
    FuelDetails details =
        new FuelDetails(
            VEHICLE_ID,
            FuelType.GASOLINA,
            new BigDecimal("40"),
            new BigDecimal("5"),
            new BigDecimal("400"),
            null);
    Transaction transaction = fuelTransaction(new BigDecimal("200.00"), details);

    FuelRatios ratios = FuelRatiosQuery.ratiosFor(transaction);

    // 400 km / 40 L = 10 km/L; 200 / 400 km = 0.5 amount/km; 40 L / 400 km = 0.1 L/km.
    assertThat(ratios.kmPerLiter()).isEqualByComparingTo("10.0000");
    assertThat(ratios.amountPerKm()).isEqualByComparingTo("0.5000");
    assertThat(ratios.litersPerKm()).isEqualByComparingTo("0.1000");
  }

  @Test
  void everyRatioIsNullWhenKmSinceLastFillIsAbsent() {
    FuelDetails details =
        new FuelDetails(
            VEHICLE_ID, FuelType.ETANOL, new BigDecimal("30"), new BigDecimal("4"), null, null);
    Transaction transaction = fuelTransaction(new BigDecimal("120.00"), details);

    FuelRatios ratios = FuelRatiosQuery.ratiosFor(transaction);

    assertThat(ratios.kmPerLiter()).isNull();
    assertThat(ratios.amountPerKm()).isNull();
    assertThat(ratios.litersPerKm()).isNull();
  }

  @Test
  void everyRatioIsNullWhenTheTransactionHasNoFuelDetails() {
    Transaction transaction = fuelTransaction(new BigDecimal("50.00"), null);

    FuelRatios ratios = FuelRatiosQuery.ratiosFor(transaction);

    assertThat(ratios).isEqualTo(FuelRatios.empty());
  }

  @Test
  void roundsToFourDecimalPlacesHalfUp() {
    FuelDetails details =
        new FuelDetails(
            VEHICLE_ID,
            FuelType.GASOLINA_ADITIVADA,
            new BigDecimal("33.333"),
            new BigDecimal("5.799"),
            new BigDecimal("333.0"),
            null);
    Transaction transaction = fuelTransaction(new BigDecimal("193.29"), details);

    FuelRatios ratios = FuelRatiosQuery.ratiosFor(transaction);

    // 333 / 33.333 = 9.99009900... -> 9.9901 half-up.
    assertThat(ratios.kmPerLiter()).isEqualByComparingTo("9.9901");
  }
}
