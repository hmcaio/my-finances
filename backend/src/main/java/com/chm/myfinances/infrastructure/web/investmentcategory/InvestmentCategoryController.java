package com.chm.myfinances.infrastructure.web.investmentcategory;

import com.chm.myfinances.application.investmentcategory.InvestmentCategoryService;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentCategory} (F008 spec). Every response nests the category's
 * sub-categories, so the settings screen and the product pickers need one shape only.
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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-blank, size",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-category-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentCategoryResponse create(
      @Valid @RequestBody CreateInvestmentCategoryRequest request) {
    InvestmentCategory category = categoryService.create(request.name());
    return InvestmentCategoryResponse.from(categoryService.findWithSubcategories(category.getId()));
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-blank, size",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-category-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-category-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentCategoryResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvestmentCategoryRequest request) {
    categoryService.rename(id, request.name());
    return InvestmentCategoryResponse.from(categoryService.findWithSubcategories(id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-category-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-category-in-use",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    categoryService.delete(id);
  }
}
