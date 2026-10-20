package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;

/**
 * Optional filter dimensions for {@link InvestmentProductService#findAll(InvestmentProductFilter,
 * org.springframework.data.domain.Pageable)} (F023 spec's {@code GET /api/investment-products?
 * categoryId=&subcategoryId=&accountId=&name=&status=}). {@code categoryId}/{@code subcategoryId}/
 * {@code accountId}/{@code name} are nullable - {@code null} means "no constraint on this
 * dimension"; {@code status} is never null (the controller defaults it to {@link
 * InvestmentProductStatus#OPEN}, and {@link InvestmentProductStatus#ALL} is the "no constraint"
 * value for that one dimension).
 */
public record InvestmentProductFilter(
    UUID categoryId,
    UUID subcategoryId,
    UUID accountId,
    String name,
    InvestmentProductStatus status) {

  public InvestmentProductFilter {
    if (status == null) {
      status = InvestmentProductStatus.OPEN;
    }
  }

  /** No filtering at all - every product matches, regardless of status. */
  public static InvestmentProductFilter none() {
    return new InvestmentProductFilter(null, null, null, null, InvestmentProductStatus.ALL);
  }
}
