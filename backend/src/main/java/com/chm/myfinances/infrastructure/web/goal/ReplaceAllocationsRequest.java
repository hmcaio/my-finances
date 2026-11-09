package com.chm.myfinances.infrastructure.web.goal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PUT /api/goals/{id}/allocations} (F039 spec): the goal's whole allocation
 * set, replacing the previous one (an empty list clears it). A percentage is above 0 and at most
 * 100, with up to two decimals.
 */
public record ReplaceAllocationsRequest(@NotNull @Valid List<AllocationRequest> allocations) {

  /** One share of a product assigned to the goal. */
  public record AllocationRequest(
      @NotNull UUID productId,
      @NotNull @Positive @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal percentage) {}
}
