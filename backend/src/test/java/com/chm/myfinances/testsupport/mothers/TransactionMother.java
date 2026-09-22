package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Test data builder for {@link Transaction} (issue #31, B6): a valid, in-memory transaction with
 * sensible defaults that a test overrides only where it cares. A test asserting on {@link
 * Transaction#create}'s own validation should keep calling {@code Transaction.create(...)} directly
 * - going through this builder would obscure what's being tested.
 */
public final class TransactionMother {

  private UUID id = UUID.randomUUID();
  private LocalDate date = LocalDate.now();
  private BigDecimal amount = new BigDecimal("42.50");
  private UUID categoryId = UUID.randomUUID();
  private CategoryType type = CategoryType.EXPENSE;
  private UUID accountId = UUID.randomUUID();
  private UUID paymentMethodId = UUID.randomUUID();
  private UUID recurringTemplateVersionId = null;
  private String description = "Groceries";
  private String additionalNotes = null;

  private TransactionMother() {}

  public static TransactionMother expense() {
    return new TransactionMother();
  }

  public static TransactionMother income() {
    return new TransactionMother().withType(CategoryType.INCOME).withDescription("Salary");
  }

  public TransactionMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public TransactionMother withDate(LocalDate date) {
    this.date = date;
    return this;
  }

  public TransactionMother withAmount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  public TransactionMother withCategoryId(UUID categoryId) {
    this.categoryId = categoryId;
    return this;
  }

  public TransactionMother withType(CategoryType type) {
    this.type = type;
    return this;
  }

  public TransactionMother withAccountId(UUID accountId) {
    this.accountId = accountId;
    return this;
  }

  public TransactionMother withPaymentMethodId(UUID paymentMethodId) {
    this.paymentMethodId = paymentMethodId;
    return this;
  }

  public TransactionMother withRecurringTemplateVersionId(UUID recurringTemplateVersionId) {
    this.recurringTemplateVersionId = recurringTemplateVersionId;
    return this;
  }

  public TransactionMother withDescription(String description) {
    this.description = description;
    return this;
  }

  public TransactionMother withAdditionalNotes(String additionalNotes) {
    this.additionalNotes = additionalNotes;
    return this;
  }

  public Transaction build() {
    return Transaction.create(
        id,
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes);
  }
}
