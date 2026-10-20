package com.chm.myfinances.domain.allocationplan;

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
 * AllocationPlanVersion aggregate (F026 spec, ADR 0023): a full set of target percentages,
 * effective from a given month onward, for the one {@link AllocationPlan}. Versions exactly like
 * {@code BudgetVersion} (ADR 0002): forward-only history, with the one same-month-correction
 * carve-out {@link #updateEntries} allows (parallel to {@code BudgetVersion.updateCap}). No
 * tombstone - stopping the plan isn't a modeled concept for v1.
 *
 * <p>Invariants (checked on every {@code create}/{@code reconstitute}/{@link #updateEntries}): at
 * least one entry, no duplicate {@code investmentProductId} within the version, and the entries'
 * {@code targetPercentage}s sum to exactly {@code 100} (each entry's own positivity is {@link
 * AllocationPlanEntry}'s invariant). That every entry's product is classified under "REITs (FIIs)"
 * is an {@code AllocationPlanService} concern - this class holds products by id only.
 */
public final class AllocationPlanVersion {

  private static final BigDecimal FULL_ALLOCATION = new BigDecimal("100");

  private final UUID id;
  private final UUID planId;
  private List<AllocationPlanEntry> entries;
  private final YearMonth effectiveFrom;

  private AllocationPlanVersion(
      UUID id, UUID planId, List<AllocationPlanEntry> entries, YearMonth effectiveFrom) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.planId = Objects.requireNonNull(planId, "planId must not be null");
    this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    this.entries = requireValidEntries(entries);
  }

  /** Creates a brand-new version. {@code id} must come from the {@code IdGenerator} port. */
  public static AllocationPlanVersion create(
      UUID id, UUID planId, List<AllocationPlanEntry> entries, YearMonth effectiveFrom) {
    return new AllocationPlanVersion(id, planId, entries, effectiveFrom);
  }

  /** Rebuilds a version from already-validated persisted state. */
  public static AllocationPlanVersion reconstitute(
      UUID id, UUID planId, List<AllocationPlanEntry> entries, YearMonth effectiveFrom) {
    return new AllocationPlanVersion(id, planId, entries, effectiveFrom);
  }

  /**
   * Replaces this version's entries in place - the one mutation this otherwise-immutable-history
   * aggregate allows (F026 spec's same-month correction, mirroring {@code
   * BudgetVersion.updateCap}). {@code planId}/{@code effectiveFrom} never change. A rejected update
   * leaves the version untouched.
   */
  public void updateEntries(List<AllocationPlanEntry> newEntries) {
    this.entries = requireValidEntries(newEntries);
  }

  private static List<AllocationPlanEntry> requireValidEntries(List<AllocationPlanEntry> value) {
    List<AllocationPlanEntry> copy =
        List.copyOf(Objects.requireNonNull(value, "entries must not be null"));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("entries must not be empty");
    }
    long distinctProducts =
        copy.stream().map(AllocationPlanEntry::investmentProductId).distinct().count();
    if (distinctProducts != copy.size()) {
      throw new IllegalArgumentException("entries must not reference the same product twice");
    }
    BigDecimal sum =
        copy.stream()
            .map(AllocationPlanEntry::targetPercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (sum.compareTo(FULL_ALLOCATION) != 0) {
      throw new IllegalArgumentException("entries must sum to exactly 100, got " + sum);
    }
    return copy;
  }

  /**
   * Resolves whichever of {@code versions} is effective for {@code targetMonth}: the version with
   * the latest {@code effectiveFrom} that is {@code <=} {@code targetMonth} (mirrors {@code
   * BudgetVersion.resolveEffective}). Pure domain logic, independent of list order.
   */
  public static Optional<AllocationPlanVersion> resolveEffective(
      List<AllocationPlanVersion> versions, YearMonth targetMonth) {
    Objects.requireNonNull(targetMonth, "targetMonth must not be null");
    return versions.stream()
        .filter(version -> !version.effectiveFrom.isAfter(targetMonth))
        .max(Comparator.comparing(AllocationPlanVersion::getEffectiveFrom));
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlanId() {
    return planId;
  }

  public List<AllocationPlanEntry> getEntries() {
    return entries;
  }

  public YearMonth getEffectiveFrom() {
    return effectiveFrom;
  }

  /**
   * Flat snapshot of every persisted field (F025 spec, ADR 0022): {@code entries} flattens to a
   * list of {@code {investmentProductId, targetPercentage}} maps, same "a collection field is a
   * list of flat maps" convention as {@code Transfer.toAuditSnapshot}'s trade confirmation lines.
   */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("planId", planId.toString());
    snapshot.put("effectiveFrom", effectiveFrom.toString());
    snapshot.put(
        "entries",
        entries.stream()
            .map(
                entry -> {
                  Map<String, Object> entrySnapshot = new LinkedHashMap<>();
                  entrySnapshot.put("investmentProductId", entry.investmentProductId().toString());
                  entrySnapshot.put("targetPercentage", entry.targetPercentage());
                  return entrySnapshot;
                })
            .toList());
    return snapshot;
  }
}
