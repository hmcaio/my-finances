package com.chm.myfinances.application.investmentreport;

/**
 * How {@link InvestmentAllocationQuery} groups product values (F009 spec's {@code groupBy}; {@code
 * ACCOUNT} added by F023).
 */
public enum AllocationGrouping {
  /** One row per investment category. */
  CATEGORY,
  /** One row per category then sub-category; products without a sub-category get a null slice. */
  SUBCATEGORY,
  /**
   * One row per {@code INVESTMENT} account: the sum of the latest snapshots of the holdings there
   * (F023 - enabled by F022's holdings, no new value computation).
   */
  ACCOUNT
}
