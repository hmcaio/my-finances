package com.chm.myfinances.application.report;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Report 6 (F030 spec): every holding's latest snapshot as of a selectable date, plus the
 * trade-confirmation buy/sell lines booked within a date range, plus (F038) the opening positions
 * whose as-of date falls within it. Splits (F028/F029) are excluded - nothing to report on until
 * that feature ships.
 */
@Service
public class InvestmentsReportGenerator implements ReportGenerator {

  private static final List<String> SNAPSHOT_COLUMNS =
      List.of("product_name", "account_name", "balance", "snapshot_date");
  private static final List<String> ACTIVITY_COLUMNS =
      List.of(
          "date",
          "product_name",
          "side",
          "quantity",
          "unit_price",
          "resulting_balance",
          "from_account_name",
          "to_account_name");
  private static final List<String> OPENING_POSITION_COLUMNS =
      List.of(
          "product_name", "account_name", "as_of_date", "quantity", "unit_price", "current_value");

  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final TransferTradeLineRepository tradeLineRepository;
  private final InvestmentOpeningPositionRepository openingPositionRepository;
  private final Clock clock;

  public InvestmentsReportGenerator(
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      InvestmentHoldingRepository holdingRepository,
      InvestmentProductRepository productRepository,
      AccountRepository accountRepository,
      TransferTradeLineRepository tradeLineRepository,
      InvestmentOpeningPositionRepository openingPositionRepository,
      Clock clock) {
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.holdingRepository = holdingRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.tradeLineRepository = tradeLineRepository;
    this.openingPositionRepository = openingPositionRepository;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.INVESTMENTS;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    LocalDate asOf = filter.asOfDate() != null ? filter.asOfDate() : LocalDate.now(clock);
    Map<UUID, String> productNames = new HashMap<>();
    for (InvestmentProduct product : productRepository.findAll()) {
      productNames.put(product.getId(), product.getName());
    }
    Map<UUID, String> accountNames = new HashMap<>();
    for (Account account : accountRepository.findAll()) {
      accountNames.put(account.getId(), account.getName());
    }

    Map<UUID, InvestmentSnapshot> latestByHolding = latestSnapshotQuery.latestByHolding(asOf);
    List<List<Object>> snapshotRows = new ArrayList<>();
    for (InvestmentHolding holding : holdingRepository.findAll()) {
      InvestmentSnapshot snapshot = latestByHolding.get(holding.getId());
      if (snapshot == null) {
        continue;
      }
      snapshotRows.add(
          Arrays.asList(
              productNames.getOrDefault(holding.getProductId(), ""),
              accountNames.getOrDefault(holding.getAccountId(), ""),
              snapshot.getBalance(),
              snapshot.getDate()));
    }

    List<List<Object>> activityRows = new ArrayList<>();
    for (TransferTradeLine line : tradeLineRepository.findAll()) {
      if (filter.dateFrom() != null && line.date().isBefore(filter.dateFrom())) {
        continue;
      }
      if (filter.dateTo() != null && line.date().isAfter(filter.dateTo())) {
        continue;
      }
      activityRows.add(
          Arrays.asList(
              line.date(),
              productNames.getOrDefault(line.productId(), ""),
              line.side().name(),
              line.quantity(),
              line.unitPrice(),
              line.resultingBalance(),
              accountNames.getOrDefault(line.fromAccountId(), ""),
              accountNames.getOrDefault(line.toAccountId(), "")));
    }

    List<List<Object>> openingPositionRows =
        openingPositionRows(filter, productNames, accountNames);

    byte[] csv =
        CsvSectionWriter.write(
            List.of(
                new CsvSectionWriter.Section(
                    "HOLDINGS (LATEST SNAPSHOT)", SNAPSHOT_COLUMNS, snapshotRows),
                new CsvSectionWriter.Section("TRADE ACTIVITY", ACTIVITY_COLUMNS, activityRows),
                new CsvSectionWriter.Section(
                    "OPENING POSITIONS", OPENING_POSITION_COLUMNS, openingPositionRows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }

  /**
   * F038 (ADR 0033): one row per opening position whose {@code as_of_date} is inside the {@code
   * dateFrom}/{@code dateTo} range. A position stores no value, so {@code current_value} is the
   * holding's snapshot dated exactly {@code as_of_date} - the one the same step wrote, if it is
   * still there - and empty otherwise (hence {@code Arrays.asList}, {@code
   * docs/conventions/csv-output.md}).
   */
  private List<List<Object>> openingPositionRows(
      ReportFilter filter, Map<UUID, String> productNames, Map<UUID, String> accountNames) {
    Map<UUID, InvestmentHolding> holdingsById = new HashMap<>();
    for (InvestmentHolding holding : holdingRepository.findAll()) {
      holdingsById.put(holding.getId(), holding);
    }
    List<InvestmentOpeningPosition> positions =
        openingPositionRepository.findAll().stream()
            .filter(p -> filter.dateFrom() == null || !p.getAsOfDate().isBefore(filter.dateFrom()))
            .filter(p -> filter.dateTo() == null || !p.getAsOfDate().isAfter(filter.dateTo()))
            .sorted(
                Comparator.comparing(InvestmentOpeningPosition::getAsOfDate)
                    .thenComparing(InvestmentOpeningPosition::getHoldingId))
            .toList();
    List<List<Object>> rows = new ArrayList<>();
    for (InvestmentOpeningPosition position : positions) {
      InvestmentHolding holding = holdingsById.get(position.getHoldingId());
      BigDecimal currentValue =
          latestSnapshotQuery
              .latestOf(position.getHoldingId(), position.getAsOfDate())
              .filter(snapshot -> snapshot.getDate().equals(position.getAsOfDate()))
              .map(InvestmentSnapshot::getBalance)
              .orElse(null);
      rows.add(
          Arrays.asList(
              productNames.getOrDefault(holding.getProductId(), ""),
              accountNames.getOrDefault(holding.getAccountId(), ""),
              position.getAsOfDate(),
              position.getQuantity(),
              position.getUnitPrice(),
              currentValue));
    }
    return rows;
  }
}
