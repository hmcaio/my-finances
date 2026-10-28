package com.chm.myfinances.application.networth;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Issue #189, fix 3: {@link NetWorthQuery#trend} reads a constant number of statements, whatever
 * the number of sampled dates, ledger rows and snapshots (it used to run a snapshot and a holding
 * query per sampled date, and to load every ledger row as an entity).
 */
@DatabaseIntegrationTest
class NetWorthTrendStatementCountTest {

  /** A range wholly in the past, so the clock does not matter. */
  private static final LocalDate FROM = LocalDate.of(2015, 1, 1);

  private static final LocalDate TO = LocalDate.of(2015, 12, 31);
  private static final int MAX_STATEMENTS = 12;

  @Autowired private NetWorthQuery netWorthQuery;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;
  @Autowired private InvestmentProductRepository investmentProductRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentSnapshotRepository snapshotRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private Account checking;
  private Account creditCard;
  private UUID categoryId;
  private UUID paymentMethodId;
  private final List<InvestmentHolding> holdings = new ArrayList<>();

  @BeforeEach
  void setUp() {
    UUID institutionId = TestInstitutions.builtInId(institutionRepository);
    checking =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Trend checking",
                institutionId,
                AccountType.CHECKING,
                BigDecimal.TEN,
                FROM.minusDays(1)));
    creditCard =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Trend card",
                institutionId,
                AccountType.CREDIT_CARD,
                BigDecimal.ZERO,
                FROM.minusDays(1)));
    Account broker =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Trend broker",
                institutionId,
                AccountType.INVESTMENT,
                null,
                null));
    categoryId =
        TestFixtures.category(categoryRepository, "Trend Expense Test", CategoryType.EXPENSE)
            .getId();
    paymentMethodId =
        TestFixtures.paymentMethod(paymentMethodRepository, "Trend Method Test").getId();
    UUID investmentCategoryId = investmentCategoryRepository.findAll().get(0).getId();
    for (int i = 0; i < 3; i++) {
      UUID productId =
          investmentProductRepository
              .save(
                  InvestmentProduct.create(
                      UUID.randomUUID(),
                      investmentCategoryId,
                      null,
                      "Trend product " + i + " Test",
                      null))
              .getId();
      holdings.add(
          holdingRepository.save(
              InvestmentHolding.create(UUID.randomUUID(), productId, broker.getId(), null)));
    }
    entityManager.flush();
  }

  /** {@code days} distinct dates in 2015, each with a transaction, a transfer and a snapshot. */
  private void seedLedger(int firstDay, int days) {
    for (int day = firstDay; day < firstDay + days; day++) {
      jdbcTemplate.update(
          "insert into transactions (id, date, amount, category_id, type, account_id,"
              + " payment_method_id, description, created_at, last_modified_at)"
              + " values (gen_random_uuid(), ?, 12.34, ?, 'EXPENSE', ?, ?, 'Trend', now(), now())",
          FROM.plusDays(day),
          categoryId,
          checking.getId(),
          paymentMethodId);
      jdbcTemplate.update(
          "insert into transfers (id, date, from_account_id, to_account_id, amount, description,"
              + " created_at, last_modified_at)"
              + " values (gen_random_uuid(), ?, ?, ?, 5.00, 'Trend', now(), now())",
          FROM.plusDays(day),
          checking.getId(),
          creditCard.getId());
      InvestmentHolding holding = holdings.get(day % holdings.size());
      snapshotRepository.save(
          InvestmentSnapshot.create(
              UUID.randomUUID(), holding.getId(), FROM.plusDays(day), BigDecimal.valueOf(day + 1)));
    }
    entityManager.flush();
    entityManager.clear();
  }

  private long statementsOf(NetWorthGranularity granularity) {
    Statistics statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    netWorthQuery.trend(FROM, TO, granularity);
    return statistics.getPrepareStatementCount();
  }

  @Test
  void changeDateTrendReadsAConstantNumberOfStatementsWhateverTheLedgerSize() {
    seedLedger(0, 20);
    long small = statementsOf(NetWorthGranularity.CHANGE_DATE);

    seedLedger(20, 300);
    long large = statementsOf(NetWorthGranularity.CHANGE_DATE);

    assertThat(small).isLessThanOrEqualTo(MAX_STATEMENTS);
    assertThat(large).isEqualTo(small);
  }

  @Test
  void monthTrendReadsAConstantNumberOfStatementsWhateverTheLedgerSize() {
    seedLedger(0, 20);
    long small = statementsOf(NetWorthGranularity.MONTH);

    seedLedger(20, 300);
    long large = statementsOf(NetWorthGranularity.MONTH);

    assertThat(small).isLessThanOrEqualTo(MAX_STATEMENTS);
    assertThat(large).isEqualTo(small);
  }
}
