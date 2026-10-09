package com.chm.myfinances.domain.transfer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Repository port for {@link Transfer} (ADR 0004: domain/application logic sits behind ports,
 * isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/transfer}.
 *
 * <p>Uses Spring Data's framework-agnostic {@code Page}/{@code Pageable} directly, same convention
 * F004's {@code TransactionRepository} established for the app's first paginated list endpoint.
 */
public interface TransferRepository {

  Transfer save(Transfer transfer);

  Optional<Transfer> findById(UUID id);

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Filtered, paginated list (F005 spec's {@code GET /api/transfers} query params). */
  Page<Transfer> findAll(TransferFilter filter, Pageable pageable);

  /**
   * Every transfer touching {@code accountId} - on either side - on or before {@code asOfDate}, for
   * {@code AccountBalanceQuery} (F003, extended by F005) to fold into a running balance.
   */
  List<Transfer> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate);

  /**
   * The distinct dates, within {@code from}..{@code to} inclusive, on which any transfer is dated -
   * the change dates of F010's net worth trend.
   */
  List<LocalDate> findDistinctDatesBetween(LocalDate from, LocalDate to);

  /** Whether any transfer touches {@code accountId} on either side - account delete guard. */
  boolean existsByAccountId(UUID accountId);
}
