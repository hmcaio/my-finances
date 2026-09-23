package com.chm.myfinances.application.investmentreport;

/** How {@link InvestmentAllocationQuery} groups product values (F009 spec's {@code groupBy}). */
public enum AllocationGrouping {
  /** One row per investment category. */
  CATEGORY,
  /** One row per category then sub-category; products without a sub-category get a null slice. */
  SUBCATEGORY
}
