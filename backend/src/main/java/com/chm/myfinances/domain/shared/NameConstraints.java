package com.chm.myfinances.domain.shared;

/**
 * Shared length limit for free-text {@code name} fields on flat, user-editable taxonomy entities
 * (Category, PaymentMethod, and future ones such as F008's InvestmentCategory) — keeps the domain
 * invariant, the DTO {@code @Size} constraint, and the {@code varchar(n)} column all agreeing on
 * the same number instead of each layer/aggregate picking its own (security-audit fix: unbounded
 * free-text input).
 */
public final class NameConstraints {

  public static final int MAX_NAME_LENGTH = 100;

  private NameConstraints() {}
}
