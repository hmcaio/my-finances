package com.chm.myfinances.application.budget;

import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link Budget}/{@link BudgetVersion}: create (with its first version),
 * findById/findAll, and set-cap (F006 spec). New ids come from the {@link IdGenerator} port (ADR
 * 0005).
 *
 * <p>Coordinates against F002's {@link CategoryRepository} to validate the target category exists
 * and is an {@code EXPENSE} category - ordinary application-layer orchestration (ADR 0004), not a
 * domain-layer dependency: {@code domain/budget} itself never imports {@code domain.category}, same
 * convention as {@code TransferService} validating accounts via F003's {@code AccountRepository}.
 */
@Service
public class BudgetService {

  private final BudgetRepository budgetRepository;
  private final BudgetVersionRepository budgetVersionRepository;
  private final CategoryRepository categoryRepository;
  private final IdGenerator idGenerator;

  public BudgetService(
      BudgetRepository budgetRepository,
      BudgetVersionRepository budgetVersionRepository,
      CategoryRepository categoryRepository,
      IdGenerator idGenerator) {
    this.budgetRepository = budgetRepository;
    this.budgetVersionRepository = budgetVersionRepository;
    this.categoryRepository = categoryRepository;
    this.idGenerator = idGenerator;
  }

  /**
   * Creates a Budget for {@code categoryId} plus its first {@link BudgetVersion} (F006 spec).
   * Rejects an unknown category (404), a non-{@code EXPENSE} category (409, PRD S5.6), and a
   * category that's already budgeted (409, PRD S5.6's "one per category").
   */
  public Budget create(UUID categoryId, BigDecimal monthlyCap, YearMonth effectiveFrom) {
    Category category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    if (category.getType() != CategoryType.EXPENSE) {
      throw new CategoryNotExpenseException(categoryId);
    }
    if (budgetRepository.existsByCategoryId(categoryId)) {
      throw new BudgetAlreadyExistsException(categoryId);
    }

    Budget budget = budgetRepository.save(Budget.create(idGenerator.newId(), categoryId));
    budgetVersionRepository.save(
        BudgetVersion.create(idGenerator.newId(), budget.getId(), monthlyCap, effectiveFrom));
    return budget;
  }

  public Budget findById(UUID id) {
    return budgetRepository.findById(id).orElseThrow(() -> new BudgetNotFoundException(id));
  }

  public List<Budget> findAll() {
    return budgetRepository.findAll();
  }

  /**
   * Sets the cap effective from {@code effectiveFrom} (F006 spec's {@code PATCH .../cap}): replaces
   * the existing version for that exact month if one already exists (the one allowed same-month
   * correction), otherwise creates a brand-new forward-only version - versioning is otherwise never
   * rewritten (PRD S5.6).
   */
  public BudgetVersion setCap(UUID budgetId, BigDecimal monthlyCap, YearMonth effectiveFrom) {
    Budget budget = findById(budgetId);
    Optional<BudgetVersion> existing =
        budgetVersionRepository.findByBudgetIdAndEffectiveFrom(budget.getId(), effectiveFrom);
    if (existing.isPresent()) {
      BudgetVersion version = existing.get();
      version.updateCap(monthlyCap);
      return budgetVersionRepository.save(version);
    }
    BudgetVersion version =
        BudgetVersion.create(idGenerator.newId(), budget.getId(), monthlyCap, effectiveFrom);
    return budgetVersionRepository.save(version);
  }
}
