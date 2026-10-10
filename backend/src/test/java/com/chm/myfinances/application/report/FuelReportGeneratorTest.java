package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeVehicleRepository;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FuelReportGenerator} (F030 spec), written first (ADR 0004): per-row ratios
 * carried through from {@code FuelRatiosQuery}, and the per-vehicle summary block's total
 * spent/liters/average km/L math, computed only at report time.
 */
class FuelReportGeneratorTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeVehicleRepository vehicleRepository = new FakeVehicleRepository();
  private final FuelReportGenerator generator =
      new FuelReportGenerator(transactionRepository, vehicleRepository);

  private UUID vehicle(String name) {
    return vehicleRepository.save(Vehicle.create(UUID.randomUUID(), name)).getId();
  }

  private void fillUp(
      UUID vehicleId, LocalDate date, String amount, String liters, String kmSinceLastFill) {
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(date)
            .withAmount(new BigDecimal(amount))
            .withFuelDetails(
                new FuelDetails(
                    vehicleId,
                    FuelType.GASOLINA,
                    new BigDecimal(liters),
                    new BigDecimal(amount).divide(new BigDecimal(liters), 3, RoundingMode.HALF_UP),
                    kmSinceLastFill == null ? null : new BigDecimal(kmSinceLastFill),
                    null))
            .build());
  }

  @Test
  void computesPerRowRatiosAndAPerVehicleSummaryOfTotalsAndAverageKmPerLiter() {
    UUID civic = vehicle("Civic R Test");
    // 400 km on 40L -> 10 km/L; 500 km on 50L -> 10 km/L. Average km/L = 10.
    fillUp(civic, LocalDate.of(2026, 3, 1), "200.00", "40.000", "400");
    fillUp(civic, LocalDate.of(2026, 3, 15), "250.00", "50.000", "500");

    List<GeneratedFile> files = generator.generate(ReportFilter.none());
    List<List<Map<String, String>>> sections =
        CsvTestSupport.parseSections(files.get(0).content(), 2);
    List<Map<String, String>> detail = sections.get(0);
    List<Map<String, String>> summary = sections.get(1);

    assertThat(detail).hasSize(2);
    assertThat(detail.get(0).get("km_per_liter")).isEqualTo("10.0000");
    assertThat(summary).hasSize(1);
    assertThat(summary.get(0).get("vehicle_name")).isEqualTo("Civic R Test");
    assertThat(summary.get(0).get("total_spent")).isEqualTo("450.00");
    assertThat(summary.get(0).get("total_liters")).isEqualTo("90.000");
    assertThat(summary.get(0).get("average_km_per_liter")).isEqualTo("10.0000");
  }

  @Test
  void aFirstFillWithNoKmSinceLastFillIsExcludedFromTheAverage() {
    UUID civic = vehicle("Civic R Test 2");
    fillUp(civic, LocalDate.of(2026, 3, 1), "200.00", "40.000", null);
    fillUp(civic, LocalDate.of(2026, 3, 15), "200.00", "40.000", "400");

    List<GeneratedFile> files = generator.generate(ReportFilter.none());
    List<List<Map<String, String>>> sections =
        CsvTestSupport.parseSections(files.get(0).content(), 2);

    assertThat(sections.get(0).get(0).get("km_per_liter")).isEqualTo("");
    // Only the second fill (10 km/L) contributes to the average.
    assertThat(sections.get(1).get(0).get("average_km_per_liter")).isEqualTo("10.0000");
  }

  @Test
  void vehicleAndDateFiltersNarrowTheFillUpsConsidered() {
    UUID civic = vehicle("Civic R Test 3");
    UUID corolla = vehicle("Corolla R Test 3");
    fillUp(civic, LocalDate.of(2026, 1, 1), "100.00", "20.000", "200");
    fillUp(civic, LocalDate.of(2026, 5, 1), "100.00", "20.000", "200");
    fillUp(corolla, LocalDate.of(2026, 3, 1), "100.00", "20.000", "200");

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 6, 1),
                null,
                null,
                null,
                civic,
                null,
                null));
    List<List<Map<String, String>>> sections =
        CsvTestSupport.parseSections(files.get(0).content(), 2);

    assertThat(sections.get(0)).hasSize(1);
    assertThat(sections.get(0).get(0).get("date")).isEqualTo("2026-05-01");
  }
}
