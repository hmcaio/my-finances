package com.chm.myfinances.application.category;

import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *
 * <p>Delete, in order: unknown id (404), a built-in category (409, {@link
 * BuiltInCategoryException}), the fuel category (409, {@link FuelCategoryException} - F024,
 * independent of {@code built_in}), a category something still references (409, {@link
 * CategoryInUseException}). Rename rejects the fuel category the same way, before the
 * duplicate-name check - unlike a built-in category, which stays freely renamable.
 */
@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final BudgetRepository budgetRepository;
  private final RecurringTemplateRepository recurringTemplateRepository;
  private final TransactionRepository transactionRepository;
  private final IdGenerator idGenerator;
  private final AuditRecorder auditRecorder;

  public CategoryService(
      CategoryRepository categoryRepository,
      BudgetRepository budgetRepository,
      RecurringTemplateRepository recurringTemplateRepository,
      TransactionRepository transactionRepository,
      IdGenerator idGenerator,
      AuditRecorder auditRecorder) {
    this.categoryRepository = categoryRepository;
    this.budgetRepository = budgetRepository;
    this.recurringTemplateRepository = recurringTemplateRepository;
    this.transactionRepository = transactionRepository;
    this.idGenerator = idGenerator;
    this.auditRecorder = auditRecorder;
  }

  @Transactional
  public Category create(String name, CategoryType type) {
    if (categoryRepository.existsByName(name)) {
      throw new CategoryNameAlreadyExistsException(name);
    }
    Category category = Category.create(idGenerator.newId(), name, type);
    Category saved = categoryRepository.save(category);
    auditRecorder.recordCreate(
        AuditEntityType.CATEGORY, saved.getId(), saved.getName(), saved.toAuditSnapshot());
    return saved;
  }

  public List<Category> findAll() {
    return categoryRepository.findAll();
  }

  @Transactional
  public Category rename(UUID id, String newName) {
    Category category =
        categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    Map<String, Object> before = category.toAuditSnapshot();
    if (category.isFuelCategory()) {
      throw new FuelCategoryException(id);
    }
    if (category.isDividendCategory()) {
      throw new DividendCategoryException(id);
    }
    if (categoryRepository.existsByNameAndIdNot(newName, id)) {
      throw new CategoryNameAlreadyExistsException(newName);
    }
    category.rename(newName);
    Category saved = categoryRepository.save(category);
    auditRecorder.recordUpdate(
        AuditEntityType.CATEGORY, saved.getId(), saved.getName(), before, saved.toAuditSnapshot());
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    Category category =
        categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    if (category.isBuiltIn()) {
      throw new BuiltInCategoryException(id);
    }
    if (category.isFuelCategory()) {
      throw new FuelCategoryException(id);
    }
    if (category.isDividendCategory()) {
      throw new DividendCategoryException(id);
    }
    if (transactionRepository.existsByCategoryId(id)
        || budgetRepository.existsByCategoryId(id)
        || recurringTemplateRepository.existsByCategoryId(id)) {
      throw new CategoryInUseException(id);
    }
    categoryRepository.deleteById(id);
    auditRecorder.recordDelete(
        AuditEntityType.CATEGORY, category.getId(), category.getName(), category.toAuditSnapshot());
  }
}
