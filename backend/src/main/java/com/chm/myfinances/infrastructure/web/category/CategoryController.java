package com.chm.myfinances.infrastructure.web.category;

import com.chm.myfinances.application.category.CategoryService;
import com.chm.myfinances.domain.category.Category;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
  public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
    Category category = categoryService.create(request.name(), request.type());
    return CategoryResponse.from(category);
  }

  @PatchMapping("/{id}")
  public CategoryResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
    Category category = categoryService.rename(id, request.name());
    return CategoryResponse.from(category);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    categoryService.delete(id);
  }
}
