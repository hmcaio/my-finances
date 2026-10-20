package com.chm.myfinances.domain.investmentsubcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentSubcategory} (F008 spec): a second-level taxonomy
 * entry whose parent category is fixed at reconstitution. Pure JUnit (ADR 0004).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename} were deleted — every row is now fixed,
 * migration-seeded content, reconstituted from persistence only. These tests now exercise {@link
 * InvestmentSubcategory#reconstitute} directly for the same validation {@code create} used to
 * cover.
 */
class InvestmentSubcategoryTest {

  private static final UUID CATEGORY_ID = UUID.randomUUID();

  @Test
  void reconstitutesWithGivenIdParentAndName() {
    UUID id = UUID.randomUUID();

    InvestmentSubcategory subcategory = InvestmentSubcategory.reconstitute(id, CATEGORY_ID, "CDB");

    assertThat(subcategory.getId()).isEqualTo(id);
    assertThat(subcategory.getInvestmentCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(subcategory.getName()).isEqualTo("CDB");
  }

  @Test
  void reconstituteRejectsANullParent() {
    assertThatThrownBy(() -> InvestmentSubcategory.reconstitute(UUID.randomUUID(), null, "CDB"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void reconstituteRejectsNullId() {
    assertThatThrownBy(() -> InvestmentSubcategory.reconstitute(null, CATEGORY_ID, "CDB"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void reconstituteRejectsBlankName() {
    assertThatThrownBy(
            () -> InvestmentSubcategory.reconstitute(UUID.randomUUID(), CATEGORY_ID, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteAcceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(InvestmentSubcategory.reconstitute(UUID.randomUUID(), CATEGORY_ID, atMax).getName())
        .isEqualTo(atMax);
    assertThatThrownBy(
            () -> InvestmentSubcategory.reconstitute(UUID.randomUUID(), CATEGORY_ID, overMax))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
