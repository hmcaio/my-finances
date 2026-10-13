package com.chm.myfinances.infrastructure.web.transaction;

import com.chm.myfinances.application.transaction.MonthlySpendByCategoryQuery;
import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code Transaction} (F004 spec). */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

  private final TransactionService transactionService;
  private final MonthlySpendByCategoryQuery monthlySpendByCategoryQuery;

  public TransactionController(
      TransactionService transactionService,
      MonthlySpendByCategoryQuery monthlySpendByCategoryQuery) {
    this.transactionService = transactionService;
    this.monthlySpendByCategoryQuery = monthlySpendByCategoryQuery;
  }

  /**
   * Expense total per category for {@code month} ({@code YYYY-MM}), largest first (F012's dashboard
   * widget).
   */
  @GetMapping("/spend-by-category")
  public List<CategorySpendResponse> spendByCategory(
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return monthlySpendByCategoryQuery.forMonth(month).stream()
        .map(CategorySpendResponse::from)
        .toList();
  }

  /**
   * Filtered, paginated list - {@code GET /api/transactions?dateFrom=&dateTo=&categoryId=&
   * accountId=&paymentMethodId=&page=&size=&sort=}. Defaults to 20 per page, most recent first
   * ({@code date} descending). Returns {@link PagedModel} rather than a raw {@link Page} - Spring
   * Data's recommended shape for a stable JSON envelope ({@code content}/{@code page}) instead of
   * leaking {@code PageImpl}'s internal serialization.
   */
  @GetMapping
  public PagedModel<TransactionResponse> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateTo,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID paymentMethodId,
      @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
          Pageable pageable) {
    TransactionFilter filter =
        new TransactionFilter(dateFrom, dateTo, categoryId, accountId, paymentMethodId);
    Page<Transaction> page = transactionService.findAll(filter, pageable);
    return new PagedModel<>(page.map(TransactionResponse::from));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of urn:my-finances:error:fuel-details-category-mismatch, "
              + "urn:my-finances:error:investment-holding-category-mismatch. May instead be "
              + "urn:my-finances:error:method-argument-not-valid (F032), carrying an errors[] "
              + "array of {field, code} with code one of: not-null, positive, not-blank, size, "
              + "digits, fuel-fields-given-together",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:category-not-found, "
              + "urn:my-finances:error:account-not-found, "
              + "urn:my-finances:error:payment-method-not-found, "
              + "urn:my-finances:error:vehicle-not-found, "
              + "urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:account-closed, "
              + "urn:my-finances:error:account-type-not-allowed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TransactionResponse create(@Valid @RequestBody CreateTransactionRequest request) {
    Transaction transaction =
        transactionService.create(
            request.date(),
            request.amount(),
            request.categoryId(),
            request.accountId(),
            request.paymentMethodId(),
            null,
            request.description(),
            request.additionalNotes(),
            toFuelDetails(request),
            request.investmentHoldingId());
    return TransactionResponse.from(transaction);
  }

  private static FuelDetails toFuelDetails(CreateTransactionRequest request) {
    return request.vehicleId() == null
        ? null
        : new FuelDetails(
            request.vehicleId(),
            request.fuelType(),
            request.liters(),
            request.pricePerLiter(),
            request.kmSinceLastFill(),
            request.odometer());
  }

  private static FuelDetails toFuelDetails(UpdateTransactionRequest request) {
    return request.vehicleId() == null
        ? null
        : new FuelDetails(
            request.vehicleId(),
            request.fuelType(),
            request.liters(),
            request.pricePerLiter(),
            request.kmSinceLastFill(),
            request.odometer());
  }

  @GetMapping("/{id}")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:transaction-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TransactionResponse get(@PathVariable UUID id) {
    return TransactionResponse.from(transactionService.findById(id));
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of urn:my-finances:error:fuel-details-category-mismatch, "
              + "urn:my-finances:error:investment-holding-category-mismatch. May instead be "
              + "urn:my-finances:error:method-argument-not-valid (F032), carrying an errors[] "
              + "array of {field, code} with code one of: not-null, positive, not-blank, size, "
              + "digits, fuel-fields-given-together",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:transaction-not-found, "
              + "urn:my-finances:error:category-not-found, "
              + "urn:my-finances:error:account-not-found, "
              + "urn:my-finances:error:payment-method-not-found, "
              + "urn:my-finances:error:vehicle-not-found, "
              + "urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:account-closed, "
              + "urn:my-finances:error:account-type-not-allowed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TransactionResponse edit(
      @PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest request) {
    Transaction transaction =
        transactionService.edit(
            id,
            request.date(),
            request.amount(),
            request.categoryId(),
            request.accountId(),
            request.paymentMethodId(),
            request.description(),
            request.additionalNotes(),
            toFuelDetails(request),
            request.investmentHoldingId());
    return TransactionResponse.from(transaction);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:transaction-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    transactionService.delete(id);
  }
}
