package com.chm.myfinances.domain.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link BudgetVersion} (PRD S5.6, F006 spec), written before {@link
 * BudgetVersion} itself (ADR 0004). Covers plan.md's three domain rules: a version's own field
 * invariants, the same-month "replace, don't duplicate" cap edit, and - the bulk of this file -
 * {@link BudgetVersion#resolveEffective}'s version-resolution rule ("the version with the latest
 * {@code effectiveFrom} that is {@code <=} the month in question", F006 spec).
 */
class BudgetVersionTest {

  private static final UUID BUDGET_ID = UUID.randomUUID();

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    YearMonth effectiveFrom = YearMonth.of(2026, 3);

    BudgetVersion version =
        BudgetVersion.create(id, BUDGET_ID, new BigDecimal("500.00"), effectiveFrom);

    assertThat(version.getId()).isEqualTo(id);
    assertThat(version.getBudgetId()).isEqualTo(BUDGET_ID);
    assertThat(version.getMonthlyCap()).isEqualByComparingTo("500.00");
    assertThat(version.getEffectiveFrom()).isEqualTo(effectiveFrom);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> BudgetVersion.create(null, BUDGET_ID, BigDecimal.TEN, YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullBudgetId() {
    assertThatThrownBy(
            () -> BudgetVersion.create(UUID.randomUUID(), null, BigDecimal.TEN, YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullEffectiveFrom() {
    assertThatThrownBy(
            () -> BudgetVersion.create(UUID.randomUUID(), BUDGET_ID, BigDecimal.TEN, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullMonthlyCap() {
    assertThatThrownBy(
            () -> BudgetVersion.create(UUID.randomUUID(), BUDGET_ID, null, YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsZeroMonthlyCap() {
    assertThatThrownBy(
            () ->
                BudgetVersion.create(
                    UUID.randomUUID(), BUDGET_ID, BigDecimal.ZERO, YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNegativeMonthlyCap() {
    assertThatThrownBy(
            () ->
                BudgetVersion.create(
                    UUID.randomUUID(), BUDGET_ID, new BigDecimal("-1.00"), YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateCapReplacesTheCapInPlace() {
    BudgetVersion version =
        BudgetVersion.create(
            UUID.randomUUID(), BUDGET_ID, new BigDecimal("500.00"), YearMonth.of(2026, 3));

    version.updateCap(new BigDecimal("600.00"));

    assertThat(version.getMonthlyCap()).isEqualByComparingTo("600.00");
    // effectiveFrom/budgetId/id are untouched - only the cap itself is corrected in place.
    assertThat(version.getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 3));
  }

  @Test
  void updateCapRejectsNonPositiveValue() {
    BudgetVersion version =
        BudgetVersion.create(UUID.randomUUID(), BUDGET_ID, BigDecimal.TEN, YearMonth.now());

    assertThatThrownBy(() -> version.updateCap(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveEffectivePicksTheLatestVersionAtOrBeforeTheTargetMonth() {
    BudgetVersion january = version(YearMonth.of(2026, 1), "100.00");
    BudgetVersion march = version(YearMonth.of(2026, 3), "200.00");
    BudgetVersion june = version(YearMonth.of(2026, 6), "300.00");

    Optional<BudgetVersion> resolved =
        BudgetVersion.resolveEffective(List.of(january, march, june), YearMonth.of(2026, 5));

    assertThat(resolved).contains(march);
  }

  @Test
  void resolveEffectiveMatchesExactlyOnTheEffectiveMonthItself() {
    BudgetVersion march = version(YearMonth.of(2026, 3), "200.00");

    Optional<BudgetVersion> resolved =
        BudgetVersion.resolveEffective(List.of(march), YearMonth.of(2026, 3));

    assertThat(resolved).contains(march);
  }

  @Test
  void resolveEffectiveIgnoresVersionsAfterTheTargetMonth() {
    BudgetVersion future = version(YearMonth.of(2026, 12), "999.00");

    Optional<BudgetVersion> resolved =
        BudgetVersion.resolveEffective(List.of(future), YearMonth.of(2026, 1));

    assertThat(resolved).isEmpty();
  }

  @Test
  void resolveEffectiveReturnsEmptyWhenNoVersionsGiven() {
    assertThat(BudgetVersion.resolveEffective(List.of(), YearMonth.of(2026, 1))).isEmpty();
  }

  @Test
  void resolveEffectiveIsUnaffectedByListOrder() {
    BudgetVersion january = version(YearMonth.of(2026, 1), "100.00");
    BudgetVersion march = version(YearMonth.of(2026, 3), "200.00");

    Optional<BudgetVersion> resolved =
        BudgetVersion.resolveEffective(List.of(march, january), YearMonth.of(2026, 5));

    assertThat(resolved).contains(march);
  }

  private static BudgetVersion version(YearMonth effectiveFrom, String cap) {
    return BudgetVersion.create(UUID.randomUUID(), BUDGET_ID, new BigDecimal(cap), effectiveFrom);
  }
}
