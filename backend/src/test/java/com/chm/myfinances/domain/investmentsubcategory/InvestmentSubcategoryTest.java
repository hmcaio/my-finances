package com.chm.myfinances.domain.investmentsubcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentSubcategory} (F008 spec): a second-level taxonomy
 * entry whose parent category is required and fixed at creation. Pure JUnit (ADR 0004), written
 * before the class itself.
 */
class InvestmentSubcategoryTest {

  private static final UUID CATEGORY_ID = UUID.randomUUID();

  @Test
  void createsWithGivenIdParentAndName() {
    UUID id = UUID.randomUUID();

    InvestmentSubcategory subcategory = InvestmentSubcategory.create(id, CATEGORY_ID, "CDB");

    assertThat(subcategory.getId()).isEqualTo(id);
    assertThat(subcategory.getInvestmentCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(subcategory.getName()).isEqualTo("CDB");
  }

  @Test
  void renameChangesTheNameButNeverTheParent() {
    // No mutator exists for the parent (structural, like Account.openingBalance): re-parenting
    // would silently reclassify every product beneath the sub-category (F008 spec).
    InvestmentSubcategory subcategory =
        InvestmentSubcategory.create(UUID.randomUUID(), CATEGORY_ID, "CDB");

    subcategory.rename("CDB / RDB");

    assertThat(subcategory.getName()).isEqualTo("CDB / RDB");
    assertThat(subcategory.getInvestmentCategoryId()).isEqualTo(CATEGORY_ID);
  }

  @Test
  void createRejectsANullParent() {
    assertThatThrownBy(() -> InvestmentSubcategory.create(UUID.randomUUID(), null, "CDB"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void reconstituteRejectsANullParent() {
    assertThatThrownBy(() -> InvestmentSubcategory.reconstitute(UUID.randomUUID(), null, "CDB"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> InvestmentSubcategory.create(null, CATEGORY_ID, "CDB"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> InvestmentSubcategory.create(UUID.randomUUID(), CATEGORY_ID, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsBlankNameAndKeepsTheOldOne() {
    InvestmentSubcategory subcategory =
        InvestmentSubcategory.create(UUID.randomUUID(), CATEGORY_ID, "CDB");

    assertThatThrownBy(() -> subcategory.rename(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(subcategory.getName()).isEqualTo("CDB");
  }

  @Test
  void acceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(InvestmentSubcategory.create(UUID.randomUUID(), CATEGORY_ID, atMax).getName())
        .isEqualTo(atMax);
    assertThatThrownBy(() -> InvestmentSubcategory.create(UUID.randomUUID(), CATEGORY_ID, overMax))
        .isInstanceOf(IllegalArgumentException.class);
    InvestmentSubcategory subcategory =
        InvestmentSubcategory.create(UUID.randomUUID(), CATEGORY_ID, "CDB");
    assertThatThrownBy(() -> subcategory.rename(overMax))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
