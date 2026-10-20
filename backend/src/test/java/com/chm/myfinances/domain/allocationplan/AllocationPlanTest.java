package com.chm.myfinances.domain.allocationplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link AllocationPlan} (F026 spec, ADR 0023): a one-row marker
 * aggregate, like {@code Budget} but with no other fields at all. Pure JUnit (ADR 0004).
 */
class AllocationPlanTest {

  @Test
  void createsWithGivenId() {
    UUID id = UUID.randomUUID();

    AllocationPlan plan = AllocationPlan.create(id);

    assertThat(plan.getId()).isEqualTo(id);
  }

  @Test
  void reconstitutePreservesState() {
    UUID id = UUID.randomUUID();

    AllocationPlan plan = AllocationPlan.reconstitute(id);

    assertThat(plan.getId()).isEqualTo(id);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> AllocationPlan.create(null)).isInstanceOf(NullPointerException.class);
  }
}
