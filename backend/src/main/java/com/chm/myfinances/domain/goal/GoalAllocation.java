package com.chm.myfinances.domain.goal;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One share of an investment product assigned to a {@link Goal} (F039 spec, ADR 0034): a {@code
 * percentage} of the product's value, in {@code (0, 100]} with at most two decimals (the {@code
 * numeric(5,2)} column; padded to that scale so a re-saved figure is a no-op for the audit diff).
 * Unique per (goal, product).
 *
 * <p>The goal and the product are held by id only - this package never imports {@code
 * domain.investmentproduct}. That the product exists, that no product exceeds 100% across
 * non-archived goals and that a product appears once per goal are {@code GoalService} concerns:
 * they span other aggregates or sibling rows.
 */
public final class GoalAllocation {

  private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");
  private static final int SCALE = 2;

  private final UUID id;
  private final UUID goalId;
  private final UUID productId;
  private final BigDecimal percentage;

  private GoalAllocation(UUID id, UUID goalId, UUID productId, BigDecimal percentage) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.goalId = Objects.requireNonNull(goalId, "goalId must not be null");
    this.productId = Objects.requireNonNull(productId, "productId must not be null");
    this.percentage = requireValidPercentage(percentage);
  }

  /** Creates a brand-new allocation. {@code id} must come from the {@code IdGenerator} port. */
  public static GoalAllocation create(UUID id, UUID goalId, UUID productId, BigDecimal percentage) {
    return new GoalAllocation(id, goalId, productId, percentage);
  }

  /** Rebuilds an allocation from already-validated persisted state. */
  public static GoalAllocation reconstitute(
      UUID id, UUID goalId, UUID productId, BigDecimal percentage) {
    return new GoalAllocation(id, goalId, productId, percentage);
  }

  private static BigDecimal requireValidPercentage(BigDecimal value) {
    if (value == null) {
      throw new IllegalArgumentException("percentage must not be null");
    }
    if (value.signum() <= 0) {
      throw new IllegalArgumentException("percentage must be positive");
    }
    if (value.compareTo(MAX_PERCENTAGE) > 0) {
      throw new IllegalArgumentException("percentage must be at most 100");
    }
    if (value.stripTrailingZeros().scale() > SCALE) {
      throw new IllegalArgumentException("percentage allows at most " + SCALE + " decimal places");
    }
    return value.setScale(SCALE);
  }

  public UUID getId() {
    return id;
  }

  public UUID getGoalId() {
    return goalId;
  }

  public UUID getProductId() {
    return productId;
  }

  public BigDecimal getPercentage() {
    return percentage;
  }

  /** Flat snapshot of the persisted fields beyond the goal it belongs to (F025 spec). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("productId", productId.toString());
    snapshot.put("percentage", percentage);
    return snapshot;
  }
}
