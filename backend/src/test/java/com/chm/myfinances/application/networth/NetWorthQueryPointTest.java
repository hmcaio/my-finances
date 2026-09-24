package com.chm.myfinances.application.networth;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NetWorthQuery#asOf} (F010 spec, PRD S5.9), written first (ADR 0004): the formula
 * {@code assets + investments - liabilities}, the three parts, and the as-of-aware account filter
 * (opened on or before the date, closed after it).
 */
class NetWorthQueryPointTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final NetWorthQuery query =
      new NetWorthQuery(
          accountRepository,
          new AccountBalanceQuery(
              transactionRepository,
              transferRepository,
              productRepository,
              new LatestInvestmentSnapshotQuery(snapshotRepository)),
          transactionRepository,
          transferRepository,
          snapshotRepository,
          Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

  private Account save(AccountMother mother) {
    return accountRepository.save(mother.build());
  }

  private void snapshot(Account investment, LocalDate date, String balance) {
    InvestmentProduct product =
        productRepository.save(
            InvestmentProductMother.product()
                .withAccountId(investment.getId())
                .withName("Product " + UUID.randomUUID())
                .build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), product.getId(), date, new BigDecimal(balance)));
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  @Test
  void matchesTheFormula_assetsAndInvestmentsAddCreditCardsSubtract() {
    LocalDate opened = LocalDate.of(2026, 1, 1);
    save(AccountMother.checking().withOpeningBalance(bd("1000.00")).withOpeningBalanceDate(opened));
    save(AccountMother.savings().withOpeningBalance(bd("500.00")).withOpeningBalanceDate(opened));
    save(AccountMother.cashWallet().withOpeningBalance(bd("50.00")).withOpeningBalanceDate(opened));
    save(
        AccountMother.creditCard().withOpeningBalance(bd("200.00")).withOpeningBalanceDate(opened));
    Account broker = save(AccountMother.investment());
    snapshot(broker, LocalDate.of(2026, 2, 1), "3000.00");

    NetWorthPoint point = query.asOf(LocalDate.of(2026, 3, 1));

    assertThat(point.date()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(point.assets()).isEqualByComparingTo("1550.00");
    assertThat(point.liabilities()).isEqualByComparingTo("200.00");
    assertThat(point.investments()).isEqualByComparingTo("3000.00");
    assertThat(point.netWorth()).isEqualByComparingTo("4350.00");
  }

  @Test
  void balancesAreAsOfTheDate_transactionsAfterItAreIgnored() {
    Account checking =
        save(
            AccountMother.checking()
                .withOpeningBalance(bd("1000.00"))
                .withOpeningBalanceDate(LocalDate.of(2026, 1, 1)));
    transactionRepository.save(
        TransactionMother.expense()
            .withAccountId(checking.getId())
            .withType(CategoryType.EXPENSE)
            .withAmount(bd("100.00"))
            .withDate(LocalDate.of(2026, 3, 10))
            .build());

    assertThat(query.asOf(LocalDate.of(2026, 3, 9)).netWorth()).isEqualByComparingTo("1000.00");
    assertThat(query.asOf(LocalDate.of(2026, 3, 10)).netWorth()).isEqualByComparingTo("900.00");
  }

  @Test
  void creditCardSpendLowersNetWorth() {
    Account card =
        save(
            AccountMother.creditCard()
                .withOpeningBalance(bd("0.00"))
                .withOpeningBalanceDate(LocalDate.of(2026, 1, 1)));
    transactionRepository.save(
        TransactionMother.expense()
            .withAccountId(card.getId())
            .withType(CategoryType.EXPENSE)
            .withAmount(bd("80.00"))
            .withDate(LocalDate.of(2026, 2, 1))
            .build());

    NetWorthPoint point = query.asOf(LocalDate.of(2026, 2, 1));

    assertThat(point.liabilities()).isEqualByComparingTo("80.00");
    assertThat(point.netWorth()).isEqualByComparingTo("-80.00");
  }

  @Test
  void anAccountClosedAfterTheDateStillCounts_andStopsCountingFromItsClosedDate() {
    Account savings =
        save(
            AccountMother.savings()
                .withOpeningBalance(bd("700.00"))
                .withOpeningBalanceDate(LocalDate.of(2026, 1, 1)));
    savings.close(LocalDate.of(2026, 4, 1));
    accountRepository.save(savings);

    assertThat(query.asOf(LocalDate.of(2026, 3, 31)).netWorth()).isEqualByComparingTo("700.00");
    assertThat(query.asOf(LocalDate.of(2026, 4, 1)).netWorth()).isEqualByComparingTo("0.00");
    assertThat(query.asOf(LocalDate.of(2026, 5, 1)).netWorth()).isEqualByComparingTo("0.00");
  }

  @Test
  void anAccountOpenedAfterTheDateDoesNotCount() {
    save(
        AccountMother.checking()
            .withOpeningBalance(bd("900.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 3, 1)));

    assertThat(query.asOf(LocalDate.of(2026, 2, 28)).netWorth()).isEqualByComparingTo("0.00");
    assertThat(query.asOf(LocalDate.of(2026, 3, 1)).netWorth()).isEqualByComparingTo("900.00");
  }

  @Test
  void anInvestmentAccountContributesZeroBeforeItsFirstSnapshot() {
    Account broker = save(AccountMother.investment());
    snapshot(broker, LocalDate.of(2026, 3, 1), "1200.00");

    assertThat(query.asOf(LocalDate.of(2026, 2, 28)).investments()).isEqualByComparingTo("0.00");
    assertThat(query.asOf(LocalDate.of(2026, 3, 1)).investments()).isEqualByComparingTo("1200.00");
  }

  @Test
  void aClosedInvestmentAccountStopsCountingFromItsClosedDate() {
    Account broker = save(AccountMother.investment());
    snapshot(broker, LocalDate.of(2026, 1, 1), "1200.00");
    broker.close(LocalDate.of(2026, 5, 1));
    accountRepository.save(broker);

    assertThat(query.asOf(LocalDate.of(2026, 4, 30)).investments()).isEqualByComparingTo("1200.00");
    assertThat(query.asOf(LocalDate.of(2026, 5, 1)).investments()).isEqualByComparingTo("0.00");
  }

  @Test
  void noAccountsIsZeroAcrossTheBoard() {
    NetWorthPoint point = query.asOf(TODAY);

    assertThat(point.netWorth()).isEqualByComparingTo("0.00");
    assertThat(point.assets()).isEqualByComparingTo("0.00");
    assertThat(point.liabilities()).isEqualByComparingTo("0.00");
    assertThat(point.investments()).isEqualByComparingTo("0.00");
  }
}
