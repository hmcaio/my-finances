package com.chm.myfinances.domain.allocationplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link AllocationPlanVersion}/{@link AllocationPlanEntry} (F026
 * spec, ADR 0023), written before the classes themselves (ADR 0004). Mirrors {@code
 * BudgetVersionTest}'s shape: field invariants, the same-month "replace in place" edit
 * ({@link AllocationPlanVersion#updateEntries}), and {@link AllocationPlanVersion#resolveEffective}.
 * Entry-specific invariants (no duplicate product, every percentage positive, sum exactly 100) are
 * this version's own, since a budget has only one cap per version and never had to check this.
 */
class AllocationPlanVersionTest {

  private static final UUID PLAN_ID = UUID.randomUUID();

  private static AllocationPlanEntry entry(UUID productId, String percentage) {
    return new AllocationPlanEntry(productId, new BigDecimal(percentage));
  }

  private static List<AllocationPlanEntry> fullEntries() {
    return List.of(entry(UUID.randomUUID(), "60.00"), entry(UUID.randomUUID(), "40.00"));
  }

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    YearMonth effectiveFrom = YearMonth.of(2026, 3);
    List<AllocationPlanEntry> entries = fullEntries();

    AllocationPlanVersion version =
        AllocationPlanVersion.create(id, PLAN_ID, entries, effectiveFrom);

    assertThat(version.getId()).isEqualTo(id);
    assertThat(version.getPlanId()).isEqualTo(PLAN_ID);
    assertThat(version.getEntries()).containsExactlyInAnyOrderElementsOf(entries);
    assertThat(version.getEffectiveFrom()).isEqualTo(effectiveFrom);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(
            () -> AllocationPlanVersion.create(null, PLAN_ID, fullEntries(), YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullPlanId() {
    assertThatThrownBy(
            () ->
                AllocationPlanVersion.create(
                    UUID.randomUUID(), null, fullEntries(), YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullEffectiveFrom() {
    assertThatThrownBy(
            () -> AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, fullEntries(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsEmptyEntries() {
    assertThatThrownBy(
            () ->
                AllocationPlanVersion.create(
                    UUID.randomUUID(), PLAN_ID, List.of(), YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsADuplicateProductWithinTheSameVersion() {
    UUID productId = UUID.randomUUID();
    List<AllocationPlanEntry> entries =
        List.of(entry(productId, "60.00"), entry(productId, "40.00"));

    assertThatThrownBy(
            () -> AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, entries, YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsASumOtherThanExactly100() {
    List<AllocationPlanEntry> tooLow =
        List.of(entry(UUID.randomUUID(), "60.00"), entry(UUID.randomUUID(), "39.00"));
    List<AllocationPlanEntry> tooHigh =
        List.of(entry(UUID.randomUUID(), "60.00"), entry(UUID.randomUUID(), "41.00"));

    assertThatThrownBy(
            () -> AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, tooLow, YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, tooHigh, YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAcceptsASingleEntryAtExactly100() {
    List<AllocationPlanEntry> entries = List.of(entry(UUID.randomUUID(), "100.00"));

    AllocationPlanVersion version =
        AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, entries, YearMonth.now());

    assertThat(version.getEntries()).hasSize(1);
  }

  @Test
  void entryRejectsNonPositivePercentage() {
    assertThatThrownBy(() -> new AllocationPlanEntry(UUID.randomUUID(), BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AllocationPlanEntry(UUID.randomUUID(), new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void entryRejectsNullProductIdOrPercentage() {
    assertThatThrownBy(() -> new AllocationPlanEntry(null, BigDecimal.TEN))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AllocationPlanEntry(UUID.randomUUID(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void updateEntriesReplacesInPlace() {
    AllocationPlanVersion version =
        AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, fullEntries(), YearMonth.of(2026, 3));
    List<AllocationPlanEntry> replacement = List.of(entry(UUID.randomUUID(), "100.00"));

    version.updateEntries(replacement);

    assertThat(version.getEntries()).containsExactlyElementsOf(replacement);
    // id/planId/effectiveFrom are untouched - only the entries are corrected in place.
    assertThat(version.getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 3));
    assertThat(version.getPlanId()).isEqualTo(PLAN_ID);
  }

  @Test
  void updateEntriesStillEnforcesTheSumAndDuplicateInvariants() {
    AllocationPlanVersion version =
        AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, fullEntries(), YearMonth.now());

    assertThatThrownBy(() -> version.updateEntries(List.of(entry(UUID.randomUUID(), "50.00"))))
        .isInstanceOf(IllegalArgumentException.class);
    // The rejected update leaves the version untouched.
    assertThat(version.getEntries()).hasSize(2);
  }

  @Test
  void resolveEffectivePicksTheLatestVersionAtOrBeforeTheTargetMonth() {
    AllocationPlanVersion january = version(YearMonth.of(2026, 1));
    AllocationPlanVersion march = version(YearMonth.of(2026, 3));
    AllocationPlanVersion june = version(YearMonth.of(2026, 6));

    Optional<AllocationPlanVersion> resolved =
        AllocationPlanVersion.resolveEffective(
            List.of(january, march, june), YearMonth.of(2026, 5));

    assertThat(resolved).contains(march);
  }

  @Test
  void resolveEffectiveIgnoresVersionsAfterTheTargetMonth() {
    AllocationPlanVersion future = version(YearMonth.of(2026, 12));

    Optional<AllocationPlanVersion> resolved =
        AllocationPlanVersion.resolveEffective(List.of(future), YearMonth.of(2026, 1));

    assertThat(resolved).isEmpty();
  }

  @Test
  void resolveEffectiveReturnsEmptyWhenNoVersionsGiven() {
    assertThat(AllocationPlanVersion.resolveEffective(List.of(), YearMonth.of(2026, 1)))
        .isEmpty();
  }

  @Test
  void resolveEffectiveIsUnaffectedByListOrder() {
    AllocationPlanVersion january = version(YearMonth.of(2026, 1));
    AllocationPlanVersion march = version(YearMonth.of(2026, 3));

    Optional<AllocationPlanVersion> resolved =
        AllocationPlanVersion.resolveEffective(List.of(march, january), YearMonth.of(2026, 5));

    assertThat(resolved).contains(march);
  }

  private static AllocationPlanVersion version(YearMonth effectiveFrom) {
    return AllocationPlanVersion.create(UUID.randomUUID(), PLAN_ID, fullEntries(), effectiveFrom);
  }
}
