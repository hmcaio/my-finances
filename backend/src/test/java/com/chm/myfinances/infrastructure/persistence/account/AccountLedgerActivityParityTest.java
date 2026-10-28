package com.chm.myfinances.infrastructure.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.account.AccountLedgerActivityPort;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.fakes.FakeAccountLedgerActivity;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Parity test for issue #189, fix 2: the SQL adapter of {@link AccountLedgerActivityPort} must give
 * exactly what the Java rules ({@link AccountBalanceQuery#signedContribution}/{@link
 * AccountBalanceQuery#transferContribution}, run by {@link FakeAccountLedgerActivity} over the real
 * repositories) give, on randomized data: every non-investment account type, same-day items, items
 * after the as-of date, transfers in both directions, credit card payments, a closed account, an
 * account without activity, and a trade on an {@code INVESTMENT} account.
 */
@DatabaseIntegrationTest
class AccountLedgerActivityParityTest {

  private static final LocalDate FIRST_DAY = LocalDate.of(1990, 1, 1);
  private static final int DAYS = 60;

  @Autowired private AccountLedgerActivityPort sqlActivity;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private TransferRepository transferRepository;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;
  @Autowired private InvestmentProductRepository investmentProductRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentSnapshotRepository snapshotRepository;
  @Autowired private EntityManager entityManager;

  private final Random random = new Random(189);
  private List<Account> ledgerAccounts;
  private AccountLedgerActivityPort javaActivity;

  @BeforeEach
  void seed() {
    UUID institutionId = TestInstitutions.builtInId(institutionRepository);
    List<Account> accounts = new ArrayList<>();
    AccountType[] types = {
      AccountType.CHECKING,
      AccountType.CHECKING,
      AccountType.SAVINGS,
      AccountType.CASH_WALLET,
      AccountType.CREDIT_CARD,
      AccountType.CREDIT_CARD
    };
    for (int i = 0; i < types.length; i++) {
      accounts.add(
          accountRepository.save(
              Account.create(
                  UUID.randomUUID(),
                  "Parity " + i,
                  institutionId,
                  types[i],
                  money(random.nextInt(500_000)),
                  FIRST_DAY.minusDays(1))));
    }
    Account closed =
        Account.create(
            UUID.randomUUID(),
            "Parity closed",
            institutionId,
            AccountType.SAVINGS,
            money(123_456),
            FIRST_DAY.minusDays(1));
    closed.close(FIRST_DAY.plusDays(DAYS));
    accounts.add(accountRepository.save(closed));
    Account untouched =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Parity untouched",
                institutionId,
                AccountType.CHECKING,
                money(777),
                FIRST_DAY.minusDays(1)));
    Account broker =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Parity broker",
                institutionId,
                AccountType.INVESTMENT,
                null,
                null));

    UUID expenseCategory =
        TestFixtures.category(categoryRepository, "Parity Expense Test", CategoryType.EXPENSE)
            .getId();
    UUID incomeCategory =
        TestFixtures.category(categoryRepository, "Parity Income Test", CategoryType.INCOME)
            .getId();
    UUID paymentMethodId =
        TestFixtures.paymentMethod(paymentMethodRepository, "Parity Method Test").getId();

    for (int i = 0; i < 250; i++) {
      boolean expense = random.nextBoolean();
      transactionRepository.save(
          Transaction.create(
              UUID.randomUUID(),
              FIRST_DAY.plusDays(random.nextInt(DAYS)),
              money(1 + random.nextInt(99_999)),
              expense ? expenseCategory : incomeCategory,
              expense ? CategoryType.EXPENSE : CategoryType.INCOME,
              accounts.get(random.nextInt(accounts.size())).getId(),
              paymentMethodId,
              null,
              "Parity transaction",
              null));
    }
    for (int i = 0; i < 150; i++) {
      int from = random.nextInt(accounts.size());
      int to = (from + 1 + random.nextInt(accounts.size() - 1)) % accounts.size();
      transferRepository.save(
          Transfer.create(
              UUID.randomUUID(),
              FIRST_DAY.plusDays(random.nextInt(DAYS)),
              accounts.get(from).getId(),
              accounts.get(to).getId(),
              money(1 + random.nextInt(99_999)),
              "Parity transfer",
              null));
    }
    UUID productId =
        investmentProductRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(),
                    investmentCategoryRepository.findAll().get(0).getId(),
                    null,
                    "Product Parity Test",
                    null))
            .getId();
    transferRepository.save(
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            FIRST_DAY.plusDays(10),
            accounts.get(0).getId(),
            broker.getId(),
            "Parity trade",
            null,
            BigDecimal.ONE,
            TradeConfirmation.of(
                List.of(
                    new TradeConfirmationLine(
                        productId,
                        TradeSide.BUY,
                        BigDecimal.TEN,
                        new BigDecimal("12.50"),
                        null,
                        false)))));
    entityManager.flush();
    entityManager.clear();

    accounts.add(untouched);
    ledgerAccounts = List.copyOf(accounts);
    javaActivity = new FakeAccountLedgerActivity(transactionRepository, transferRepository);
  }

  private static BigDecimal money(int cents) {
    return BigDecimal.valueOf(cents, 2);
  }

  @Test
  void sqlActivityEqualsTheJavaRulesOnEveryAsOfDate() {
    List<LocalDate> asOfDates =
        List.of(
            FIRST_DAY.minusDays(1), // before any item
            FIRST_DAY, // first day, same-day items
            FIRST_DAY.plusDays(10), // the trade's day
            FIRST_DAY.plusDays(DAYS / 2),
            FIRST_DAY.plusDays(DAYS - 1), // last day with items
            FIRST_DAY.plusDays(DAYS), // the closed account's closing day
            FIRST_DAY.plusYears(5)); // after everything

    for (LocalDate asOf : asOfDates) {
      Map<UUID, BigDecimal> expected = javaActivity.activityAsOf(ledgerAccounts, asOf);
      Map<UUID, BigDecimal> actual = sqlActivity.activityAsOf(ledgerAccounts, asOf);

      assertThat(actual).as("accounts for %s", asOf).containsOnlyKeys(expected.keySet());
      expected.forEach(
          (accountId, sum) ->
              assertThat(actual.get(accountId))
                  .as("activity of %s on %s", accountId, asOf)
                  .isEqualByComparingTo(sum));
    }
  }

  @Test
  void balancesBuiltOnTheSqlActivityAreByteIdenticalToTheJavaOnes() {
    LatestInvestmentSnapshotQuery latest =
        new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
    AccountBalanceQuery viaSql = new AccountBalanceQuery(sqlActivity, holdingRepository, latest);
    AccountBalanceQuery viaJava = new AccountBalanceQuery(javaActivity, holdingRepository, latest);
    LocalDate asOf = FIRST_DAY.plusDays(DAYS / 2);

    Map<UUID, BigDecimal> expected = viaJava.balancesAsOf(ledgerAccounts, asOf);
    Map<UUID, BigDecimal> actual = viaSql.balancesAsOf(ledgerAccounts, asOf);

    assertThat(actual).isEqualTo(expected); // BigDecimal.equals: same value and same scale
  }

  @Test
  void activityForAllAccountsIsOneStatementWhateverTheLedgerSize() {
    Statistics statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    sqlActivity.activityAsOf(ledgerAccounts, FIRST_DAY.plusYears(5));

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  void sqlDailyActivityEqualsTheJavaRulesDayByDay() {
    for (LocalDate upTo :
        List.of(
            FIRST_DAY.minusDays(1),
            FIRST_DAY,
            FIRST_DAY.plusDays(DAYS / 2),
            FIRST_DAY.plusYears(5))) {
      Map<UUID, NavigableMap<LocalDate, BigDecimal>> expected =
          javaActivity.dailyActivityUpTo(ledgerAccounts, upTo);
      Map<UUID, NavigableMap<LocalDate, BigDecimal>> actual =
          sqlActivity.dailyActivityUpTo(ledgerAccounts, upTo);

      assertThat(actual).as("accounts up to %s", upTo).containsOnlyKeys(expected.keySet());
      expected.forEach(
          (accountId, days) -> {
            assertThat(actual.get(accountId).keySet())
                .as("dates of %s up to %s", accountId, upTo)
                .isEqualTo(days.keySet());
            days.forEach(
                (day, sum) ->
                    assertThat(actual.get(accountId).get(day))
                        .as("%s on %s", accountId, day)
                        .isEqualByComparingTo(sum));
          });
    }
  }

  @Test
  void theDailyActivityAddsUpToTheActivityAsOfEveryDate() {
    LocalDate upTo = FIRST_DAY.plusDays(DAYS);
    Map<UUID, NavigableMap<LocalDate, BigDecimal>> daily =
        sqlActivity.dailyActivityUpTo(ledgerAccounts, upTo);

    for (LocalDate asOf : List.of(FIRST_DAY, FIRST_DAY.plusDays(17), upTo)) {
      Map<UUID, BigDecimal> asOfSums = sqlActivity.activityAsOf(ledgerAccounts, asOf);
      daily.forEach(
          (accountId, days) ->
              assertThat(
                      days.headMap(asOf, true).values().stream()
                          .reduce(BigDecimal.ZERO, BigDecimal::add))
                  .as("%s as of %s", accountId, asOf)
                  .isEqualByComparingTo(asOfSums.get(accountId)));
    }
  }

  @Test
  void dailyActivityForAllAccountsIsOneStatement() {
    Statistics statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    sqlActivity.dailyActivityUpTo(ledgerAccounts, FIRST_DAY.plusYears(5));

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }
}
