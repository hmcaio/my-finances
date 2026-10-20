package com.chm.myfinances.domain.investmentholding;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment holding aggregate (F022 spec, ADR 0020): the many-to-many link between an {@code
 * InvestmentProduct} and the {@code INVESTMENT} account it's held in. The same instrument bought at
 * two brokers is one product with two holdings, each with its own independent lifecycle - {@code
 * closedDate}, the close guard and {@code needsSnapshot} all live here now, not on the product.
 *
 * <p>{@code productId} and {@code accountId} are immutable after creation - re-pointing either
 * would misattribute existing snapshots/trades to the wrong pair. References are held by id only;
 * this class imports neither {@code domain/investmentproduct} nor {@code domain/account}. That the
 * product and account exist, that the account is an open {@code INVESTMENT} account, and that the
 * pair is unique are all checked in {@code InvestmentHoldingService}.
 */
public final class InvestmentHolding {

  private final UUID id;
  private final UUID productId;
  private final UUID accountId;
  private LocalDate closedDate;
  private String additionalNotes;

  private InvestmentHolding(
      UUID id, UUID productId, UUID accountId, LocalDate closedDate, String additionalNotes) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.productId = Objects.requireNonNull(productId, "productId must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.closedDate = closedDate;
    this.additionalNotes = requireValidNotes(additionalNotes);
  }

  /** Creates a brand-new, open holding. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentHolding create(
      UUID id, UUID productId, UUID accountId, String additionalNotes) {
    return new InvestmentHolding(id, productId, accountId, null, additionalNotes);
  }

  /** Rebuilds a holding from already-validated persisted state. */
  public static InvestmentHolding reconstitute(
      UUID id, UUID productId, UUID accountId, LocalDate closedDate, String additionalNotes) {
    return new InvestmentHolding(id, productId, accountId, closedDate, additionalNotes);
  }

  /** Replaces the notes ({@code null} clears them). */
  public void editNotes(String newAdditionalNotes) {
    this.additionalNotes = requireValidNotes(newAdditionalNotes);
  }

  /**
   * Sets {@code closedDate}. Closing an already-closed holding is rejected - {@code closedDate} is
   * set once, same as {@code Account.close()}/the old {@code InvestmentProduct.close()}. Takes the
   * date as a parameter rather than reading {@code LocalDate.now()} itself (ADR 0004/0005) -
   * "today" comes from {@code InvestmentHoldingService}'s injected {@code Clock}.
   */
  public void close(LocalDate closedDate) {
    if (isClosed()) {
      throw new IllegalStateException("Investment holding is already closed: " + id);
    }
    this.closedDate = Objects.requireNonNull(closedDate, "closedDate must not be null");
  }

  public boolean isClosed() {
    return closedDate != null;
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

  public UUID getProductId() {
    return productId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public LocalDate getClosedDate() {
    return closedDate;
  }

  /** {@code null} when the holding carries no remark. */
  public String getAdditionalNotes() {
    return additionalNotes;
  }

  /** Flat snapshot of every persisted field (F025 spec, ADR 0022). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("productId", productId.toString());
    snapshot.put("accountId", accountId.toString());
    snapshot.put("closedDate", closedDate == null ? null : closedDate.toString());
    snapshot.put("additionalNotes", additionalNotes);
    return snapshot;
  }
}
