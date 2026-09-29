package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import java.util.UUID;

/**
 * Test data builder for {@link InvestmentProduct} (issue #31, B6; F022 pure-taxonomy shape): a
 * valid, in-memory product with sensible defaults that a test overrides only where it cares. A test
 * asserting on {@link InvestmentProduct#create}'s own validation should keep calling {@code
 * InvestmentProduct.create(...)} directly - going through this builder would obscure what's being
 * tested.
 */
public final class InvestmentProductMother {

  private UUID id = UUID.randomUUID();
  private UUID investmentCategoryId = UUID.randomUUID();
  private UUID investmentSubcategoryId = UUID.randomUUID();
  private String name = "Tesouro Selic 2029";
  private String additionalNotes;

  private InvestmentProductMother() {}

  public static InvestmentProductMother product() {
    return new InvestmentProductMother();
  }

  public InvestmentProductMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public InvestmentProductMother withInvestmentCategoryId(UUID investmentCategoryId) {
    this.investmentCategoryId = investmentCategoryId;
    return this;
  }

  public InvestmentProductMother withInvestmentSubcategoryId(UUID investmentSubcategoryId) {
    this.investmentSubcategoryId = investmentSubcategoryId;
    return this;
  }

  public InvestmentProductMother withName(String name) {
    this.name = name;
    return this;
  }

  public InvestmentProductMother withAdditionalNotes(String additionalNotes) {
    this.additionalNotes = additionalNotes;
    return this;
  }

  public InvestmentProduct build() {
    return InvestmentProduct.create(
        id, investmentCategoryId, investmentSubcategoryId, name, additionalNotes);
  }
}
