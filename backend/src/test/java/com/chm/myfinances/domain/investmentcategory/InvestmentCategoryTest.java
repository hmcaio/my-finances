package com.chm.myfinances.domain.investmentcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentCategory} (F008 spec): a flat, user-editable
 * top-level taxonomy entry, same shape as F002's {@code Category}. Pure JUnit (ADR 0004), written
 * before the class itself.
 */
class InvestmentCategoryTest {

  @Test
  void createsWithGivenIdAndName() {
    UUID id = UUID.randomUUID();

    InvestmentCategory category = InvestmentCategory.create(id, "Fixed Income");

    assertThat(category.getId()).isEqualTo(id);
    assertThat(category.getName()).isEqualTo("Fixed Income");
  }

  @Test
  void reconstitutePreservesState() {
    UUID id = UUID.randomUUID();

    InvestmentCategory category = InvestmentCategory.reconstitute(id, "Crypto");

    assertThat(category.getId()).isEqualTo(id);
    assertThat(category.getName()).isEqualTo("Crypto");
  }

  @Test
  void renameChangesTheName() {
    InvestmentCategory category = InvestmentCategory.create(UUID.randomUUID(), "Funds");

    category.rename("Investment Funds");

    assertThat(category.getName()).isEqualTo("Investment Funds");
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> InvestmentCategory.create(null, "Funds"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> InvestmentCategory.create(UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullName() {
    assertThatThrownBy(() -> InvestmentCategory.create(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsBlankNameAndKeepsTheOldOne() {
    InvestmentCategory category = InvestmentCategory.create(UUID.randomUUID(), "Funds");

    assertThatThrownBy(() -> category.rename(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(category.getName()).isEqualTo("Funds");
  }

  @Test
  void acceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(InvestmentCategory.create(UUID.randomUUID(), atMax).getName()).isEqualTo(atMax);
    assertThatThrownBy(() -> InvestmentCategory.create(UUID.randomUUID(), overMax))
        .isInstanceOf(IllegalArgumentException.class);
    InvestmentCategory category = InvestmentCategory.create(UUID.randomUUID(), "Funds");
    assertThatThrownBy(() -> category.rename(overMax)).isInstanceOf(IllegalArgumentException.class);
  }
}
