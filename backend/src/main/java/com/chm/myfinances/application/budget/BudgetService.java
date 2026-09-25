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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

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
   *
   * <p>{@code @Transactional} since it's two writes (budget + its first version) that must commit
   * or roll back together - a failure between them would otherwise leave a budget with zero
   * versions, same multi-write reasoning as F007's {@code RecurringTemplateService.create}.
   */
  @Transactional
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
      BudgetVersion replaced = budgetVersionRepository.save(version);
      log.info("Budget {}: version effective {} replaced", budget.getId(), effectiveFrom);
      return replaced;
    }
    BudgetVersion version =
        BudgetVersion.create(idGenerator.newId(), budget.getId(), monthlyCap, effectiveFrom);
    BudgetVersion saved = budgetVersionRepository.save(version);
    log.info("Budget {}: new version effective {}", budget.getId(), effectiveFrom);
    return saved;
  }

  /**
   * Stops budgeting from {@code effectiveFrom} onward (issue #61): stores a tombstone version (no
   * cap), replacing the version for that exact month if there is one, like {@link #setCap}. Earlier
   * months keep their cap; a later {@link #setCap} resumes the budget. A no-op (returns the
   * effective tombstone) when the budget is already stopped as of that month, so repeated stops
   * never pile up redundant tombstones.
   */
  public BudgetVersion stop(UUID budgetId, YearMonth effectiveFrom) {
    Budget budget = findById(budgetId);
    Optional<BudgetVersion> effective =
        BudgetVersion.resolveEffective(
            budgetVersionRepository.findByBudgetId(budget.getId()), effectiveFrom);
    if (effective.isPresent() && effective.get().isTombstone()) {
      return effective.get();
    }
    Optional<BudgetVersion> existing =
        budgetVersionRepository.findByBudgetIdAndEffectiveFrom(budget.getId(), effectiveFrom);
    BudgetVersion tombstone;
    if (existing.isPresent()) {
      tombstone = existing.get();
      tombstone.stop();
    } else {
      tombstone = BudgetVersion.tombstone(idGenerator.newId(), budget.getId(), effectiveFrom);
    }
    BudgetVersion saved = budgetVersionRepository.save(tombstone);
    log.info("Budget {}: stopped effective {}", budget.getId(), effectiveFrom);
    return saved;
  }
}
