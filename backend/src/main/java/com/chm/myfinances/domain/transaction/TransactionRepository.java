package com.chm.myfinances.domain.transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Repository port for {@link Transaction} (ADR 0004: domain/application logic sits behind ports,
 * isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/transaction}.
 *
 * <p>Uses Spring Data's framework-agnostic {@code Page}/{@code Pageable} (spring-data-commons, not
 * JPA-specific) directly rather than inventing a parallel paging abstraction - F004's list endpoint
 * is the first paginated one in this codebase, and this keeps the port, the application service,
 * and the controller all speaking the same shape end to end.
 */
public interface TransactionRepository {

  Transaction save(Transaction transaction);

  Optional<Transaction> findById(UUID id);

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Filtered, paginated list (F004 spec's {@code GET /api/transactions} query params). */
  Page<Transaction> findAll(TransactionFilter filter, Pageable pageable);

  /**
   * Every transaction posted to {@code accountId} on or before {@code asOfDate}, for {@code
   * AccountBalanceQuery} (F003) to sum into a running balance.
   */
  List<Transaction> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate);

  /**
   * Whether any transaction references {@code categoryId} - backs {@code CategoryService}'s
   * referenced-by-transaction delete guard (F002 plan.md's deferred item, added now that this
   * table exists to check against).
   */
  boolean existsByCategoryId(UUID categoryId);

  /** Same as {@link #existsByCategoryId} but for {@code PaymentMethodService}'s delete guard. */
  boolean existsByPaymentMethodId(UUID paymentMethodId);
}
