package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public final class FakeTransferRepository implements TransferRepository {

  private final Map<UUID, Transfer> store = new HashMap<>();

  @Override
  public Transfer save(Transfer transfer) {
    store.put(transfer.getId(), transfer);
    return transfer;
  }

  @Override
  public Optional<Transfer> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return store.containsKey(id);
  }

  @Override
  public Page<Transfer> findAll(TransferFilter filter, Pageable pageable) {
    List<Transfer> filtered =
        store.values().stream()
            .filter(t -> filter.dateFrom() == null || !t.getDate().isBefore(filter.dateFrom()))
            .filter(t -> filter.dateTo() == null || !t.getDate().isAfter(filter.dateTo()))
            .filter(
                t ->
                    filter.accountId() == null
                        || t.getFromAccountId().equals(filter.accountId())
                        || t.getToAccountId().equals(filter.accountId()))
            .sorted(Comparator.comparing(Transfer::getDate).reversed())
            .toList();

    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), filtered.size());
    List<Transfer> pageContent =
        start >= filtered.size() ? List.of() : filtered.subList(start, end);
    return new PageImpl<>(pageContent, pageable, filtered.size());
  }

  @Override
  public List<Transfer> findByAccountIdOnOrBefore(UUID accountId, LocalDate asOfDate) {
    return store.values().stream()
        .filter(
            t ->
                (t.getFromAccountId().equals(accountId) || t.getToAccountId().equals(accountId))
                    && !t.getDate().isAfter(asOfDate))
        .toList();
  }
}
