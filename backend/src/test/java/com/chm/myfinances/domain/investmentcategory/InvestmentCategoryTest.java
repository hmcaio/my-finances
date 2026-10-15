package com.chm.myfinances.domain.investmentcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentCategory} (F008 spec): a flat, fixed top-level
 * taxonomy entry, same shape as F002's {@code Category}. Pure JUnit (ADR 0004).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename} were deleted — every row is now fixed,
 * migration-seeded content, reconstituted from persistence only. These tests now exercise {@link
 * InvestmentCategory#reconstitute} directly for the same validation {@code create} used to cover.
 */
class InvestmentCategoryTest {

  @Test
  void reconstitutesWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    InvestmentCategory category = InvestmentCategory.reconstitute(id, "Fixed Income");

    assertThat(category.getId()).isEqualTo(id);
    assertThat(category.getName()).isEqualTo("Fixed Income");
  }

  @Test
  void reconstituteRejectsNullId() {
    assertThatThrownBy(() -> InvestmentCategory.reconstitute(null, "Funds"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void reconstituteRejectsBlankName() {
    assertThatThrownBy(() -> InvestmentCategory.reconstitute(UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRejectsNullName() {
    assertThatThrownBy(() -> InvestmentCategory.reconstitute(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteAcceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(InvestmentCategory.reconstitute(UUID.randomUUID(), atMax).getName())
        .isEqualTo(atMax);
    assertThatThrownBy(() -> InvestmentCategory.reconstitute(UUID.randomUUID(), overMax))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
