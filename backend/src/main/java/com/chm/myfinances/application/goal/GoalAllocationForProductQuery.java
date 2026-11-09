package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The goals a product is allocated to (F039 spec, PRD S6.17): the read-only "Allocated to" list of
 * the product detail page. Archived goals are left out, since they no longer count toward the
 * product's 100%. Largest share first.
 */
@Service
public class GoalAllocationForProductQuery {

  /** One goal the product is allocated to, and the share of the product it holds. */
  public record ProductGoalAllocation(UUID goalId, String goalName, BigDecimal percentage) {}

  private final GoalRepository goalRepository;
  private final GoalAllocationRepository allocationRepository;
  private final InvestmentProductRepository productRepository;

  public GoalAllocationForProductQuery(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      InvestmentProductRepository productRepository) {
    this.goalRepository = goalRepository;
    this.allocationRepository = allocationRepository;
    this.productRepository = productRepository;
  }

  /** 404 {@link InvestmentProductNotFoundException} for an unknown product. */
  public List<ProductGoalAllocation> forProduct(UUID productId) {
    if (!productRepository.existsById(productId)) {
      throw new InvestmentProductNotFoundException(productId);
    }
    List<GoalAllocation> allocations = allocationRepository.findByProductId(productId);
    if (allocations.isEmpty()) {
      return List.of();
    }
    Map<UUID, Goal> goals =
        goalRepository.findAll().stream()
            .collect(Collectors.toMap(Goal::getId, Function.identity()));
    return allocations.stream()
        .filter(a -> goals.get(a.getGoalId()).getStatus() != GoalStatus.ARCHIVED)
        .sorted(
            Comparator.comparing(GoalAllocation::getPercentage)
                .reversed()
                .thenComparing(
                    a -> goals.get(a.getGoalId()).getName(), String.CASE_INSENSITIVE_ORDER))
        .map(
            a ->
                new ProductGoalAllocation(
                    a.getGoalId(), goals.get(a.getGoalId()).getName(), a.getPercentage()))
        .toList();
  }
}
