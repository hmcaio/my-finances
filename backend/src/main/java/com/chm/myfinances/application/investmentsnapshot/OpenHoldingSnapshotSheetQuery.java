package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The rows of the snapshot sheet (F040, PRD S6.6): every open holding with the product and account
 * names the sheet shows and its most recent snapshot (or none), ordered by account name then
 * product name, ignoring case. Read-only and computed: the latest snapshots come from {@link
 * LatestInvestmentSnapshotQuery#latestByHolding()} and the names from one read of products and one
 * of accounts, so the cost does not grow per row. Closed holdings are left out - the close guard
 * keeps their latest snapshot at zero (PRD S5.8).
 */
@Service
public class OpenHoldingSnapshotSheetQuery {

  private static final Comparator<OpenHoldingSnapshotRow> BY_ACCOUNT_THEN_PRODUCT =
      Comparator.comparing(OpenHoldingSnapshotRow::accountName, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(OpenHoldingSnapshotRow::productName, String.CASE_INSENSITIVE_ORDER);

  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;

  public OpenHoldingSnapshotSheetQuery(
      InvestmentHoldingRepository holdingRepository,
      InvestmentProductRepository productRepository,
      AccountRepository accountRepository,
      LatestInvestmentSnapshotQuery latestSnapshotQuery) {
    this.holdingRepository = holdingRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.latestSnapshotQuery = latestSnapshotQuery;
  }

  public List<OpenHoldingSnapshotRow> openHoldings() {
    Map<UUID, InvestmentProduct> products =
        productRepository.findAll().stream()
            .collect(Collectors.toMap(InvestmentProduct::getId, Function.identity()));
    Map<UUID, Account> accounts =
        accountRepository.findAll().stream()
            .collect(Collectors.toMap(Account::getId, Function.identity()));
    Map<UUID, InvestmentSnapshot> latest = latestSnapshotQuery.latestByHolding();
    return holdingRepository.findAll().stream()
        .filter(holding -> !holding.isClosed())
        .map(holding -> toRow(holding, products, accounts, latest))
        .sorted(BY_ACCOUNT_THEN_PRODUCT)
        .toList();
  }

  private static OpenHoldingSnapshotRow toRow(
      InvestmentHolding holding,
      Map<UUID, InvestmentProduct> products,
      Map<UUID, Account> accounts,
      Map<UUID, InvestmentSnapshot> latest) {
    InvestmentProduct product = products.get(holding.getProductId());
    Account account = accounts.get(holding.getAccountId());
    InvestmentSnapshot snapshot = latest.get(holding.getId());
    return new OpenHoldingSnapshotRow(
        holding.getId(),
        product.getId(),
        product.getName(),
        product.getTicker(),
        account.getId(),
        account.getName(),
        snapshot == null
            ? null
            : new OpenHoldingSnapshotRow.LatestSnapshot(snapshot.getDate(), snapshot.getBalance()));
  }
}
