package com.chm.myfinances.application.budget;

import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves a {@code Budget}'s currently-effective cap for a given month (F006 spec: "GET
 * /api/budgets - list, each with its current cap", and the budget-vs-actual report) by loading
 * every {@link BudgetVersion} for a budget and delegating to {@link BudgetVersion#resolveEffective}
 * - a computed-not-stored value shared by both {@code BudgetController}'s list endpoint and {@link
 * BudgetReportQuery}. Same application-layer "query object for a computed-not-stored value"
 * convention as F003's {@code AccountBalanceQuery}.
 */
@Service
public class BudgetCapQuery {

  private final BudgetVersionRepository budgetVersionRepository;

  public BudgetCapQuery(BudgetVersionRepository budgetVersionRepository) {
    this.budgetVersionRepository = budgetVersionRepository;
  }

  public Optional<BudgetVersion> effectiveCap(UUID budgetId, YearMonth month) {
    return BudgetVersion.resolveEffective(budgetVersionRepository.findByBudgetId(budgetId), month);
  }
}
