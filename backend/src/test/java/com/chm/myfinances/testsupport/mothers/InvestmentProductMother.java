package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import java.util.UUID;

/**
 * Test data builder for {@link InvestmentProduct} (issue #31, B6): a valid, in-memory product with
 * sensible defaults that a test overrides only where it cares. A test asserting on {@link
 * InvestmentProduct#create}'s own validation should keep calling {@code
 * InvestmentProduct.create(...)} directly - going through this builder would obscure what's being
 * tested.
 */
public final class InvestmentProductMother {

  private UUID id = UUID.randomUUID();
  private UUID accountId = UUID.randomUUID();
  private UUID investmentCategoryId = UUID.randomUUID();
  private UUID investmentSubcategoryId = UUID.randomUUID();
  private String name = "Tesouro Selic 2029";

  private InvestmentProductMother() {}

  public static InvestmentProductMother product() {
    return new InvestmentProductMother();
  }

  public InvestmentProductMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public InvestmentProductMother withAccountId(UUID accountId) {
    this.accountId = accountId;
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

  public InvestmentProduct build() {
    return InvestmentProduct.create(
        id, accountId, investmentCategoryId, investmentSubcategoryId, name);
  }
}
