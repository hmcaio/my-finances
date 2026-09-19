package com.chm.myfinances.application.category;

import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Category}: create/rename/delete (F002 spec). New ids come from the {@link
 * IdGenerator} port (ADR 0005) — never generated ad hoc here or left to the database.
 *
 * <p>Delete enforces F002 plan.md's referenced-by-transaction guard (409 when a category is in
 * use), deferred until F004 (Transactions) existed to check against — see F004's spec.md. Broadened
 * in the post-F007 schema audit to also check F006's {@link BudgetRepository} and F007's {@link
 * RecurringTemplateRepository}: a category with a {@code Budget} or {@code RecurringTemplate} but
 * zero transactions yet was previously still hard-deletable, which would have orphaned that row's
 * own FK to {@code categories} (a raw DB constraint violation surfacing as a generic 500, not this
 * clean 409) — same {@link CategoryInUseException}, just a wider check.
 *
 * <p>Create/rename reject a duplicate name (409, {@link CategoryNameAlreadyExistsException}) —
 * exact match, case-sensitive, backed by {@code categories.name UNIQUE} ({@code
 * V10__db_constraint_hardening.sql}), added in the same audit.
 */
@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final BudgetRepository budgetRepository;
  private final RecurringTemplateRepository recurringTemplateRepository;
  private final TransactionRepository transactionRepository;
  private final IdGenerator idGenerator;

  public CategoryService(
      CategoryRepository categoryRepository,
      BudgetRepository budgetRepository,
      RecurringTemplateRepository recurringTemplateRepository,
      TransactionRepository transactionRepository,
      IdGenerator idGenerator) {
    this.categoryRepository = categoryRepository;
    this.budgetRepository = budgetRepository;
    this.recurringTemplateRepository = recurringTemplateRepository;
    this.transactionRepository = transactionRepository;
    this.idGenerator = idGenerator;
  }

  public Category create(String name, CategoryType type) {
    if (categoryRepository.existsByName(name)) {
      throw new CategoryNameAlreadyExistsException(name);
    }
    Category category = Category.create(idGenerator.newId(), name, type);
    return categoryRepository.save(category);
  }

  public List<Category> findAll() {
    return categoryRepository.findAll();
  }

  public Category rename(UUID id, String newName) {
    Category category =
        categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    if (categoryRepository.existsByNameAndIdNot(newName, id)) {
      throw new CategoryNameAlreadyExistsException(newName);
    }
    category.rename(newName);
    return categoryRepository.save(category);
  }

  public void delete(UUID id) {
    if (!categoryRepository.existsById(id)) {
      throw new CategoryNotFoundException(id);
    }
    if (transactionRepository.existsByCategoryId(id)
        || budgetRepository.existsByCategoryId(id)
        || recurringTemplateRepository.existsByCategoryId(id)) {
      throw new CategoryInUseException(id);
    }
    categoryRepository.deleteById(id);
  }
}
