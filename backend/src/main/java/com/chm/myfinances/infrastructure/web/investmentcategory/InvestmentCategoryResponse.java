package com.chm.myfinances.infrastructure.web.investmentcategory;

import com.chm.myfinances.application.investmentcategory.InvestmentCategoryWithSubcategories;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import java.util.List;
import java.util.UUID;

/**
 * API representation of an investment category with its sub-categories nested (F008 spec): one
 * small call feeds the settings screen and every product picker.
 */
public record InvestmentCategoryResponse(
    UUID id, String name, List<InvestmentSubcategoryEntry> subcategories) {

  /** A sub-category as nested under its category: id and name only. */
  public record InvestmentSubcategoryEntry(UUID id, String name) {

    static InvestmentSubcategoryEntry from(InvestmentSubcategory subcategory) {
      return new InvestmentSubcategoryEntry(subcategory.getId(), subcategory.getName());
    }
  }

  public static InvestmentCategoryResponse from(InvestmentCategoryWithSubcategories nested) {
    return new InvestmentCategoryResponse(
        nested.category().getId(),
        nested.category().getName(),
        nested.subcategories().stream().map(InvestmentSubcategoryEntry::from).toList());
  }
}
