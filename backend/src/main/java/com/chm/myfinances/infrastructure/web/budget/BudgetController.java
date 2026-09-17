package com.chm.myfinances.infrastructure.web.budget;

import com.chm.myfinances.application.budget.BudgetCapQuery;
import com.chm.myfinances.application.budget.BudgetReportQuery;
import com.chm.myfinances.application.budget.BudgetService;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetVersion;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code Budget}/{@code BudgetVersion} (F006 spec). Unlike F004/F005's transaction/
 * transfer endpoints, {@code GET /api/budgets} is a plain list (no {@code PagedModel}) - the number
 * of budgeted categories is inherently small (PRD S5.6).
 */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

  private final BudgetService budgetService;
  private final BudgetCapQuery budgetCapQuery;
  private final BudgetReportQuery budgetReportQuery;

  public BudgetController(
      BudgetService budgetService,
      BudgetCapQuery budgetCapQuery,
      BudgetReportQuery budgetReportQuery) {
    this.budgetService = budgetService;
    this.budgetCapQuery = budgetCapQuery;
    this.budgetReportQuery = budgetReportQuery;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BudgetResponse create(@Valid @RequestBody CreateBudgetRequest request) {
    Budget budget =
        budgetService.create(request.categoryId(), request.monthlyCap(), request.effectiveFrom());
    return toResponse(budget);
  }

  /** List of every Budget, each with its cap as of the current real-world month. */
  @GetMapping
  public List<BudgetResponse> list() {
    return budgetService.findAll().stream().map(this::toResponse).toList();
  }

  @PatchMapping("/{id}/cap")
  public BudgetResponse setCap(
      @PathVariable UUID id, @Valid @RequestBody UpdateBudgetCapRequest request) {
    budgetService.setCap(id, request.monthlyCap(), request.effectiveFrom());
    return toResponse(budgetService.findById(id));
  }

  /** Budget-vs-actual for every budgeted category, for {@code month} (F006 spec, PRD S5.6). */
  @GetMapping("/report")
  public List<BudgetReportLineResponse> report(
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return budgetReportQuery.forMonth(month).stream().map(BudgetReportLineResponse::from).toList();
  }

  private BudgetResponse toResponse(Budget budget) {
    Optional<BudgetVersion> currentVersion =
        budgetCapQuery.effectiveCap(budget.getId(), YearMonth.now());
    return BudgetResponse.from(budget, currentVersion);
  }
}
