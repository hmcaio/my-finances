package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of an {@link InvestmentProduct}. {@code hasHistory} (snapshots or buy/sell
 * transfers, F009) tells the client whether the product can still be hard-deleted or can only be
 * closed; it is on every response, list included, because the list is where the delete action
 * lives.
 */
public record InvestmentProductResponse(
    UUID id,
    UUID accountId,
    UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    String name,
    LocalDate closedDate,
    boolean closed,
    boolean hasHistory) {

  public static InvestmentProductResponse from(InvestmentProduct product, boolean hasHistory) {
    return new InvestmentProductResponse(
        product.getId(),
        product.getAccountId(),
        product.getInvestmentCategoryId(),
        product.getInvestmentSubcategoryId(),
        product.getName(),
        product.getClosedDate(),
        product.isClosed(),
        hasHistory);
  }
}
