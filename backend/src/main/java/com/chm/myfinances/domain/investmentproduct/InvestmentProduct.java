package com.chm.myfinances.domain.investmentproduct;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment product aggregate (PRD S5.8, F008 spec): a holding tracked inside an {@code
 * INVESTMENT} account (a Tesouro Selic bond, a fund, an ETF, ...), classified by a category and an
 * optional sub-category. Its value comes from snapshots and its trades from transfers, both F009.
 *
 * <p>References are held by id only - this class doesn't import the account or taxonomy packages.
 * That the account is an open {@code INVESTMENT} account, that the category exists and that the
 * sub-category belongs to it are checked in {@code InvestmentProductService}. {@code close()} sets
 * {@code closedDate} once; hard-deleting a product is a separate, history-guarded use case.
 */
public final class InvestmentProduct {

  private final UUID id;
  private UUID accountId;
  private UUID investmentCategoryId;
  private UUID investmentSubcategoryId;
  private String name;
  private LocalDate closedDate;

  private InvestmentProduct(
      UUID id,
      UUID accountId,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      LocalDate closedDate) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.investmentCategoryId =
        Objects.requireNonNull(investmentCategoryId, "investmentCategoryId must not be null");
    this.investmentSubcategoryId = investmentSubcategoryId;
    this.name = requireValidName(name);
    this.closedDate = closedDate;
  }

  /** Creates a brand-new, open product. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentProduct create(
      UUID id,
      UUID accountId,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name) {
    return new InvestmentProduct(
        id, accountId, investmentCategoryId, investmentSubcategoryId, name, null);
  }

  /** Rebuilds a product from already-validated persisted state. */
  public static InvestmentProduct reconstitute(
      UUID id,
      UUID accountId,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      LocalDate closedDate) {
    return new InvestmentProduct(
        id, accountId, investmentCategoryId, investmentSubcategoryId, name, closedDate);
  }

  /**
   * Full-replace edit (PATCH, F008 spec): every editable field at once. All values are validated
   * before any is assigned, so a rejected edit leaves the product untouched. A closed product can
   * still be edited - closing blocks new activity, not a metadata correction.
   */
  public void edit(
      UUID newAccountId,
      UUID newInvestmentCategoryId,
      UUID newInvestmentSubcategoryId,
      String newName) {
    UUID validAccountId = Objects.requireNonNull(newAccountId, "accountId must not be null");
    UUID validCategoryId =
        Objects.requireNonNull(newInvestmentCategoryId, "investmentCategoryId must not be null");
    String validName = requireValidName(newName);
    this.accountId = validAccountId;
    this.investmentCategoryId = validCategoryId;
    this.investmentSubcategoryId = newInvestmentSubcategoryId;
    this.name = validName;
  }

  /**
   * Sets {@code closedDate}. Closing an already-closed product is rejected - {@code closedDate} is
   * set once, same as {@code Account.close()}. Takes the date as a parameter rather than reading
   * {@code LocalDate.now()} itself, for the same framework-free reason as {@code Account.close()}
   * (ADR 0004/0005) - "today" comes from {@code InvestmentProductService}'s injected {@code Clock}.
   */
  public void close(LocalDate closedDate) {
    if (isClosed()) {
      throw new IllegalStateException("Investment product is already closed: " + id);
    }
    this.closedDate = Objects.requireNonNull(closedDate, "closedDate must not be null");
  }

  public boolean isClosed() {
    return closedDate != null;
  }

  private static String requireValidName(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (value.length() > TextFieldConstraints.MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "name must not exceed " + TextFieldConstraints.MAX_NAME_LENGTH + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getInvestmentCategoryId() {
    return investmentCategoryId;
  }

  /** {@code null} when the product is classified by category only (e.g. Crypto). */
  public UUID getInvestmentSubcategoryId() {
    return investmentSubcategoryId;
  }

  public String getName() {
    return name;
  }

  public LocalDate getClosedDate() {
    return closedDate;
  }
}
