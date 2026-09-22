package com.chm.myfinances.domain.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link InvestmentProduct} (F008 spec). Pure JUnit (ADR 0004), written
 * before the class itself.
 */
class InvestmentProductTest {

  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private static final UUID CATEGORY_ID = UUID.randomUUID();
  private static final UUID SUBCATEGORY_ID = UUID.randomUUID();

  private static InvestmentProduct newProduct() {
    return InvestmentProduct.create(
        UUID.randomUUID(), ACCOUNT_ID, CATEGORY_ID, SUBCATEGORY_ID, "Tesouro Selic 2029");
  }

  @Test
  void createsOpenWithGivenFields() {
    UUID id = UUID.randomUUID();

    InvestmentProduct product =
        InvestmentProduct.create(id, ACCOUNT_ID, CATEGORY_ID, SUBCATEGORY_ID, "Tesouro Selic 2029");

    assertThat(product.getId()).isEqualTo(id);
    assertThat(product.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(product.getInvestmentCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(product.getInvestmentSubcategoryId()).isEqualTo(SUBCATEGORY_ID);
    assertThat(product.getName()).isEqualTo("Tesouro Selic 2029");
    assertThat(product.getClosedDate()).isNull();
    assertThat(product.isClosed()).isFalse();
  }

  @Test
  void subcategoryIsOptional() {
    InvestmentProduct product =
        InvestmentProduct.create(UUID.randomUUID(), ACCOUNT_ID, CATEGORY_ID, null, "Bitcoin");

    assertThat(product.getInvestmentSubcategoryId()).isNull();
  }

  @Test
  void reconstitutePreservesTheClosedDate() {
    LocalDate closed = LocalDate.of(2026, 5, 1);

    InvestmentProduct product =
        InvestmentProduct.reconstitute(
            UUID.randomUUID(), ACCOUNT_ID, CATEGORY_ID, null, "Bitcoin", closed);

    assertThat(product.getClosedDate()).isEqualTo(closed);
    assertThat(product.isClosed()).isTrue();
  }

  @Test
  void closeSetsTodayOnce() {
    InvestmentProduct product = newProduct();

    LocalDate closedDate = LocalDate.now();
    product.close(closedDate);

    assertThat(product.isClosed()).isTrue();
    assertThat(product.getClosedDate()).isEqualTo(closedDate);
  }

  @Test
  void closeThrowsWhenAlreadyClosed() {
    InvestmentProduct product = newProduct();
    product.close(LocalDate.now());

    assertThatThrownBy(() -> product.close(LocalDate.now()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void editReplacesNameClassificationAndAccount() {
    InvestmentProduct product = newProduct();
    UUID otherAccount = UUID.randomUUID();
    UUID otherCategory = UUID.randomUUID();

    product.edit(otherAccount, otherCategory, null, "Tesouro IPCA+ 2035");

    assertThat(product.getAccountId()).isEqualTo(otherAccount);
    assertThat(product.getInvestmentCategoryId()).isEqualTo(otherCategory);
    assertThat(product.getInvestmentSubcategoryId()).isNull();
    assertThat(product.getName()).isEqualTo("Tesouro IPCA+ 2035");
  }

  @Test
  void aRejectedEditLeavesTheProductUntouched() {
    InvestmentProduct product = newProduct();

    assertThatThrownBy(() -> product.edit(UUID.randomUUID(), UUID.randomUUID(), null, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> product.edit(null, CATEGORY_ID, null, "X"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> product.edit(ACCOUNT_ID, null, null, "X"))
        .isInstanceOf(NullPointerException.class);

    assertThat(product.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(product.getInvestmentCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(product.getName()).isEqualTo("Tesouro Selic 2029");
  }

  @Test
  void editKeepsTheClosedDate() {
    InvestmentProduct product = newProduct();
    product.close(LocalDate.now());

    product.edit(ACCOUNT_ID, CATEGORY_ID, SUBCATEGORY_ID, "Renamed");

    assertThat(product.isClosed()).isTrue();
  }

  @Test
  void createRejectsMissingRequiredReferences() {
    assertThatThrownBy(
            () -> InvestmentProduct.create(null, ACCOUNT_ID, CATEGORY_ID, null, "Bitcoin"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), null, CATEGORY_ID, null, "Bitcoin"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), ACCOUNT_ID, null, null, "Bitcoin"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsBlankOrNullName() {
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), ACCOUNT_ID, CATEGORY_ID, null, "  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> InvestmentProduct.create(UUID.randomUUID(), ACCOUNT_ID, CATEGORY_ID, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsNameAtMaxLengthAndRejectsOverIt() {
    String atMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);
    String overMax = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThat(
            InvestmentProduct.create(UUID.randomUUID(), ACCOUNT_ID, CATEGORY_ID, null, atMax)
                .getName())
        .isEqualTo(atMax);
    assertThatThrownBy(
            () ->
                InvestmentProduct.create(UUID.randomUUID(), ACCOUNT_ID, CATEGORY_ID, null, overMax))
        .isInstanceOf(IllegalArgumentException.class);
    InvestmentProduct product = newProduct();
    assertThatThrownBy(() -> product.edit(ACCOUNT_ID, CATEGORY_ID, null, overMax))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
