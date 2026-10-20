package com.chm.myfinances.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link FuelDetails} (F024 spec, ADR 0021): {@code vehicleId}, {@code
 * fuelType}, {@code liters} and {@code pricePerLiter} are always required together (unlike {@code
 * InvestmentTradeDetails}, there is no "empty" instance - absence is a {@code null} {@code
 * FuelDetails} on {@code Transaction} itself), {@code liters}/{@code pricePerLiter} positive, and
 * {@code kmSinceLastFill}/{@code odometer} optional but positive when present.
 */
class FuelDetailsTest {

  private static final UUID VEHICLE_ID = UUID.randomUUID();

  @Test
  void keepsGivenFields() {
    FuelDetails details =
        new FuelDetails(
            VEHICLE_ID,
            FuelType.GASOLINA,
            new BigDecimal("40.500"),
            new BigDecimal("5.799"),
            new BigDecimal("350.0"),
            new BigDecimal("12345.0"));

    assertThat(details.vehicleId()).isEqualTo(VEHICLE_ID);
    assertThat(details.fuelType()).isEqualTo(FuelType.GASOLINA);
    assertThat(details.liters()).isEqualByComparingTo("40.500");
    assertThat(details.pricePerLiter()).isEqualByComparingTo("5.799");
    assertThat(details.kmSinceLastFill()).isEqualByComparingTo("350.0");
    assertThat(details.odometer()).isEqualByComparingTo("12345.0");
  }

  @Test
  void allowsNullKmSinceLastFillAndOdometer() {
    FuelDetails details =
        new FuelDetails(
            VEHICLE_ID, FuelType.ETANOL, new BigDecimal("30"), new BigDecimal("4.50"), null, null);

    assertThat(details.kmSinceLastFill()).isNull();
    assertThat(details.odometer()).isNull();
  }

  @Test
  void rejectsNullVehicleId() {
    assertThatThrownBy(
            () ->
                new FuelDetails(null, FuelType.ETANOL, BigDecimal.TEN, BigDecimal.ONE, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullFuelType() {
    assertThatThrownBy(
            () -> new FuelDetails(VEHICLE_ID, null, BigDecimal.TEN, BigDecimal.ONE, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullLiters() {
    assertThatThrownBy(
            () -> new FuelDetails(VEHICLE_ID, FuelType.ETANOL, null, BigDecimal.ONE, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullPricePerLiter() {
    assertThatThrownBy(
            () -> new FuelDetails(VEHICLE_ID, FuelType.ETANOL, BigDecimal.TEN, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNonPositiveLiters() {
    assertThatThrownBy(
            () ->
                new FuelDetails(
                    VEHICLE_ID, FuelType.ETANOL, BigDecimal.ZERO, BigDecimal.ONE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new FuelDetails(
                    VEHICLE_ID, FuelType.ETANOL, new BigDecimal("-1"), BigDecimal.ONE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositivePricePerLiter() {
    assertThatThrownBy(
            () ->
                new FuelDetails(
                    VEHICLE_ID, FuelType.ETANOL, BigDecimal.ONE, BigDecimal.ZERO, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new FuelDetails(
                    VEHICLE_ID, FuelType.ETANOL, BigDecimal.ONE, new BigDecimal("-1"), null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveKmSinceLastFillWhenPresent() {
    assertThatThrownBy(
            () ->
                new FuelDetails(
                    VEHICLE_ID,
                    FuelType.ETANOL,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ZERO,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveOdometerWhenPresent() {
    assertThatThrownBy(
            () ->
                new FuelDetails(
                    VEHICLE_ID,
                    FuelType.ETANOL,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    null,
                    BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
