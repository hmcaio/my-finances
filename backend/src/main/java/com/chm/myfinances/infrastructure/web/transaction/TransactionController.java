package com.chm.myfinances.infrastructure.web.transaction;

import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

  public TransactionController(TransactionService transactionService) {
    this.transactionService = transactionService;
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
  public TransactionResponse create(@Valid @RequestBody CreateTransactionRequest request) {
    Transaction transaction =
        transactionService.create(
            request.date(),
            request.amount(),
            request.categoryId(),
            request.accountId(),
            request.paymentMethodId(),
            request.description(),
            request.additionalNotes());
    return TransactionResponse.from(transaction);
  }

  @GetMapping("/{id}")
  public TransactionResponse get(@PathVariable UUID id) {
    return TransactionResponse.from(transactionService.findById(id));
  }

  @PatchMapping("/{id}")
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
            request.additionalNotes());
    return TransactionResponse.from(transaction);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    transactionService.delete(id);
  }
}
