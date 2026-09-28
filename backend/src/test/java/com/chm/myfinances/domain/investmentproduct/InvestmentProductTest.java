package com.chm.myfinances.domain.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentProduct}'s F022 shape (ADR 0020): pure taxonomy, no
 * {@code accountId}/{@code closedDate}, an optional {@code additionalNotes}. Pure JUnit (ADR 0004).
 */
class InvestmentProductTest {

  private static final UUID CATEGORY_ID = UUID.randomUUID();
  private static final UUID SUBCATEGORY_ID = UUID.randomUUID();

  private static InvestmentProduct newProduct() {
    return InvestmentProduct.create(
        UUID.randomUUID(), CATEGORY_ID, SUBCATEGORY_ID, "Tesouro Selic 2029", null);
  }

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();

    InvestmentProduct product =
        InvestmentProduct.create(
            id, CATEGORY_ID, SUBCATEGORY_ID, "Tesouro Selic 2029", "matures 2029");

    assertThat(product.getId()).isEqualTo(id);
    assertThat(product.getInvestmentCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(product.getInvestmentSubcategoryId()).isEqualTo(SUBCATEGORY_ID);
    assertThat(product.getName()).isEqualTo("Tesouro Selic 2029");
    assertThat(product.getAdditionalNotes()).isEqualTo("matures 2029");
  }

  @Test
  void subcategoryAndNotesAreOptional() {
    InvestmentProduct product =
        InvestmentProduct.create(UUID.randomUUID(), CATEGORY_ID, null, "Bitcoin", null);

    assertThat(product.getInvestmentSubcategoryId()).isNull();
    assertThat(product.getAdditionalNotes()).isNull();
  }

  @Test
  void reconstituteRebuildsFromPersistedState() {
    InvestmentProduct product =
        InvestmentProduct.reconstitute(UUID.randomUUID(), CATEGORY_ID, null, "Bitcoin", "note");

    assertThat(product.getAdditionalNotes()).isEqualTo("note");
  }

  @Test
  void editReplacesNameClassificationAndNotes() {
    InvestmentProduct product = newProduct();
    UUID otherCategory = UUID.randomUUID();

    product.edit(otherCategory, null, "Tesouro IPCA+ 2035", "tax-exempt");

    assertThat(product.getInvestmentCategoryId()).isEqualTo(otherCategory);
    assertThat(product.getInvestmentSubcategoryId()).isNull();
    assertThat(product.getName()).isEqualTo("Tesouro IPCA+ 2035");
    assertThat(product.getAdditionalNotes()).isEqualTo("tax-exempt");
  }

  @Test
  void aRejectedEditLeavesTheProductUntouched() {
    InvestmentProduct product = newProduct();

    assertThatThrownBy(() -> product.edit(UUID.randomUUID(), null, " ", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> product.edit(null, null, "X", null))
        .isInstanceOf(NullPointerException.class);

    assertThat(product.getInvestmentCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(product.getName()).isEqualTo("Tesouro Selic 2029");
  }

  @Test
  void createRejectsMissingRequiredReferences() {
    assertThatThrownBy(() -> InvestmentProduct.create(null, CATEGORY_ID, null, "Bitcoin", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), null, null, "Bitcoin", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsBlankOrNullName() {
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), CATEGORY_ID, null, "  ", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), CATEGORY_ID, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(
            InvestmentProduct.create(UUID.randomUUID(), CATEGORY_ID, null, atMax, null).getName())
        .isEqualTo(atMax);
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), CATEGORY_ID, null, overMax, null))
        .isInstanceOf(IllegalArgumentException.class);
    InvestmentProduct product = newProduct();
    assertThatThrownBy(() -> product.edit(CATEGORY_ID, null, overMax, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsNotesAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1);

    assertThat(
            InvestmentProduct.create(UUID.randomUUID(), CATEGORY_ID, null, "X", atMax)
                .getAdditionalNotes())
        .isEqualTo(atMax);
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), CATEGORY_ID, null, "X", overMax))
        .isInstanceOf(IllegalArgumentException.class);
    InvestmentProduct product = newProduct();
    assertThatThrownBy(() -> product.edit(CATEGORY_ID, null, "X", overMax))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
