package com.chm.myfinances.application.investmentproduct;

/**
 * The derived, not-stored status filter for the global product list (F023 spec): a product's status
 * comes from its holdings, the same pattern as {@code needsSnapshot} - never a column on {@code
 * InvestmentProduct} itself.
 */
public enum InvestmentProductStatus {
  /** At least one of the product's holdings is open ({@code closedDate == null}). */
  OPEN,
  /** Every one of the product's holdings is closed, or it has none at all. */
  CLOSED,
  /** No status constraint. */
  ALL
}
