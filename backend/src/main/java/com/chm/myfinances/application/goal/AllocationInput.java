package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.util.UUID;

/** One requested share in a goal's allocation set (F039): a product and its percentage. */
public record AllocationInput(UUID productId, BigDecimal percentage) {}
