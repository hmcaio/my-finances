package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * In-memory test double for {@link TransferRepository}, shared across application-service tests
 * (same spirit as {@code FakeTransactionRepository}). Implements real filtering/sorting/paging
 * semantics - not a stub - including {@code accountId} matching either side of the transfer (PRD
 * S6.9).
 */
public final class FakeTransferRepository extends InMemoryRepository<Transfer>
    implements TransferRepository {

  public FakeTransferRepository() {
    super(Transfer::getId);
  }

  @Override
  public Page<Transfer> findAll(TransferFilter filter, Pageable pageable) {
    List<Transfer> filtered =
        values().stream()
            .filter(t -> filter.dateFrom() == null || !t.getDate().isBefore(filter.dateFrom()))
            .filter(t -> filter.dateTo() == null || !t.getDate().isAfter(filter.dateTo()))
            .filter(
                t ->
                    filter.accountId() == null
                        || t.getFromAccountId().equals(filter.accountId())
                        || t.getToAccountId().equals(filter.accountId()))
            .filter(
                t ->
                    filter.investmentProductId() == null
                        || filter.investmentProductId().equals(t.getInvestmentProductId()))
            .sorted(Comparator.comparing(Transfer::getDate).reversed())
            .toList();

    // Pageable.unpaged() means "every match" (F013 export), as for real Spring Data JPA.
    if (pageable.isUnpaged()) {
      return new PageImpl<>(filtered);
    }

    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), filtered.size());
    List<Transfer> pageContent =
        start >= filtered.size() ? List.of() : filtered.subList(start, end);
    return new PageImpl<>(pageContent, pageable, filtered.size());
  }

  @Override
  public List<LocalDate> findDistinctDatesBetween(LocalDate from, LocalDate to) {
    return values().stream()
        .map(Transfer::getDate)
        .filter(date -> !date.isBefore(from) && !date.isAfter(to))
        .distinct()
        .toList();
  }

  @Override
  public List<Transfer> findByInvestmentProductId(UUID investmentProductId) {
    return values().stream()
        .filter(t -> investmentProductId.equals(t.getInvestmentProductId()))
        .toList();
  }

  @Override
  public List<Transfer> findAllInvestmentTrades() {
    return values().stream().filter(t -> t.getInvestmentProductId() != null).toList();
  }

  @Override
  public boolean existsByInvestmentProductId(UUID investmentProductId) {
    return values().stream().anyMatch(t -> investmentProductId.equals(t.getInvestmentProductId()));
  }

  @Override
  public List<Transfer> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate) {
    return values().stream()
        .filter(
            t ->
                (t.getFromAccountId().equals(accountId) || t.getToAccountId().equals(accountId))
                    && !t.getDate().isAfter(asOfDate))
        .toList();
  }
}
