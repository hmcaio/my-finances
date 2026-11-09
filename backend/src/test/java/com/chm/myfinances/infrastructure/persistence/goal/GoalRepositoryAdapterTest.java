package com.chm.myfinances.infrastructure.persistence.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.StatementCount;
import com.chm.myfinances.testsupport.mothers.GoalMother;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence-layer integration test for {@link GoalRepositoryAdapter} and {@link
 * GoalAllocationRepositoryAdapter} (F039, ADR 0034) against a real Testcontainers Postgres (ADR
 * 0010), so {@code V28} runs for real. The non-archived filter of {@code
 * sumPercentageByProductExcludingGoal} restates a domain rule in JPQL, so it is exercised here
 * against both statuses.
 */
@DatabaseIntegrationTest
class GoalRepositoryAdapterTest {

  @Autowired private GoalRepository goalRepository;
  @Autowired private GoalAllocationRepository allocationRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private UUID categoryId;

  @BeforeEach
  void setUp() {
    categoryId = TaxonomySeeder.investmentCategory(jdbcTemplate, "Category Goal Repo Test").getId();
  }

  private UUID newProduct(String name) {
    return productRepository
        .save(InvestmentProduct.create(UUID.randomUUID(), categoryId, null, name, null))
        .getId();
  }

  private Goal newGoal(GoalMother mother) {
    return goalRepository.save(mother.build());
  }

  private GoalAllocation allocation(UUID goalId, UUID productId, String percentage) {
    return GoalAllocation.create(UUID.randomUUID(), goalId, productId, new BigDecimal(percentage));
  }

  @Test
  void aGoalRoundTripsEveryField() {
    Goal saved = newGoal(GoalMother.goal().withNotes("a note").withReturns("1.50", "4.25", "7.75"));

    Goal loaded = goalRepository.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getName()).isEqualTo("Car");
    assertThat(loaded.getNotes()).isEqualTo("a note");
    assertThat(loaded.getTargetAmount()).isEqualByComparingTo("80000");
    assertThat(loaded.getTargetDate()).isEqualTo(saved.getTargetDate());
    assertThat(loaded.getPlannedMonthlyContribution()).isEqualByComparingTo("2000");
    assertThat(loaded.getReturnPessimistic()).isEqualByComparingTo("1.5");
    assertThat(loaded.getReturnBase()).isEqualByComparingTo("4.25");
    assertThat(loaded.getReturnOptimistic()).isEqualByComparingTo("7.75");
    assertThat(loaded.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    assertThat(loaded.isShowOnDashboard()).isTrue();
  }

  @Test
  void aDatelessGoalAndAnEditArePersisted() {
    Goal saved = newGoal(GoalMother.goal().dateless());
    assertThat(goalRepository.findById(saved.getId()).orElseThrow().getTargetDate()).isNull();

    saved.edit(
        "Renamed",
        null,
        new BigDecimal("10"),
        null,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        GoalStatus.ARCHIVED,
        true);
    goalRepository.save(saved);
    entityManager.flush();
    entityManager.clear();

    Goal loaded = goalRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getName()).isEqualTo("Renamed");
    assertThat(loaded.getStatus()).isEqualTo(GoalStatus.ARCHIVED);
    assertThat(loaded.isShowOnDashboard()).isFalse();
  }

  @Test
  void findAllReturnsEveryGoal() {
    Goal a = newGoal(GoalMother.goal().withName("A Goal Repo Test"));
    Goal b = newGoal(GoalMother.goal().withName("B Goal Repo Test"));

    assertThat(goalRepository.findAll()).extracting(Goal::getId).contains(a.getId(), b.getId());
  }

  @Test
  void replaceForGoalReplacesTheWholeSetEvenWithTheSameProduct() {
    Goal goal = newGoal(GoalMother.goal());
    UUID p1 = newProduct("P1 Goal Repo Test");
    UUID p2 = newProduct("P2 Goal Repo Test");
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(allocation(goal.getId(), p1, "60"), allocation(goal.getId(), p2, "10")));

    allocationRepository.replaceForGoal(
        goal.getId(), List.of(allocation(goal.getId(), p1, "70.50")));
    entityManager.flush();
    entityManager.clear();

    List<GoalAllocation> stored = allocationRepository.findByGoalId(goal.getId());
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).getProductId()).isEqualTo(p1);
    assertThat(stored.get(0).getPercentage()).isEqualByComparingTo("70.5");
  }

  @Test
  void replacingWithAnEmptyListClearsTheSet() {
    Goal goal = newGoal(GoalMother.goal());
    allocationRepository.replaceForGoal(
        goal.getId(), List.of(allocation(goal.getId(), newProduct("P Goal Repo Test"), "50")));

    allocationRepository.replaceForGoal(goal.getId(), List.of());

    assertThat(allocationRepository.findByGoalId(goal.getId())).isEmpty();
  }

  @Test
  void findByGoalIdsAndByProductIdReadAcrossGoals() {
    Goal g1 = newGoal(GoalMother.goal().withName("G1 Goal Repo Test"));
    Goal g2 = newGoal(GoalMother.goal().withName("G2 Goal Repo Test"));
    UUID product = newProduct("P Goal Repo Test");
    allocationRepository.replaceForGoal(g1.getId(), List.of(allocation(g1.getId(), product, "60")));
    allocationRepository.replaceForGoal(g2.getId(), List.of(allocation(g2.getId(), product, "40")));

    assertThat(allocationRepository.findByGoalIds(Set.of(g1.getId(), g2.getId()))).hasSize(2);
    assertThat(allocationRepository.findByGoalIds(Set.of())).isEmpty();
    assertThat(allocationRepository.findByProductId(product))
        .extracting(GoalAllocation::getGoalId)
        .containsExactlyInAnyOrder(g1.getId(), g2.getId());
    assertThat(allocationRepository.existsByProductId(product)).isTrue();
    assertThat(allocationRepository.existsByProductId(UUID.randomUUID())).isFalse();
  }

  @Test
  void theSumExcludesTheGivenGoalAndArchivedGoals() {
    Goal mine = newGoal(GoalMother.goal().withName("Mine Goal Repo Test"));
    Goal other = newGoal(GoalMother.goal().withName("Other Goal Repo Test"));
    Goal third = newGoal(GoalMother.goal().withName("Third Goal Repo Test"));
    Goal archived =
        newGoal(
            GoalMother.goal()
                .withName("Archived Goal Repo Test")
                .withStatus(GoalStatus.ARCHIVED)
                .hiddenFromDashboard());
    UUID product = newProduct("P Goal Repo Test");
    UUID untouched = newProduct("Untouched Goal Repo Test");
    allocationRepository.replaceForGoal(
        mine.getId(), List.of(allocation(mine.getId(), product, "25")));
    allocationRepository.replaceForGoal(
        other.getId(), List.of(allocation(other.getId(), product, "30")));
    allocationRepository.replaceForGoal(
        third.getId(), List.of(allocation(third.getId(), product, "15.5")));
    allocationRepository.replaceForGoal(
        archived.getId(), List.of(allocation(archived.getId(), product, "50")));

    Map<UUID, BigDecimal> sums =
        allocationRepository.sumPercentageByProductExcludingGoal(
            Set.of(product, untouched), mine.getId());

    assertThat(sums).containsOnlyKeys(product);
    assertThat(sums.get(product)).isEqualByComparingTo("45.5");
    assertThat(allocationRepository.sumPercentageByProductExcludingGoal(Set.of(), mine.getId()))
        .isEmpty();
  }

  @Test
  void deletingAGoalDeletesItsAllocations() {
    Goal goal = newGoal(GoalMother.goal());
    UUID product = newProduct("P Goal Repo Test");
    allocationRepository.replaceForGoal(
        goal.getId(), List.of(allocation(goal.getId(), product, "50")));
    entityManager.flush();

    goalRepository.deleteById(goal.getId());
    entityManager.flush();
    entityManager.clear();

    assertThat(goalRepository.findById(goal.getId())).isEmpty();
    assertThat(allocationRepository.existsByProductId(product)).isFalse();
  }

  /**
   * Parity (CODING_STANDARDS "Changes that travel together"): the JPQL sum restates "archived goals
   * do not count" in SQL, so it is compared with the same rule written in Java, on random statuses
   * and shares. Change {@code GoalAllocationJpaRepository#sumPercentageByProductExcludingGoal} and
   * {@code GoalStatus} semantics together.
   */
  @Test
  void theSumMatchesTheJavaRuleOnRandomGoals() {
    java.util.Random random = new java.util.Random(39);
    UUID product = newProduct("P Parity Goal Repo Test");
    Goal self = newGoal(GoalMother.goal().withName("Self Parity Goal Repo Test"));
    BigDecimal expected = BigDecimal.ZERO;
    for (int i = 0; i < 25; i++) {
      GoalStatus status = GoalStatus.values()[random.nextInt(GoalStatus.values().length)];
      GoalMother mother = GoalMother.goal().withName("Parity " + i + " " + UUID.randomUUID());
      if (status == GoalStatus.ARCHIVED) {
        mother = mother.withStatus(status).hiddenFromDashboard();
      } else {
        mother = mother.withStatus(status);
      }
      Goal goal = newGoal(mother);
      BigDecimal share = BigDecimal.valueOf(random.nextInt(400) + 1, 2);
      allocationRepository.replaceForGoal(
          goal.getId(), List.of(allocation(goal.getId(), product, share.toPlainString())));
      if (goal.getStatus() != GoalStatus.ARCHIVED) {
        expected = expected.add(share);
      }
    }
    allocationRepository.replaceForGoal(
        self.getId(), List.of(allocation(self.getId(), product, "50")));

    BigDecimal actual =
        allocationRepository
            .sumPercentageByProductExcludingGoal(Set.of(product), self.getId())
            .get(product);

    assertThat(actual).isEqualByComparingTo(expected);
  }

  @Test
  void multiIdReadsRunTheSameNumberOfStatementsForFewAndManyGoals() {
    UUID product = newProduct("P Goal Repo Test");
    List<UUID> few = seedGoalsAllocating(product, 2);
    entityManager.flush();
    entityManager.clear();
    long fewStatements =
        StatementCount.of(
            entityManager,
            () -> {
              allocationRepository.findByGoalIds(few);
              allocationRepository.sumPercentageByProductExcludingGoal(
                  List.of(product), few.get(0));
            });

    List<UUID> many = seedGoalsAllocating(product, 12);
    entityManager.flush();
    entityManager.clear();
    long manyStatements =
        StatementCount.of(
            entityManager,
            () -> {
              allocationRepository.findByGoalIds(many);
              allocationRepository.sumPercentageByProductExcludingGoal(
                  List.of(product), many.get(0));
            });

    assertThat(manyStatements).isEqualTo(fewStatements).isLessThanOrEqualTo(2);
  }

  private List<UUID> seedGoalsAllocating(UUID product, int count) {
    java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
    for (int i = 0; i < count; i++) {
      Goal goal = newGoal(GoalMother.goal().withName("Bulk " + i + " " + UUID.randomUUID()));
      allocationRepository.replaceForGoal(
          goal.getId(), List.of(allocation(goal.getId(), product, "1")));
      ids.add(goal.getId());
    }
    return ids;
  }
}
