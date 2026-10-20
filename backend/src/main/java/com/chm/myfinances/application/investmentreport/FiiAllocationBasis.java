package com.chm.myfinances.application.investmentreport;

/** Which allocation {@code FiiAllocationQuery} reports (F026 spec): the API's {@code basis=}. */
public enum FiiAllocationBasis {
  /** Current value, from the latest snapshots (ADR 0012's valuation source, unchanged). */
  ACTUAL,
  /** The current {@code AllocationPlanVersion}'s target percentages. */
  PLANNED
}
