package com.chm.myfinances.application.report;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Report 6 (F030 spec): every holding's latest snapshot as of a selectable date, plus the
 * trade-confirmation buy/sell lines booked within a date range. Splits (F028/F029) are excluded -
 * nothing to report on until that feature ships.
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

  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final TransferTradeLineRepository tradeLineRepository;
  private final Clock clock;

  public InvestmentsReportGenerator(
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      InvestmentHoldingRepository holdingRepository,
      InvestmentProductRepository productRepository,
      AccountRepository accountRepository,
      TransferTradeLineRepository tradeLineRepository,
      Clock clock) {
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.holdingRepository = holdingRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.tradeLineRepository = tradeLineRepository;
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

    byte[] csv =
        CsvSectionWriter.write(
            List.of(
                new CsvSectionWriter.Section(
                    "HOLDINGS (LATEST SNAPSHOT)", SNAPSHOT_COLUMNS, snapshotRows),
                new CsvSectionWriter.Section("TRADE ACTIVITY", ACTIVITY_COLUMNS, activityRows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }
}
