package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import java.util.UUID;

/**
 * API representation of an {@link InvestmentProduct} (F022 spec): pure taxonomy. {@code accountId},
 * {@code closedDate} and {@code hasHistory} moved to the holding response - a product no longer
 * belongs to a single account.
 */
public record InvestmentProductResponse(
    UUID id,
    UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    String name,
    String additionalNotes) {

  public static InvestmentProductResponse from(InvestmentProduct product) {
    return new InvestmentProductResponse(
        product.getId(),
        product.getInvestmentCategoryId(),
        product.getInvestmentSubcategoryId(),
        product.getName(),
        product.getAdditionalNotes());
  }
}
