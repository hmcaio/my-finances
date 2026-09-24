package com.chm.myfinances.domain.investmentsnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment snapshot aggregate (PRD S5.8, F009 spec): the manually entered value of one {@code
 * InvestmentProduct} on one date - the sole source of a product's current value everywhere else
 * (account balance, allocation, net worth). One per product per date; the service upserts on {@code
 * (productId, date)}.
 *
 * <p>{@code balance} is {@code >= 0}, not {@code > 0} like every other amount in the codebase:
 * {@code 0} is the legitimate value of a liquidated position (and what lets the product be closed).
 * The product is held by id only - that it exists is checked in {@code InvestmentSnapshotService}.
 */
public final class InvestmentSnapshot {

  private final UUID id;
  private final UUID productId;
  private final LocalDate date;
  private BigDecimal balance;

  private InvestmentSnapshot(UUID id, UUID productId, LocalDate date, BigDecimal balance) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.productId = Objects.requireNonNull(productId, "productId must not be null");
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.balance = requireValidBalance(balance);
  }

  /** Creates a brand-new snapshot. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentSnapshot create(
      UUID id, UUID productId, LocalDate date, BigDecimal balance) {
    return new InvestmentSnapshot(id, productId, date, balance);
  }

  /** Rebuilds a snapshot from already-validated persisted state. */
  public static InvestmentSnapshot reconstitute(
      UUID id, UUID productId, LocalDate date, BigDecimal balance) {
    return new InvestmentSnapshot(id, productId, date, balance);
  }

  /** Replaces the balance (a second entry for the same product and day). */
  public void replaceBalance(BigDecimal newBalance) {
    this.balance = requireValidBalance(newBalance);
  }

  private static BigDecimal requireValidBalance(BigDecimal value) {
    Objects.requireNonNull(value, "balance must not be null");
    if (value.signum() < 0) {
      throw new IllegalArgumentException("balance must not be negative");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public LocalDate getDate() {
    return date;
  }

  public BigDecimal getBalance() {
    return balance;
  }
}
