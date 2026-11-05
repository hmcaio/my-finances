package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Goal} and its {@link GoalAllocation}s (F039 spec, ADR 0034). New ids come
 * from the {@link IdGenerator} port (ADR 0005). Every write is {@code @Transactional} and audited
 * in the same transaction ({@code AuditEntityType.GOAL}); replacing the allocations is audited as
 * an update of the goal, with the allocation set in the diff under {@code allocations}.
 *
 * <p>Rules that need a clock, other aggregates or sibling rows live here, not in the domain: a
 * target date must be after today when the goal is created and whenever it changes (an unchanged
 * date that has since passed is allowed, so an overdue goal's notes stay editable); a product
 * allocated to a goal must exist; and a product's percentages across the other non-archived goals
 * plus this one must not exceed 100 - checked in the transaction, not by a database constraint,
 * because it spans rows of different goals and ignores archived ones. Archiving (an {@link #edit}
 * to {@code ARCHIVED}) frees a goal's percentages; restoring it re-checks that they still fit.
 */
@Service
public class GoalService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final GoalRepository goalRepository;
  private final GoalAllocationRepository allocationRepository;
  private final InvestmentProductRepository productRepository;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final AuditRecorder auditRecorder;

  public GoalService(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      InvestmentProductRepository productRepository,
      IdGenerator idGenerator,
      Clock clock,
      AuditRecorder auditRecorder) {
    this.goalRepository = goalRepository;
    this.allocationRepository = allocationRepository;
    this.productRepository = productRepository;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.auditRecorder = auditRecorder;
  }

  /** Creates an {@code ACTIVE} goal with no allocations. */
  @Transactional
  public Goal create(GoalInput input) {
    requireFutureDate(input.targetDate());
    Goal goal;
    try {
      goal =
          Goal.create(
              idGenerator.newId(),
              input.name(),
              input.notes(),
              input.targetAmount(),
              input.targetDate(),
              input.plannedMonthlyContribution(),
              input.returnPessimistic(),
              input.returnBase(),
              input.returnOptimistic(),
              input.showOnDashboard());
    } catch (IllegalArgumentException e) {
      throw new InvalidGoalException(e.getMessage());
    }
    Goal saved = goalRepository.save(goal);
    auditRecorder.recordCreate(
        AuditEntityType.GOAL, saved.getId(), saved.getName(), saved.toAuditSnapshot());
    return saved;
  }

  /**
   * Full-replace edit, including the status (also how a goal is archived or restored). Restoring an
   * archived goal re-checks every one of its allocations against the other non-archived goals.
   */
  @Transactional
  public Goal edit(UUID id, GoalInput input, GoalStatus status) {
    Goal goal = findById(id);
    Map<String, Object> before = goal.toAuditSnapshot();
    if (input.targetDate() != null && !input.targetDate().equals(goal.getTargetDate())) {
      requireFutureDate(input.targetDate());
    }
    if (goal.getStatus() == GoalStatus.ARCHIVED && status != GoalStatus.ARCHIVED) {
      requireAllocationsFit(id, allocationRepository.findByGoalId(id));
    }
    try {
      goal.edit(
          input.name(),
          input.notes(),
          input.targetAmount(),
          input.targetDate(),
          input.plannedMonthlyContribution(),
          input.returnPessimistic(),
          input.returnBase(),
          input.returnOptimistic(),
          status,
          input.showOnDashboard());
    } catch (IllegalArgumentException e) {
      throw new InvalidGoalException(e.getMessage());
    }
    Goal saved = goalRepository.save(goal);
    auditRecorder.recordUpdate(
        AuditEntityType.GOAL, saved.getId(), saved.getName(), before, saved.toAuditSnapshot());
    return saved;
  }

  /** Turns the dashboard flag on or off; on is rejected for an archived goal. */
  @Transactional
  public Goal setShowOnDashboard(UUID id, boolean show) {
    Goal goal = findById(id);
    if (show && goal.getStatus() == GoalStatus.ARCHIVED) {
      throw new GoalArchivedException(id);
    }
    Map<String, Object> before = goal.toAuditSnapshot();
    goal.setShowOnDashboard(show);
    Goal saved = goalRepository.save(goal);
    auditRecorder.recordUpdate(
        AuditEntityType.GOAL, saved.getId(), saved.getName(), before, saved.toAuditSnapshot());
    return saved;
  }

  /** Hard-deletes the goal; its allocations go with it (database cascade). */
  @Transactional
  public void delete(UUID id) {
    Goal goal = findById(id);
    Map<String, Object> before =
        snapshotWithAllocations(goal, allocationRepository.findByGoalId(id));
    goalRepository.deleteById(id);
    auditRecorder.recordDelete(AuditEntityType.GOAL, goal.getId(), goal.getName(), before);
  }

  /**
   * Replaces the goal's whole allocation set. Checks, in order: the goal exists (404) and is not
   * archived (409); each percentage is valid and no product repeats (400); every product exists
   * (404); and no product exceeds 100% across the other non-archived goals plus this one (409).
   */
  @Transactional
  public List<GoalAllocation> replaceAllocations(UUID goalId, List<AllocationInput> inputs) {
    Goal goal = findById(goalId);
    if (goal.getStatus() == GoalStatus.ARCHIVED) {
      throw new GoalArchivedException(goalId);
    }
    List<GoalAllocation> allocations = buildAllocations(goalId, inputs);
    requireProductsExist(allocations);
    requireAllocationsFit(goalId, allocations);

    List<GoalAllocation> previous = allocationRepository.findByGoalId(goalId);
    allocationRepository.replaceForGoal(goalId, allocations);
    auditRecorder.recordUpdate(
        AuditEntityType.GOAL,
        goal.getId(),
        goal.getName(),
        snapshotWithAllocations(goal, previous),
        snapshotWithAllocations(goal, allocations));
    return allocations;
  }

  public Goal findById(UUID id) {
    return goalRepository.findById(id).orElseThrow(() -> new GoalNotFoundException(id));
  }

  private void requireFutureDate(LocalDate targetDate) {
    if (targetDate != null && !targetDate.isAfter(LocalDate.now(clock))) {
      throw new InvalidGoalException("targetDate must be after today");
    }
  }

  private List<GoalAllocation> buildAllocations(UUID goalId, List<AllocationInput> inputs) {
    Set<UUID> seen = new HashSet<>();
    List<GoalAllocation> allocations = new java.util.ArrayList<>();
    for (AllocationInput input : inputs) {
      if (input.productId() != null && !seen.add(input.productId())) {
        throw new InvalidGoalException("a product can appear only once per goal");
      }
      try {
        allocations.add(
            GoalAllocation.create(
                idGenerator.newId(), goalId, input.productId(), input.percentage()));
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new InvalidGoalException(e.getMessage());
      }
    }
    return allocations;
  }

  private void requireProductsExist(List<GoalAllocation> allocations) {
    if (allocations.isEmpty()) {
      return;
    }
    Set<UUID> existing =
        productRepository.findAll().stream()
            .map(InvestmentProduct::getId)
            .collect(Collectors.toSet());
    for (GoalAllocation allocation : allocations) {
      if (!existing.contains(allocation.getProductId())) {
        throw new InvestmentProductNotFoundException(allocation.getProductId());
      }
    }
  }

  /** One query for every product: others' non-archived sum plus this goal's share must fit. */
  private void requireAllocationsFit(UUID goalId, List<GoalAllocation> allocations) {
    if (allocations.isEmpty()) {
      return;
    }
    Set<UUID> productIds =
        allocations.stream().map(GoalAllocation::getProductId).collect(Collectors.toSet());
    Map<UUID, BigDecimal> others =
        allocationRepository.sumPercentageByProductExcludingGoal(productIds, goalId);
    for (GoalAllocation allocation : allocations) {
      BigDecimal total =
          others
              .getOrDefault(allocation.getProductId(), BigDecimal.ZERO)
              .add(allocation.getPercentage());
      if (total.compareTo(HUNDRED) > 0) {
        throw new GoalAllocationExceedsProductException(allocation.getProductId());
      }
    }
  }

  /** The goal's own snapshot plus {@code allocations}: a list of flat maps, ordered by product. */
  private static Map<String, Object> snapshotWithAllocations(
      Goal goal, List<GoalAllocation> allocations) {
    Map<String, Object> snapshot = new LinkedHashMap<>(goal.toAuditSnapshot());
    snapshot.put(
        "allocations",
        allocations.stream()
            .sorted(Comparator.comparing(a -> a.getProductId().toString()))
            .map(GoalAllocation::toAuditSnapshot)
            .toList());
    return snapshot;
  }
}
