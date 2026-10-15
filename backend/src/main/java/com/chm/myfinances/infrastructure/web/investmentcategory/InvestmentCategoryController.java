package com.chm.myfinances.infrastructure.web.investmentcategory;

import com.chm.myfinances.application.investmentcategory.InvestmentCategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentCategory} (F008 spec). Every response nests the category's
 * sub-categories, so the product pickers need one shape only.
 *
 * <p>F034/ADR 0028: read-only — {@code POST}/{@code PATCH}/{@code DELETE} were removed, every row
 * is fixed, migration-seeded content.
 */
@RestController
@RequestMapping("/api/investment-categories")
public class InvestmentCategoryController {

  private final InvestmentCategoryService categoryService;

  public InvestmentCategoryController(InvestmentCategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public List<InvestmentCategoryResponse> list() {
    return categoryService.findAllWithSubcategories().stream()
        .map(InvestmentCategoryResponse::from)
        .toList();
  }
}
