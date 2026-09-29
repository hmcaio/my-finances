package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import java.util.UUID;

/**
 * API representation of an {@link InvestmentProduct} (F022 spec): pure taxonomy. {@code accountId}
 * and {@code hasHistory} moved to the holding response - a product no longer belongs to a single
 * account. {@code closed} is F023's derived, not-stored status (every holding closed, or none at
 * all) - the same pattern as a holding's own {@code closed}/{@code needsSnapshot} - so the global
 * product list can show each row's status without a second per-row call.
 */
public record InvestmentProductResponse(
    UUID id,
    UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    String name,
    String additionalNotes,
    boolean closed) {

  public static InvestmentProductResponse from(InvestmentProduct product, boolean closed) {
    return new InvestmentProductResponse(
        product.getId(),
        product.getInvestmentCategoryId(),
        product.getInvestmentSubcategoryId(),
        product.getName(),
        product.getAdditionalNotes(),
        closed);
  }
}
