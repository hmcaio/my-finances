package com.chm.myfinances.application.auditlog;

/**
 * Every aggregate F025 audits (spec's Scope). Extended beyond the spec's original enumerated list
 * to also cover {@code InvestmentSegment} and {@code AllocationPlan}/{@code AllocationPlanVersion}
 * (F026) and Transfer's {@code TradeConfirmation} lines (F027) - both landed on {@code develop}
 * after this feature's spec/plan were written (the spec's own "(once F024 lands)" aside for {@code
 * Vehicle} shows it was already tracking this drift), and the PR2 goal of emptying the ArchUnit
 * allowlist requires every write use case - including these two - to depend on {@link
 * AuditRecorder}.
 */
public enum AuditEntityType {
  TRANSACTION,
  TRANSFER,
  ACCOUNT,
  CATEGORY,
  PAYMENT_METHOD,
  INSTITUTION,
  BUDGET,
  RECURRING_TEMPLATE,
  INVESTMENT_CATEGORY,
  INVESTMENT_SUBCATEGORY,
  INVESTMENT_PRODUCT,
  INVESTMENT_HOLDING,
  INVESTMENT_SNAPSHOT,
  INVESTMENT_SEGMENT,
  ALLOCATION_PLAN,
  VEHICLE
}
