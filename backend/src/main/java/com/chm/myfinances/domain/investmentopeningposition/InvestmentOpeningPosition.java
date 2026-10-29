package com.chm.myfinances.domain.investmentopeningposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment opening position aggregate (F038 spec, ADR 0033): a holding's starting quantity and
 * average unit price, recorded without moving cash. It counts like a {@code BUY} line dated {@code
 * asOfDate} in everything derived from trade lines (cotas held, amount contributed, the monthly
 * series, the needs-snapshot flag, the Investments report). At most one per holding.
 *
 * <p>The current value is deliberately not a field: it is an optional input at write time that
 * records the holding's snapshot dated {@code asOfDate} through {@code InvestmentSnapshotService}
 * (ADR 0012: snapshots are the sole source of value).
 *
 * <p>The holding is held by id only - that it exists and is open, that {@code asOfDate} is not in
 * the future (the domain has no clock) and that it does not fall after the holding's earliest trade
 * are all checked in {@code InvestmentOpeningPositionService}. The values are rejected with {@link
 * IllegalArgumentException}, which the service translates to a 400.
 */
public final class InvestmentOpeningPosition {

  /** The scale of the {@code quantity} and {@code unit_price} columns. */
  private static final int SCALE = 8;

  private final UUID id;
  private final UUID holdingId;
  private LocalDate asOfDate;
  private BigDecimal quantity;
  private BigDecimal unitPrice;

  private InvestmentOpeningPosition(
      UUID id, UUID holdingId, LocalDate asOfDate, BigDecimal quantity, BigDecimal unitPrice) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.holdingId = Objects.requireNonNull(holdingId, "holdingId must not be null");
    this.asOfDate = requireDate(asOfDate);
    this.quantity = atColumnScale(requirePositive(quantity, "quantity"));
    this.unitPrice = atColumnScale(requirePositive(unitPrice, "unitPrice"));
  }

  /** Creates a brand-new position. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentOpeningPosition create(
      UUID id, UUID holdingId, LocalDate asOfDate, BigDecimal quantity, BigDecimal unitPrice) {
    return new InvestmentOpeningPosition(id, holdingId, asOfDate, quantity, unitPrice);
  }

  /** Rebuilds a position from already-validated persisted state. */
  public static InvestmentOpeningPosition reconstitute(
      UUID id, UUID holdingId, LocalDate asOfDate, BigDecimal quantity, BigDecimal unitPrice) {
    return new InvestmentOpeningPosition(id, holdingId, asOfDate, quantity, unitPrice);
  }

  /**
   * Replaces the three editable values; the holding never changes. All-or-nothing: an invalid value
   * leaves the position untouched.
   */
  public void edit(LocalDate newAsOfDate, BigDecimal newQuantity, BigDecimal newUnitPrice) {
    LocalDate validDate = requireDate(newAsOfDate);
    BigDecimal validQuantity = atColumnScale(requirePositive(newQuantity, "quantity"));
    BigDecimal validUnitPrice = atColumnScale(requirePositive(newUnitPrice, "unitPrice"));
    this.asOfDate = validDate;
    this.quantity = validQuantity;
    this.unitPrice = validUnitPrice;
  }

  /** {@code quantity x unitPrice} at full precision, never rounded here (same as a buy line). */
  public BigDecimal amountContributed() {
    return quantity.multiply(unitPrice);
  }

  private static LocalDate requireDate(LocalDate value) {
    if (value == null) {
      throw new IllegalArgumentException("asOfDate must not be null");
    }
    return value;
  }

  /**
   * Pads to the column's scale ({@code numeric(19,8)}), never rounds. The value read back from the
   * database always has this scale, so keeping the in-memory value there too makes replacing a
   * position with the same figures a no-op for the audit diff (which compares by {@code equals},
   * where {@code 40} and {@code 40.00000000} differ).
   */
  private static BigDecimal atColumnScale(BigDecimal value) {
    return value.scale() < SCALE ? value.setScale(SCALE) : value;
  }

  private static BigDecimal requirePositive(BigDecimal value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
    if (value.signum() <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getHoldingId() {
    return holdingId;
  }

  public LocalDate getAsOfDate() {
    return asOfDate;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  /** Flat snapshot of every persisted field (F025 spec, ADR 0022). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("holdingId", holdingId.toString());
    snapshot.put("asOfDate", asOfDate.toString());
    snapshot.put("quantity", quantity);
    snapshot.put("unitPrice", unitPrice);
    return snapshot;
  }
}
