package com.chm.myfinances.application.category;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Category}: create/rename/delete (F002 spec). New ids come from the {@link
 * IdGenerator} port (ADR 0005) — never generated ad hoc here or left to the database.
 *
 * <p>Delete is currently unconditional. F002 plan.md's referenced-by-transaction delete guard (409
 * when a category is in use) is deferred to F004 (Transactions): there is no transaction table to
 * check against yet, so a real guard/test would have nothing to guard against. Add the guard (and
 * its test) here once F004 lands.
 */
@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final IdGenerator idGenerator;

  public CategoryService(CategoryRepository categoryRepository, IdGenerator idGenerator) {
    this.categoryRepository = categoryRepository;
    this.idGenerator = idGenerator;
  }

  public Category create(String name, CategoryType type) {
    Category category = Category.create(idGenerator.newId(), name, type);
    return categoryRepository.save(category);
  }

  public List<Category> findAll() {
    return categoryRepository.findAll();
  }

  public Category rename(UUID id, String newName) {
    Category category =
        categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    category.rename(newName);
    return categoryRepository.save(category);
  }

  public void delete(UUID id) {
    if (!categoryRepository.existsById(id)) {
      throw new CategoryNotFoundException(id);
    }
    categoryRepository.deleteById(id);
  }
}
