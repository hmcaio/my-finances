package com.chm.myfinances.domain.investmentproduct;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment product aggregate (PRD S5.8, F022 spec, ADR 0020): pure taxonomy for an instrument (a
 * Tesouro Selic bond, a fund, an ETF, ...), classified by a category and an optional sub-category,
 * globally unique by name. It no longer belongs to a single account - {@code InvestmentHolding} is
 * the many-to-many link to the {@code INVESTMENT} account(s) it's held in, and carries the
 * account-specific {@code closedDate}. A product's value comes from its holdings' snapshots and its
 * trades from transfers (F009).
 *
 * <p>References are held by id only - this class doesn't import the taxonomy package. That the
 * category exists and the sub-category belongs to it are checked in {@code
 * InvestmentProductService}.
 */
public final class InvestmentProduct {

  private final UUID id;
  private UUID investmentCategoryId;
  private UUID investmentSubcategoryId;
  private String name;
  private String additionalNotes;

  private InvestmentProduct(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.investmentCategoryId =
        Objects.requireNonNull(investmentCategoryId, "investmentCategoryId must not be null");
    this.investmentSubcategoryId = investmentSubcategoryId;
    this.name = requireValidName(name);
    this.additionalNotes = requireValidNotes(additionalNotes);
  }

  /** Creates a brand-new product. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentProduct create(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes) {
    return new InvestmentProduct(
        id, investmentCategoryId, investmentSubcategoryId, name, additionalNotes);
  }

  /** Rebuilds a product from already-validated persisted state. */
  public static InvestmentProduct reconstitute(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes) {
    return new InvestmentProduct(
        id, investmentCategoryId, investmentSubcategoryId, name, additionalNotes);
  }

  /**
   * Full-replace edit (PATCH): every editable field at once. All values are validated before any is
   * assigned, so a rejected edit leaves the product untouched.
   */
  public void edit(
      UUID newInvestmentCategoryId,
      UUID newInvestmentSubcategoryId,
      String newName,
      String newAdditionalNotes) {
    UUID validCategoryId =
        Objects.requireNonNull(newInvestmentCategoryId, "investmentCategoryId must not be null");
    String validName = requireValidName(newName);
    String validNotes = requireValidNotes(newAdditionalNotes);
    this.investmentCategoryId = validCategoryId;
    this.investmentSubcategoryId = newInvestmentSubcategoryId;
    this.name = validName;
    this.additionalNotes = validNotes;
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

  private static String requireValidNotes(String value) {
    if (value != null && value.length() > TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) {
      throw new IllegalArgumentException(
          "additionalNotes must not exceed "
              + TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH
              + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
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

  /** {@code null} when the product carries no remark. */
  public String getAdditionalNotes() {
    return additionalNotes;
  }
}
