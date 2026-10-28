package com.chm.myfinances.application.networth;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountLedgerActivity;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Issue #189, fix 3: {@link NetWorthQuery#trend} walks the ledger and the snapshots once, while
 * {@link NetWorthQuery#asOf} computes each date on its own through {@link AccountBalanceQuery}.
 * They must agree on every point, on randomized data: both granularities, same-day items, several
 * holdings of one investment account, a holding with a late first snapshot, a closed account and an
 * account opened mid-range.
 */
class NetWorthQueryTrendEquivalenceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);
  private static final LocalDate START = LocalDate.of(2026, 1, 1);

  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeAccountLedgerActivity ledgerActivity =
      new FakeAccountLedgerActivity(transactionRepository, transferRepository);
  private final NetWorthQuery query =
      new NetWorthQuery(
          accountRepository,
          new AccountBalanceQuery(
              ledgerActivity,
              holdingRepository,
              new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository)),
          ledgerActivity,
          transactionRepository,
          transferRepository,
          holdingRepository,
          snapshotRepository,
          TestClocks.fixedAtStartOf(TODAY));

  private final Random random = new Random(189);

  private LocalDate randomDay() {
    return START.plusDays(random.nextInt(300));
  }

  private BigDecimal money(int maxCents) {
    return BigDecimal.valueOf(1 + random.nextInt(maxCents), 2);
  }

  @Test
  void everyTrendPointEqualsTheNetWorthComputedOnItsOwn() {
    List<Account> ledger = new ArrayList<>();
    ledger.add(save(AccountMother.checking().withName("A").withOpeningBalance(money(900_000))));
    ledger.add(save(AccountMother.checking().withName("B").withOpeningBalance(money(900_000))));
    ledger.add(save(AccountMother.savings().withName("C").withOpeningBalance(money(900_000))));
    ledger.add(save(AccountMother.creditCard().withName("D").withOpeningBalance(money(900_000))));
    Account closed =
        save(AccountMother.cashWallet().withName("Closed").withOpeningBalance(money(5_000)));
    closed.close(START.plusDays(150));
    ledger.add(closed);
    ledger.add(
        save(
            AccountMother.savings()
                .withName("Opened late")
                .withOpeningBalance(money(5_000))
                .withOpeningBalanceDate(START.plusDays(120))));
    Account broker = save(AccountMother.investment().withName("Broker"));
    Account otherBroker = save(AccountMother.investment().withName("Other broker"));

    for (int i = 0; i < 120; i++) {
      boolean expense = random.nextBoolean();
      transactionRepository.save(
          TransactionMother.expense()
              .withAccountId(ledger.get(random.nextInt(ledger.size())).getId())
              .withType(expense ? CategoryType.EXPENSE : CategoryType.INCOME)
              .withAmount(money(99_999))
              .withDate(randomDay())
              .build());
    }
    for (int i = 0; i < 80; i++) {
      int from = random.nextInt(ledger.size());
      int to = (from + 1 + random.nextInt(ledger.size() - 1)) % ledger.size();
      transferRepository.save(
          TransferMother.transfer()
              .withFromAccountId(ledger.get(from).getId())
              .withToAccountId(ledger.get(to).getId())
              .withAmount(money(99_999))
              .withDate(randomDay())
              .build());
    }
    List<InvestmentHolding> holdings = new ArrayList<>();
    for (Account account : List.of(broker, broker, broker, otherBroker)) {
      holdings.add(
          holdingRepository.save(
              InvestmentHoldingMother.holding().withAccountId(account.getId()).build()));
    }
    for (int i = 0; i < 60; i++) {
      InvestmentHolding holding = holdings.get(random.nextInt(holdings.size() - 1));
      LocalDate day = randomDay();
      if (snapshotRepository.findByHoldingIdAndDate(holding.getId(), day).isEmpty()) {
        snapshotRepository.save(
            InvestmentSnapshot.create(UUID.randomUUID(), holding.getId(), day, money(900_000)));
      }
    }
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holdings.get(3).getId(), START.plusDays(280), money(1_000)));

    for (NetWorthGranularity granularity : NetWorthGranularity.values()) {
      List<NetWorthPoint> points = query.trend(START, TODAY, granularity);

      assertThat(points).isNotEmpty();
      for (NetWorthPoint point : points) {
        assertThat(point)
            .as("%s point on %s", granularity, point.date())
            .isEqualTo(query.asOf(point.date()));
      }
    }
  }

  private Account save(AccountMother mother) {
    return accountRepository.save(mother.build());
  }
}
