package com.chm.myfinances.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain-level unit tests for {@link GoalAllocation} (F039, ADR 0034), written first. */
class GoalAllocationTest {

  private static GoalAllocation allocation(String percentage) {
    return GoalAllocation.create(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(percentage));
  }

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    UUID goalId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    GoalAllocation allocation = GoalAllocation.create(id, goalId, productId, new BigDecimal("60"));

    assertThat(allocation.getId()).isEqualTo(id);
    assertThat(allocation.getGoalId()).isEqualTo(goalId);
    assertThat(allocation.getProductId()).isEqualTo(productId);
    assertThat(allocation.getPercentage()).isEqualTo(new BigDecimal("60.00"));
  }

  @Test
  void rejectsAZeroPercentage() {
    assertThatThrownBy(() -> allocation("0")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsANegativePercentage() {
    assertThatThrownBy(() -> allocation("-1")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAPercentageOverOneHundred() {
    assertThatThrownBy(() -> allocation("100.01")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMoreThanTwoDecimals() {
    assertThatThrownBy(() -> allocation("10.001")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsTheBoundsOneHundredAndOneCent() {
    assertThat(allocation("100").getPercentage()).isEqualByComparingTo("100");
    assertThat(allocation("0.01").getPercentage()).isEqualByComparingTo("0.01");
  }

  @Test
  void rejectsNullFields() {
    assertThatThrownBy(
            () ->
                GoalAllocation.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> GoalAllocation.create(UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.TEN))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> GoalAllocation.create(UUID.randomUUID(), null, UUID.randomUUID(), BigDecimal.TEN))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void auditSnapshotCarriesTheProductAndPercentage() {
    GoalAllocation allocation = allocation("25");

    assertThat(allocation.toAuditSnapshot())
        .containsEntry("productId", allocation.getProductId().toString())
        .containsEntry("percentage", new BigDecimal("25.00"));
  }
}
