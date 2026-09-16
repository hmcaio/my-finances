package com.chm.myfinances.application.category;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Category}: create/rename/delete (F002 spec). New ids come from the {@link
 * IdGenerator} port (ADR 0005) — never generated ad hoc here or left to the database.
 *
 * <p>Delete now enforces F002 plan.md's referenced-by-transaction guard (409 when a category is in
 * use), deferred until F004 (Transactions) existed to check against — see CLAUDE.md's F002 status
 * entry.
 */
@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;
  private final IdGenerator idGenerator;

  public CategoryService(
      CategoryRepository categoryRepository,
      TransactionRepository transactionRepository,
      IdGenerator idGenerator) {
    this.categoryRepository = categoryRepository;
    this.transactionRepository = transactionRepository;
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
    if (transactionRepository.existsByCategoryId(id)) {
      throw new CategoryInUseException(id);
    }
    categoryRepository.deleteById(id);
  }
}
