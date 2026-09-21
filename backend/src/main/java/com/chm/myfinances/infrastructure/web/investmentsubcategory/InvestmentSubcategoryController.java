package com.chm.myfinances.infrastructure.web.investmentsubcategory;

import com.chm.myfinances.application.investmentsubcategory.InvestmentSubcategoryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentSubcategory} (F008 spec). No list endpoint: sub-categories are read
 * nested under their category ({@code GET /api/investment-categories}).
 */
@RestController
@RequestMapping("/api/investment-subcategories")
public class InvestmentSubcategoryController {

  private final InvestmentSubcategoryService subcategoryService;

  public InvestmentSubcategoryController(InvestmentSubcategoryService subcategoryService) {
    this.subcategoryService = subcategoryService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InvestmentSubcategoryResponse create(
      @Valid @RequestBody CreateInvestmentSubcategoryRequest request) {
    return InvestmentSubcategoryResponse.from(
        subcategoryService.create(request.investmentCategoryId(), request.name()));
  }

  @PatchMapping("/{id}")
  public InvestmentSubcategoryResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvestmentSubcategoryRequest request) {
    return InvestmentSubcategoryResponse.from(subcategoryService.rename(id, request.name()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    subcategoryService.delete(id);
  }
}
