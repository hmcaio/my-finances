package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Test data builder for {@link Goal} (F039): a valid, in-memory goal with sensible defaults that a
 * test overrides only where it cares. A test asserting on {@link Goal#create}'s own validation
 * keeps calling {@code Goal.create(...)} directly.
 */
public final class GoalMother {

  private UUID id = UUID.randomUUID();
  private String name = "Car";
  private String notes;
  private BigDecimal targetAmount = new BigDecimal("80000.00");
  private LocalDate targetDate = LocalDate.of(2028, 11, 5);
  private BigDecimal contribution = new BigDecimal("2000.00");
  private BigDecimal pessimistic = new BigDecimal("2");
  private BigDecimal base = new BigDecimal("4");
  private BigDecimal optimistic = new BigDecimal("6");
  private GoalStatus status = GoalStatus.ACTIVE;
  private boolean showOnDashboard = true;

  private GoalMother() {}

  public static GoalMother goal() {
    return new GoalMother();
  }

  public GoalMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public GoalMother withName(String name) {
    this.name = name;
    return this;
  }

  public GoalMother withNotes(String notes) {
    this.notes = notes;
    return this;
  }

  public GoalMother withTargetAmount(String targetAmount) {
    this.targetAmount = new BigDecimal(targetAmount);
    return this;
  }

  public GoalMother withTargetDate(LocalDate targetDate) {
    this.targetDate = targetDate;
    return this;
  }

  public GoalMother dateless() {
    this.targetDate = null;
    return this;
  }

  public GoalMother withContribution(String contribution) {
    this.contribution = new BigDecimal(contribution);
    return this;
  }

  public GoalMother withReturns(String pessimistic, String base, String optimistic) {
    this.pessimistic = new BigDecimal(pessimistic);
    this.base = new BigDecimal(base);
    this.optimistic = new BigDecimal(optimistic);
    return this;
  }

  public GoalMother withStatus(GoalStatus status) {
    this.status = status;
    return this;
  }

  public GoalMother hiddenFromDashboard() {
    this.showOnDashboard = false;
    return this;
  }

  public Goal build() {
    Goal goal =
        Goal.create(
            id,
            name,
            notes,
            targetAmount,
            targetDate,
            contribution,
            pessimistic,
            base,
            optimistic,
            showOnDashboard);
    if (status != GoalStatus.ACTIVE) {
      goal.edit(
          name,
          notes,
          targetAmount,
          targetDate,
          contribution,
          pessimistic,
          base,
          optimistic,
          status,
          showOnDashboard);
    }
    return goal;
  }
}
