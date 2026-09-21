package com.chm.myfinances.application.investmentcategory;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import java.util.List;

/**
 * A category with its sub-categories nested (F008 spec): one small read that feeds the settings
 * screen and every product picker, assembled here because the two aggregates never import each
 * other.
 */
public record InvestmentCategoryWithSubcategories(
    InvestmentCategory category, List<InvestmentSubcategory> subcategories) {}
