package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.StatementCount;
import com.chm.myfinances.testsupport.mothers.GoalMother;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins that the goal list and the dashboard (F039) do not run a statement per goal
 * (CODING_STANDARDS "Persistence reads"): the count with a few goals equals the count with many,
 * each goal allocating a different product.
 */
@DatabaseIntegrationTest
class GoalQueriesStatementCountTest {

  @Autowired private GoalListQuery listQuery;
  @Autowired private GoalDashboardQuery dashboardQuery;
  @Autowired private GoalRepository goalRepository;
  @Autowired private GoalAllocationRepository allocationRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentSnapshotRepository snapshotRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private UUID categoryId;
  private UUID brokerId;
  private int sequence;

  private void seedGoals(int count) {
    if (categoryId == null) {
      categoryId = TaxonomySeeder.investmentCategory(jdbcTemplate, "Category Goal Count").getId();
      brokerId =
          TestFixtures.account(
                  accountRepository,
                  institutionRepository,
                  "Broker Goal Count",
                  AccountType.INVESTMENT)
              .getId();
    }
    for (int i = 0; i < count; i++) {
      sequence++;
      UUID productId =
          productRepository
              .save(
                  InvestmentProduct.create(
                      UUID.randomUUID(), categoryId, null, "Product Goal Count " + sequence, null))
              .getId();
      UUID holdingId =
          holdingRepository
              .save(
                  InvestmentHolding.create(
                      UUID.randomUUID(),
                      productId,
                      TestFixtures.account(
                              accountRepository,
                              institutionRepository,
                              "Broker Goal Count " + sequence,
                              AccountType.INVESTMENT)
                          .getId(),
                      null))
              .getId();
      snapshotRepository.save(
          InvestmentSnapshot.create(
              UUID.randomUUID(), holdingId, LocalDate.now().minusDays(1), new BigDecimal("1000")));
      Goal goal = goalRepository.save(GoalMother.goal().withName("Goal Count " + sequence).build());
      allocationRepository.replaceForGoal(
          goal.getId(),
          List.of(
              GoalAllocation.create(
                  UUID.randomUUID(), goal.getId(), productId, new BigDecimal("50"))));
    }
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void theListAndTheDashboardRunTheSameStatementsForFewAndManyGoals() {
    seedGoals(2);
    long listFew = StatementCount.of(entityManager, () -> listQuery.list(false));
    long dashboardFew = StatementCount.of(entityManager, dashboardQuery::dashboard);

    seedGoals(15);
    long listMany = StatementCount.of(entityManager, () -> listQuery.list(false));
    long dashboardMany = StatementCount.of(entityManager, dashboardQuery::dashboard);

    assertThat(listMany).isEqualTo(listFew).isLessThanOrEqualTo(8);
    assertThat(dashboardMany).isEqualTo(dashboardFew).isLessThanOrEqualTo(8);
    assertThat(listQuery.list(false)).hasSizeGreaterThanOrEqualTo(17);
  }
}
