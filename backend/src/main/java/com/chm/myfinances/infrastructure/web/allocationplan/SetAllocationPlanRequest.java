package com.chm.myfinances.infrastructure.web.allocationplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;
import java.util.List;

/**
 * Request body for {@code PUT /api/fii/allocation-plan} (F026 spec): creates a new version, or
 * replaces the existing one for {@code effectiveFrom} if one already exists (same-month correction,
 * mirroring {@code UpdateBudgetCapRequest}).
 */
public record SetAllocationPlanRequest(
    @NotEmpty @Valid List<AllocationPlanEntryRequest> entries, @NotNull YearMonth effectiveFrom) {}
