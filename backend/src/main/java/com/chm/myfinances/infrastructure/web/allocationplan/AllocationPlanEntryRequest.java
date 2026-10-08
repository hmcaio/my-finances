package com.chm.myfinances.infrastructure.web.allocationplan;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** One entry of a {@code PUT /api/fii/allocation-plan} request body (F026 spec). */
public record AllocationPlanEntryRequest(
    @NotNull UUID investmentProductId,
    @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal targetPercentage) {}
