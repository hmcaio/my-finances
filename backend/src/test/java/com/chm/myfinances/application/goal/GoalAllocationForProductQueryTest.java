package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.testsupport.fakes.FakeGoalAllocationRepository;
import com.chm.myfinances.testsupport.fakes.FakeGoalRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.mothers.GoalMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalAllocationForProductQuery} (F039): the non-archived goals
 * a product is allocated to, largest share first.
 */
class GoalAllocationForProductQueryTest {

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final GoalAllocationForProductQuery query =
      new GoalAllocationForProductQuery(goalRepository, allocationRepository, productRepository);

  private final UUID productId =
      productRepository.save(InvestmentProductMother.product().build()).getId();

  private void allocate(Goal goal, String percentage) {
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(
                UUID.randomUUID(), goal.getId(), productId, new BigDecimal(percentage))));
  }

  @Test
  void listsTheGoalsTheProductIsAllocatedToLargestShareFirst() {
    Goal car = goalRepository.save(GoalMother.goal().withName("Car").build());
    Goal trip = goalRepository.save(GoalMother.goal().withName("Trip").build());
    allocate(car, "60");
    allocate(trip, "40");

    List<GoalAllocationForProductQuery.ProductGoalAllocation> result = query.forProduct(productId);

    assertThat(result)
        .extracting(GoalAllocationForProductQuery.ProductGoalAllocation::goalName)
        .containsExactly("Car", "Trip");
    assertThat(result.get(0).percentage()).isEqualByComparingTo("60");
    assertThat(result.get(0).goalId()).isEqualTo(car.getId());
  }

  @Test
  void leavesOutArchivedGoalsAndIsEmptyWhenNothingIsAllocated() {
    assertThat(query.forProduct(productId)).isEmpty();

    Goal archived = goalRepository.save(GoalMother.goal().withStatus(GoalStatus.ARCHIVED).build());
    allocate(archived, "50");

    assertThat(query.forProduct(productId)).isEmpty();
  }

  @Test
  void anUnknownProductIsNotFound() {
    assertThatThrownBy(() -> query.forProduct(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }
}
