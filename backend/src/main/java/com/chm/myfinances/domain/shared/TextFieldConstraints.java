package com.chm.myfinances.domain.shared;

/**
 * Shared length limits for the bounded free-text fields used across aggregates, keeping the domain
 * invariant, the DTO {@code @Size} constraint, and the {@code varchar(n)} column all agreeing on
 * the same numbers instead of each layer/aggregate picking its own (security-audit fix: unbounded
 * free-text input).
 *
 * <p>{@code MAX_NAME_LENGTH} is for flat taxonomy "name" fields (Category, PaymentMethod, Account,
 * Institution, InvestmentCategory, InvestmentSubcategory, InvestmentProduct). {@code
 * MAX_DESCRIPTION_LENGTH}/{@code MAX_ADDITIONAL_NOTES_LENGTH} are for the narrative "description"
 * (mandatory) + "additional notes" (optional) pair on non-taxonomy entities (Transaction, Transfer,
 * RecurringTemplate).
 */
public final class TextFieldConstraints {

  public static final int MAX_NAME_LENGTH = 100;
  public static final int MAX_DESCRIPTION_LENGTH = 150;
  public static final int MAX_ADDITIONAL_NOTES_LENGTH = 500;

  private TextFieldConstraints() {}
}
