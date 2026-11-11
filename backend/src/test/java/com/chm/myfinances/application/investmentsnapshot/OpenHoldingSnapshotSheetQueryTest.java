package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OpenHoldingSnapshotSheetQuery} (F040): the rows of the snapshot sheet - every
 * open holding with its latest snapshot - ordered by account name then product name, ignoring case.
 */
class OpenHoldingSnapshotSheetQueryTest {

  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final OpenHoldingSnapshotSheetQuery query =
      new OpenHoldingSnapshotSheetQuery(
          holdingRepository,
          productRepository,
          accountRepository,
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository));

  private Account account(String name) {
    return accountRepository.save(AccountMother.investment().withName(name).build());
  }

  private InvestmentProduct product(String name, String ticker) {
    return productRepository.save(
        InvestmentProductMother.product().withName(name).withTicker(ticker).build());
  }

  private InvestmentHolding holding(InvestmentProduct product, Account account) {
    return holdingRepository.save(
        InvestmentHoldingMother.holding()
            .withProductId(product.getId())
            .withAccountId(account.getId())
            .build());
  }

  private void snapshot(InvestmentHolding holding, LocalDate date, String balance) {
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), date, new BigDecimal(balance)));
  }

  @Test
  void returnsOnlyOpenHoldingsWithTheirNamesAndTicker() {
    Account xp = account("XP");
    InvestmentProduct petr = product("Petrobras", "PETR4");
    InvestmentProduct cdb = product("CDB Banco", null);
    InvestmentHolding open = holding(petr, xp);
    InvestmentHolding closed = holding(cdb, xp);
    closed.close(LocalDate.of(2026, 4, 30));
    holdingRepository.save(closed);

    assertThat(query.openHoldings())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.holdingId()).isEqualTo(open.getId());
              assertThat(row.productId()).isEqualTo(petr.getId());
              assertThat(row.productName()).isEqualTo("Petrobras");
              assertThat(row.ticker()).isEqualTo("PETR4");
              assertThat(row.accountId()).isEqualTo(xp.getId());
              assertThat(row.accountName()).isEqualTo("XP");
            });
  }

  @Test
  void carriesTheLatestSnapshotOfEachHoldingOrNone() {
    Account xp = account("XP");
    InvestmentHolding withSnapshots = holding(product("Petrobras", "PETR4"), xp);
    InvestmentHolding without = holding(product("Vale", "VALE3"), xp);
    snapshot(withSnapshots, LocalDate.of(2026, 3, 31), "100.00");
    snapshot(withSnapshots, LocalDate.of(2026, 4, 30), "120.50");

    assertThat(query.openHoldings())
        .filteredOn(row -> row.holdingId().equals(withSnapshots.getId()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.latestSnapshot().date()).isEqualTo(LocalDate.of(2026, 4, 30));
              assertThat(row.latestSnapshot().balance()).isEqualByComparingTo("120.50");
            });
    assertThat(query.openHoldings())
        .filteredOn(row -> row.holdingId().equals(without.getId()))
        .singleElement()
        .satisfies(row -> assertThat(row.latestSnapshot()).isNull());
  }

  @Test
  void ordersByAccountNameThenProductNameIgnoringCase() {
    Account nubank = account("nubank");
    Account xp = account("XP");
    Account bradesco = account("Bradesco");
    holding(product("zeta", null), xp);
    holding(product("Alpha", null), xp);
    holding(product("beta", null), nubank);
    holding(product("Gamma", null), bradesco);

    assertThat(query.openHoldings())
        .extracting(row -> row.accountName() + "/" + row.productName())
        .containsExactly("Bradesco/Gamma", "nubank/beta", "XP/Alpha", "XP/zeta");
  }

  @Test
  void isEmptyWithoutAnyOpenHolding() {
    assertThat(query.openHoldings()).isEmpty();
  }
}
