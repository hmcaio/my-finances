package com.chm.myfinances.domain.investmentsnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment snapshot aggregate (PRD S5.8, F009 spec, rekeyed by F022/ADR 0020): the manually
 * entered value of one {@code InvestmentHolding} on one date - the sole source of a holding's
 * current value everywhere else (account balance, allocation, net worth). One per holding per date;
 * the service upserts on {@code (holdingId, date)}.
 *
 * <p>{@code balance} is {@code >= 0}, not {@code > 0} like every other amount in the codebase:
 * {@code 0} is the legitimate value of a liquidated position (and what lets the holding be closed).
 * The holding is held by id only - that it exists is checked in {@code InvestmentSnapshotService}.
 */
public final class InvestmentSnapshot {

  private final UUID id;
  private final UUID holdingId;
  private LocalDate date;
  private BigDecimal balance;

  private InvestmentSnapshot(UUID id, UUID holdingId, LocalDate date, BigDecimal balance) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.holdingId = Objects.requireNonNull(holdingId, "holdingId must not be null");
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.balance = requireValidBalance(balance);
  }

  /** Creates a brand-new snapshot. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentSnapshot create(
      UUID id, UUID holdingId, LocalDate date, BigDecimal balance) {
    return new InvestmentSnapshot(id, holdingId, date, balance);
  }

  /** Rebuilds a snapshot from already-validated persisted state. */
  public static InvestmentSnapshot reconstitute(
      UUID id, UUID holdingId, LocalDate date, BigDecimal balance) {
    return new InvestmentSnapshot(id, holdingId, date, balance);
  }

  /** Replaces the balance (a second entry for the same holding and day). */
  public void replaceBalance(BigDecimal newBalance) {
    this.balance = requireValidBalance(newBalance);
  }

  /** Moves the snapshot to another date (a wrongly dated entry). */
  public void moveTo(LocalDate newDate) {
    this.date = Objects.requireNonNull(newDate, "date must not be null");
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

  public UUID getHoldingId() {
    return holdingId;
  }

  public LocalDate getDate() {
    return date;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  /** Flat snapshot of every persisted field (F025 spec, ADR 0022). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("holdingId", holdingId.toString());
    snapshot.put("date", date.toString());
    snapshot.put("balance", balance);
    return snapshot;
  }
}
