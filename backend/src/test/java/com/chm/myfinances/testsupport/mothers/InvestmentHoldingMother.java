package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import java.util.UUID;

/**
 * Test data builder for {@link InvestmentHolding} (F022, ADR 0020; same spirit as {@link
 * InvestmentProductMother}): a valid, in-memory holding with sensible defaults that a test
 * overrides only where it cares.
 */
public final class InvestmentHoldingMother {

  private UUID id = UUID.randomUUID();
  private UUID productId = UUID.randomUUID();
  private UUID accountId = UUID.randomUUID();
  private String additionalNotes;

  private InvestmentHoldingMother() {}

  public static InvestmentHoldingMother holding() {
    return new InvestmentHoldingMother();
  }

  public InvestmentHoldingMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public InvestmentHoldingMother withProductId(UUID productId) {
    this.productId = productId;
    return this;
  }

  public InvestmentHoldingMother withAccountId(UUID accountId) {
    this.accountId = accountId;
    return this;
  }

  public InvestmentHoldingMother withAdditionalNotes(String additionalNotes) {
    this.additionalNotes = additionalNotes;
    return this;
  }

  public InvestmentHolding build() {
    return InvestmentHolding.create(id, productId, accountId, additionalNotes);
  }
}
