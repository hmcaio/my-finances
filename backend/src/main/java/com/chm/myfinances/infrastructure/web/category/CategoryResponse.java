package com.chm.myfinances.infrastructure.web.category;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import java.util.UUID;

/** API representation of a {@link Category}. */
public record CategoryResponse(UUID id, String name, CategoryType type, boolean builtIn) {

  public static CategoryResponse from(Category category) {
    return new CategoryResponse(
        category.getId(), category.getName(), category.getType(), category.isBuiltIn());
  }
}
