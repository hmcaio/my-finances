package com.chm.myfinances.infrastructure.web.investmentsubcategory;

import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import java.util.UUID;

/** API representation of an {@link InvestmentSubcategory}. */
public record InvestmentSubcategoryResponse(UUID id, UUID investmentCategoryId, String name) {

  public static InvestmentSubcategoryResponse from(InvestmentSubcategory subcategory) {
    return new InvestmentSubcategoryResponse(
        subcategory.getId(), subcategory.getInvestmentCategoryId(), subcategory.getName());
  }
}
