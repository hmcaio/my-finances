package com.chm.myfinances.domain.allocationplan;

import java.util.Objects;
import java.util.UUID;

/**
 * AllocationPlan aggregate (F026 spec, ADR 0023): a one-row marker, like {@code Budget} is one row
 * per category except there is exactly one of these ever, period, no other fields. Its versioned
 * history ({@link AllocationPlanVersion}) carries every target percentage. {@code
 * AllocationPlanService} ensures this row exists (created on first use) rather than it being
 * user-creatable via the API.
 */
public final class AllocationPlan {

  private final UUID id;

  private AllocationPlan(UUID id) {
    this.id = Objects.requireNonNull(id, "id must not be null");
  }

  /** Creates the plan's one row. {@code id} must come from the {@code IdGenerator} port. */
  public static AllocationPlan create(UUID id) {
    return new AllocationPlan(id);
  }

  /** Rebuilds the plan from already-validated persisted state. */
  public static AllocationPlan reconstitute(UUID id) {
    return new AllocationPlan(id);
  }

  public UUID getId() {
    return id;
  }
}
