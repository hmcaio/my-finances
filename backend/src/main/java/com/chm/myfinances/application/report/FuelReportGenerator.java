package com.chm.myfinances.application.report;

import com.chm.myfinances.application.transaction.FuelRatios;
import com.chm.myfinances.application.transaction.FuelRatiosQuery;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Report 4 (F030 spec): every fuel fill-up in a date range with its per-row ratios (F024's {@link
 * FuelRatiosQuery}), plus one per-vehicle summary block (total spent, total liters, average km/L)
 * computed here at report time - no new persistence, the same "computed on read" style as the
 * ratios themselves.
 */
@Service
public class FuelReportGenerator implements ReportGenerator {

  private static final List<String> DETAIL_COLUMNS =
      List.of(
          "date",
          "vehicle_name",
          "fuel_type",
          "liters",
          "price_per_liter",
          "amount",
          "km_since_last_fill",
          "odometer",
          "km_per_liter",
          "amount_per_km",
          "liters_per_km");
  private static final List<String> SUMMARY_COLUMNS =
      List.of("vehicle_name", "total_spent", "total_liters", "average_km_per_liter");
  private static final int AVERAGE_SCALE = 4;

  private final TransactionRepository transactionRepository;
  private final VehicleRepository vehicleRepository;

  public FuelReportGenerator(
      TransactionRepository transactionRepository, VehicleRepository vehicleRepository) {
    this.transactionRepository = transactionRepository;
    this.vehicleRepository = vehicleRepository;
  }

  @Override
  public ReportType type() {
    return ReportType.FUEL_HISTORY;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    Map<UUID, String> vehicleNames = new LinkedHashMap<>();
    for (Vehicle vehicle : vehicleRepository.findAll()) {
      vehicleNames.put(vehicle.getId(), vehicle.getName());
    }

    List<Transaction> fuelTransactions =
        transactionRepository
            .findAll(
                new TransactionFilter(filter.dateFrom(), filter.dateTo(), null, null, null),
                Pageable.unpaged())
            .getContent()
            .stream()
            .filter(t -> t.getFuelDetails() != null)
            .filter(
                t ->
                    filter.vehicleId() == null
                        || filter.vehicleId().equals(t.getFuelDetails().vehicleId()))
            .sorted(Comparator.comparing(Transaction::getDate))
            .toList();

    List<List<Object>> detailRows = new ArrayList<>();
    Map<UUID, VehicleTotals> totalsByVehicle = new LinkedHashMap<>();
    for (Transaction transaction : fuelTransactions) {
      FuelDetails details = transaction.getFuelDetails();
      FuelRatios ratios = FuelRatiosQuery.ratiosFor(transaction);
      detailRows.add(
          Arrays.asList(
              transaction.getDate(),
              vehicleNames.getOrDefault(details.vehicleId(), ""),
              details.fuelType().name(),
              details.liters(),
              details.pricePerLiter(),
              transaction.getAmount(),
              details.kmSinceLastFill(),
              details.odometer(),
              ratios.kmPerLiter(),
              ratios.amountPerKm(),
              ratios.litersPerKm()));

      VehicleTotals totals =
          totalsByVehicle.computeIfAbsent(details.vehicleId(), k -> new VehicleTotals());
      totals.totalSpent = totals.totalSpent.add(transaction.getAmount());
      totals.totalLiters = totals.totalLiters.add(details.liters());
      if (ratios.kmPerLiter() != null) {
        totals.kmPerLiterSum = totals.kmPerLiterSum.add(ratios.kmPerLiter());
        totals.kmPerLiterCount++;
      }
    }

    List<List<Object>> summaryRows = new ArrayList<>();
    for (Map.Entry<UUID, VehicleTotals> entry : totalsByVehicle.entrySet()) {
      VehicleTotals totals = entry.getValue();
      BigDecimal averageKmPerLiter =
          totals.kmPerLiterCount == 0
              ? null
              : totals.kmPerLiterSum.divide(
                  BigDecimal.valueOf(totals.kmPerLiterCount), AVERAGE_SCALE, RoundingMode.HALF_UP);
      summaryRows.add(
          Arrays.asList(
              vehicleNames.getOrDefault(entry.getKey(), ""),
              totals.totalSpent,
              totals.totalLiters,
              averageKmPerLiter));
    }

    byte[] csv =
        CsvSectionWriter.write(
            List.of(
                new CsvSectionWriter.Section("FUEL HISTORY", DETAIL_COLUMNS, detailRows),
                new CsvSectionWriter.Section("VEHICLE SUMMARY", SUMMARY_COLUMNS, summaryRows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }

  private static final class VehicleTotals {
    private BigDecimal totalSpent = BigDecimal.ZERO.setScale(2);
    private BigDecimal totalLiters = BigDecimal.ZERO.setScale(3);
    private BigDecimal kmPerLiterSum = BigDecimal.ZERO.setScale(AVERAGE_SCALE);
    private int kmPerLiterCount;
  }
}
