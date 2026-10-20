package com.chm.myfinances.infrastructure.web.category;

import com.chm.myfinances.application.category.CategoryService;
import com.chm.myfinances.domain.category.Category;
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

/** REST API for {@code Category} (F002 spec). */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public List<CategoryResponse> list() {
    return categoryService.findAll().stream().map(CategoryResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-blank, size, not-null",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:category-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
    Category category = categoryService.create(request.name(), request.type());
    return CategoryResponse.from(category);
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
      description = "type: urn:my-finances:error:category-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:category-name-already-exists, "
              + "urn:my-finances:error:built-in-category, urn:my-finances:error:fuel-category, "
              + "urn:my-finances:error:dividend-category",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public CategoryResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
    Category category = categoryService.rename(id, request.name());
    return CategoryResponse.from(category);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:category-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:built-in-category, "
              + "urn:my-finances:error:fuel-category, urn:my-finances:error:dividend-category, "
              + "urn:my-finances:error:category-in-use",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    categoryService.delete(id);
  }
}
