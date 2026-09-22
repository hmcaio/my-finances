package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * In-memory test double for {@link TransactionRepository}, shared across application-service tests
 * (same spirit as {@link FakeIdGenerator}). Implements real filtering/sorting/paging semantics -
 * not a stub - so {@code TransactionServiceTest}/{@code AccountBalanceQueryTest} can assert on
 * actual filter/pagination behavior without a database.
 */
public final class FakeTransactionRepository extends InMemoryRepository<Transaction>
    implements TransactionRepository {

  public FakeTransactionRepository() {
    super(Transaction::getId);
  }

  @Override
  public Page<Transaction> findAll(TransactionFilter filter, Pageable pageable) {
    List<Transaction> filtered =
        values().stream()
            .filter(t -> filter.dateFrom() == null || !t.getDate().isBefore(filter.dateFrom()))
            .filter(t -> filter.dateTo() == null || !t.getDate().isAfter(filter.dateTo()))
            .filter(
                t -> filter.categoryId() == null || t.getCategoryId().equals(filter.categoryId()))
            .filter(t -> filter.accountId() == null || t.getAccountId().equals(filter.accountId()))
            .filter(
                t ->
                    filter.paymentMethodId() == null
                        || t.getPaymentMethodId().equals(filter.paymentMethodId()))
            .sorted(Comparator.comparing(Transaction::getDate).reversed())
            .toList();

    // Pageable.unpaged() (used by F006's BudgetReportQuery, which wants every matching
    // transaction rather than one page of them) has no offset/page size to apply - same
    // "return everything" behavior Spring Data JPA gives it for real.
    if (pageable.isUnpaged()) {
      return new PageImpl<>(filtered);
    }

    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), filtered.size());
    List<Transaction> pageContent =
        start >= filtered.size() ? List.of() : filtered.subList(start, end);
    return new PageImpl<>(pageContent, pageable, filtered.size());
  }

  @Override
  public List<Transaction> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate) {
    return values().stream()
        .filter(t -> t.getAccountId().equals(accountId) && !t.getDate().isAfter(asOfDate))
        .toList();
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return values().stream().anyMatch(t -> t.getCategoryId().equals(categoryId));
  }

  @Override
  public boolean existsByPaymentMethodId(UUID paymentMethodId) {
    return values().stream().anyMatch(t -> t.getPaymentMethodId().equals(paymentMethodId));
  }
}
