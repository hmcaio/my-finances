package com.chm.myfinances.domain.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentSegment} (F026 spec, ADR 0023): a flat, fixed
 * taxonomy entry, same shape as {@code InvestmentCategory}. Pure JUnit (ADR 0004).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename} were deleted — every row is now fixed,
 * migration-seeded content, reconstituted from persistence only. These tests now exercise {@link
 * InvestmentSegment#reconstitute} directly for the same validation {@code create} used to cover.
 */
class InvestmentSegmentTest {

  @Test
  void reconstitutesWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    InvestmentSegment segment = InvestmentSegment.reconstitute(id, "Shoppings");

    assertThat(segment.getId()).isEqualTo(id);
    assertThat(segment.getName()).isEqualTo("Shoppings");
  }

  @Test
  void reconstituteRejectsNullId() {
    assertThatThrownBy(() -> InvestmentSegment.reconstitute(null, "Papel"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void reconstituteRejectsBlankName() {
    assertThatThrownBy(() -> InvestmentSegment.reconstitute(UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRejectsNullName() {
    assertThatThrownBy(() -> InvestmentSegment.reconstitute(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteAcceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(InvestmentSegment.reconstitute(UUID.randomUUID(), atMax).getName()).isEqualTo(atMax);
    assertThatThrownBy(() -> InvestmentSegment.reconstitute(UUID.randomUUID(), overMax))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
