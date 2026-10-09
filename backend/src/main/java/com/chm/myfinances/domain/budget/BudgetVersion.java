package com.chm.myfinances.domain.budget;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * BudgetVersion aggregate (PRD S5.6, F006 spec): a single monthly cap, effective from a given month
 * onward, for one {@link Budget}. Editing a cap creates a new version rather than mutating an
 * existing one - forward-only, same versioning spirit as F007's future {@code
 * RecurringTemplateVersion} - with exactly one carve-out: {@link #updateCap} corrects the cap for a
 * month that already has a version (F006 spec: "editing the cap for a month that already has a
 * version for that exact month replaces that version rather than creating a duplicate ... the one
 * mutation allowed, not rewriting history for an earlier month"). Whether an incoming edit creates
 * a new version or replaces this one is an application-layer decision ({@code BudgetService}, which
 * looks up whether a version already exists for the target month) - this class only knows how to do
 * the replacement once told to.
 *
 * <p>{@code effectiveFrom} is a {@link YearMonth} - the domain never deals in the "stored as the
 * first day of the month" persistence detail (F006 spec); that conversion happens at the
 * persistence adapter boundary (ADR 0004).
 */
public final class BudgetVersion {

  private final UUID id;
  private final UUID budgetId;
  private BigDecimal monthlyCap;
  private final YearMonth effectiveFrom;

  private BudgetVersion(UUID id, UUID budgetId, BigDecimal monthlyCap, YearMonth effectiveFrom) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.budgetId = Objects.requireNonNull(budgetId, "budgetId must not be null");
    this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    this.monthlyCap = monthlyCap == null ? null : requireValidCap(monthlyCap);
  }

  /** Creates a brand-new BudgetVersion. {@code id} must come from the {@code IdGenerator} port. */
  public static BudgetVersion create(
      UUID id, UUID budgetId, BigDecimal monthlyCap, YearMonth effectiveFrom) {
    return new BudgetVersion(
        id,
        budgetId,
        Objects.requireNonNull(monthlyCap, "monthlyCap must not be null"),
        effectiveFrom);
  }

  /**
   * Creates a tombstone version (issue #61): "no budget from {@code effectiveFrom} onward". It has
   * a {@code null} cap and stays in the history like any other version, so earlier months keep
   * their own cap and a later {@code setCap} resumes the budget as an ordinary new version.
   */
  public static BudgetVersion tombstone(UUID id, UUID budgetId, YearMonth effectiveFrom) {
    return new BudgetVersion(id, budgetId, null, effectiveFrom);
  }

  /**
   * Rebuilds a BudgetVersion from already-validated persisted state; a {@code null} cap is a
   * tombstone.
   */
  public static BudgetVersion reconstitute(
      UUID id, UUID budgetId, BigDecimal monthlyCap, YearMonth effectiveFrom) {
    return new BudgetVersion(id, budgetId, monthlyCap, effectiveFrom);
  }

  /** {@code true} when this version means "no budget from its month onward" (no cap). */
  public boolean isTombstone() {
    return monthlyCap == null;
  }

  /**
   * Turns this version into a tombstone in place - the same-month correction {@link #updateCap}
   * performs, for stopping the budget from the very month this version is effective.
   */
  public void stop() {
    this.monthlyCap = null;
  }

  /**
   * Replaces this version's cap in place - the one mutation this otherwise-immutable-history
   * aggregate allows (F006 spec's same-month correction). {@code budgetId}/{@code effectiveFrom}
   * never change.
   */
  public void updateCap(BigDecimal monthlyCap) {
    this.monthlyCap = requireValidCap(monthlyCap);
  }

  private static BigDecimal requireValidCap(BigDecimal value) {
    Objects.requireNonNull(value, "monthlyCap must not be null");
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("monthlyCap must be positive");
    }
    return value;
  }

  /**
   * Resolves whichever of {@code versions} is effective for {@code targetMonth}: the version with
   * the latest {@code effectiveFrom} that is {@code <=} {@code targetMonth} (F006 spec/PRD S5.6 -
   * "so past months' budget-vs-actual stays accurate even after the cap is later changed"). Pure
   * domain logic, independent of list order and of how the candidates were loaded - callers (the
   * application layer) pass in whatever versions they've already fetched for one {@code budgetId}.
   */
  public static Optional<BudgetVersion> resolveEffective(
      List<BudgetVersion> versions, YearMonth targetMonth) {
    Objects.requireNonNull(targetMonth, "targetMonth must not be null");
    return versions.stream()
        .filter(version -> !version.effectiveFrom.isAfter(targetMonth))
        .max(Comparator.comparing(BudgetVersion::getEffectiveFrom));
  }

  public UUID getId() {
    return id;
  }

  public UUID getBudgetId() {
    return budgetId;
  }

  /** The cap, or {@code null} for a tombstone ({@link #isTombstone()}). */
  public BigDecimal getMonthlyCap() {
    return monthlyCap;
  }

  public YearMonth getEffectiveFrom() {
    return effectiveFrom;
  }

  /**
   * Flat snapshot of every persisted field (F025 spec, ADR 0022). Used to diff the previous vs. new
   * effective version of this {@code Budget} - the logical entity the audit entry is recorded
   * against (spec's Decisions: "versioned entities log as `UPDATE` on the logical entity").
   */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("budgetId", budgetId.toString());
    snapshot.put("monthlyCap", monthlyCap);
    snapshot.put("effectiveFrom", effectiveFrom.toString());
    return snapshot;
  }
}
