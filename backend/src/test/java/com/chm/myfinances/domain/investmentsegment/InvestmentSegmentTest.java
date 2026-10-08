package com.chm.myfinances.domain.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentSegment} (F026 spec, ADR 0023): a flat,
 * user-editable taxonomy entry, same shape as {@code InvestmentCategory}. Pure JUnit (ADR 0004),
 * written before the class itself.
 */
class InvestmentSegmentTest {

  @Test
  void createsWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    InvestmentSegment segment = InvestmentSegment.create(id, "Shoppings");

    assertThat(segment.getId()).isEqualTo(id);
    assertThat(segment.getName()).isEqualTo("Shoppings");
  }

  @Test
  void reconstitutePreservesState() {
    UUID id = UUID.randomUUID();

    InvestmentSegment segment = InvestmentSegment.reconstitute(id, "Logistica");

    assertThat(segment.getId()).isEqualTo(id);
    assertThat(segment.getName()).isEqualTo("Logistica");
  }

  @Test
  void renameChangesTheName() {
    InvestmentSegment segment = InvestmentSegment.create(UUID.randomUUID(), "Papel");

    segment.rename("Lajes Corporativas");

    assertThat(segment.getName()).isEqualTo("Lajes Corporativas");
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> InvestmentSegment.create(null, "Papel"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> InvestmentSegment.create(UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullName() {
    assertThatThrownBy(() -> InvestmentSegment.create(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsBlankNameAndKeepsTheOldOne() {
    InvestmentSegment segment = InvestmentSegment.create(UUID.randomUUID(), "Papel");

    assertThatThrownBy(() -> segment.rename(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(segment.getName()).isEqualTo("Papel");
  }

  @Test
  void acceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(InvestmentSegment.create(UUID.randomUUID(), atMax).getName()).isEqualTo(atMax);
    assertThatThrownBy(() -> InvestmentSegment.create(UUID.randomUUID(), overMax))
        .isInstanceOf(IllegalArgumentException.class);
    InvestmentSegment segment = InvestmentSegment.create(UUID.randomUUID(), "Papel");
    assertThatThrownBy(() -> segment.rename(overMax)).isInstanceOf(IllegalArgumentException.class);
  }
}
