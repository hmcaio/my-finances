package com.chm.myfinances.application.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of {@link MonthlySpendByCategoryQuery}: a category's total expenses for a month. */
public record CategorySpend(UUID categoryId, BigDecimal total) {}
