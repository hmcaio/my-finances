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
 *
 * <p>F026 (ADR 0023) adds optional {@code ticker} and {@code segmentId}, generalized rather than
 * FII-scoped: any product may carry them, usable later by a stock/ETF feature without a migration,
 * even though the FII page is the only UI that manages them for v1. No invariant here links them to
 * the category/sub-category - that the allocation plan only accepts FII-subcategory products is an
 * {@code AllocationPlanService} concern. {@code segmentId} is held by id only - this class doesn't
 * import {@code domain.investmentsegment}.
 */
public final class InvestmentProduct {

  private final UUID id;
  private UUID investmentCategoryId;
  private UUID investmentSubcategoryId;
  private String name;
  private String additionalNotes;
  private String ticker;
  private UUID segmentId;

  private InvestmentProduct(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes,
      String ticker,
      UUID segmentId) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.investmentCategoryId =
        Objects.requireNonNull(investmentCategoryId, "investmentCategoryId must not be null");
    this.investmentSubcategoryId = investmentSubcategoryId;
    this.name = requireValidName(name);
    this.additionalNotes = requireValidNotes(additionalNotes);
    this.ticker = requireValidTicker(ticker);
    this.segmentId = segmentId;
  }

  /**
   * Creates a brand-new product with no ticker/segment. {@code id} comes from {@code IdGenerator}.
   */
  public static InvestmentProduct create(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes) {
    return create(
        id, investmentCategoryId, investmentSubcategoryId, name, additionalNotes, null, null);
  }

  /**
   * Creates a brand-new product, optionally carrying a {@code ticker}/{@code segmentId} (F026).
   * {@code id} must come from the {@code IdGenerator} port.
   */
  public static InvestmentProduct create(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes,
      String ticker,
      UUID segmentId) {
    return new InvestmentProduct(
        id,
        investmentCategoryId,
        investmentSubcategoryId,
        name,
        additionalNotes,
        ticker,
        segmentId);
  }

  /** Rebuilds a product (with no ticker/segment) from already-validated persisted state. */
  public static InvestmentProduct reconstitute(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes) {
    return reconstitute(
        id, investmentCategoryId, investmentSubcategoryId, name, additionalNotes, null, null);
  }

  /** Rebuilds a product, including its ticker/segment (F026), from already-validated state. */
  public static InvestmentProduct reconstitute(
      UUID id,
      UUID investmentCategoryId,
      UUID investmentSubcategoryId,
      String name,
      String additionalNotes,
      String ticker,
      UUID segmentId) {
    return new InvestmentProduct(
        id,
        investmentCategoryId,
        investmentSubcategoryId,
        name,
        additionalNotes,
        ticker,
        segmentId);
  }

  /**
   * Full-replace edit (PATCH): every editable field at once, clearing any ticker/segment. All
   * values are validated before any is assigned, so a rejected edit leaves the product untouched.
   */
  public void edit(
      UUID newInvestmentCategoryId,
      UUID newInvestmentSubcategoryId,
      String newName,
      String newAdditionalNotes) {
    edit(
        newInvestmentCategoryId,
        newInvestmentSubcategoryId,
        newName,
        newAdditionalNotes,
        null,
        null);
  }

  /**
   * Full-replace edit including {@code ticker}/{@code segmentId} (F026) - the overload the
   * controller calls. All values are validated before any is assigned, so a rejected edit leaves
   * the product untouched.
   */
  public void edit(
      UUID newInvestmentCategoryId,
      UUID newInvestmentSubcategoryId,
      String newName,
      String newAdditionalNotes,
      String newTicker,
      UUID newSegmentId) {
    UUID validCategoryId =
        Objects.requireNonNull(newInvestmentCategoryId, "investmentCategoryId must not be null");
    String validName = requireValidName(newName);
    String validNotes = requireValidNotes(newAdditionalNotes);
    String validTicker = requireValidTicker(newTicker);
    this.investmentCategoryId = validCategoryId;
    this.investmentSubcategoryId = newInvestmentSubcategoryId;
    this.name = validName;
    this.additionalNotes = validNotes;
    this.ticker = validTicker;
    this.segmentId = newSegmentId;
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

  private static String requireValidTicker(String value) {
    if (value != null && value.length() > TextFieldConstraints.MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "ticker must not exceed " + TextFieldConstraints.MAX_NAME_LENGTH + " characters");
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

  /** {@code null} when the product carries no ticker (F026). */
  public String getTicker() {
    return ticker;
  }

  /** {@code null} when the product carries no segment (F026). */
  public UUID getSegmentId() {
    return segmentId;
  }
}
