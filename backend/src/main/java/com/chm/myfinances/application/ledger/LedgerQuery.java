package com.chm.myfinances.application.ledger;

import com.chm.myfinances.application.ledger.LedgerEntry.TransactionEntry;
import com.chm.myfinances.application.ledger.LedgerEntry.TransferEntry;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query object behind {@code GET /api/ledger} (F037): reads transactions and transfers through
 * their own repository ports and merges the two lists in memory, newest first. Read-only; the two
 * reads and the summary sums see one snapshot because the method is a single read-only transaction.
 *
 * <p>Each side is fetched with the page request {@code (0, MAX_ENTRIES + 1, date DESC, id DESC)},
 * which guarantees the top {@code MAX_ENTRIES} of the merge are present; the merged list is
 * re-sorted and cut. The summary is not computed from the returned entries but from {@link
 * TransactionRepository#sumAmountByType} over the whole filtered scope, so it stays correct when
 * the list is truncated.
 *
 * <p>Filter semantics (spec): {@code type} INCOME/EXPENSE reads transactions only, TRANSFER reads
 * transfers only, and a set {@code categoryId} or {@code paymentMethodId} excludes transfers (they
 * have neither) - so TRANSFER plus a category is a valid, empty request.
 */
@Service
public class LedgerQuery {

  /** Most rows the Ledger returns; one more than this matching sets {@code truncated}. */
  public static final int MAX_ENTRIES = 1000;

  /**
   * Date descending, then transactions before transfers, then id descending compared as the
   * lower-case canonical string - which is how Postgres orders {@code uuid} (bytewise), so the
   * tie-break agrees with the repositories' own {@code id DESC} page order.
   */
  private static final Comparator<LedgerEntry> NEWEST_FIRST =
      Comparator.comparing(LedgerEntry::date)
          .reversed()
          .thenComparing(entry -> entry instanceof TransferEntry)
          .thenComparing(
              Comparator.comparing((LedgerEntry entry) -> entry.id().toString()).reversed());

  private static final Pageable CAP_PLUS_ONE =
      PageRequest.of(0, MAX_ENTRIES + 1, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));

  private final TransactionRepository transactionRepository;
  private final TransferRepository transferRepository;

  public LedgerQuery(
      TransactionRepository transactionRepository, TransferRepository transferRepository) {
    this.transactionRepository = transactionRepository;
    this.transferRepository = transferRepository;
  }

  @Transactional(readOnly = true)
  public Ledger find(LedgerFilter filter) {
    List<LedgerEntry> merged = new ArrayList<>();
    LedgerSummary summary = summaryOf(Map.of());

    if (readsTransactions(filter)) {
      TransactionFilter transactionFilter = toTransactionFilter(filter);
      transactionRepository
          .findAll(transactionFilter, CAP_PLUS_ONE)
          .forEach(transaction -> merged.add(new TransactionEntry(transaction)));
      summary = summaryOf(transactionRepository.sumAmountByType(transactionFilter));
    }
    if (readsTransfers(filter)) {
      transferRepository
          .findAll(toTransferFilter(filter), CAP_PLUS_ONE)
          .forEach(transfer -> merged.add(new TransferEntry(transfer)));
    }

    merged.sort(NEWEST_FIRST);
    boolean truncated = merged.size() > MAX_ENTRIES;
    List<LedgerEntry> entries =
        truncated ? List.copyOf(merged.subList(0, MAX_ENTRIES)) : List.copyOf(merged);
    return new Ledger(entries, summary, truncated);
  }

  private static boolean readsTransactions(LedgerFilter filter) {
    return filter.type() != LedgerEntryType.TRANSFER;
  }

  private static boolean readsTransfers(LedgerFilter filter) {
    return filter.type() != LedgerEntryType.INCOME
        && filter.type() != LedgerEntryType.EXPENSE
        && filter.categoryId() == null
        && filter.paymentMethodId() == null;
  }

  private static TransactionFilter toTransactionFilter(LedgerFilter filter) {
    CategoryType type = null;
    if (filter.type() == LedgerEntryType.INCOME) {
      type = CategoryType.INCOME;
    } else if (filter.type() == LedgerEntryType.EXPENSE) {
      type = CategoryType.EXPENSE;
    }
    return new TransactionFilter(
        filter.dateFrom(),
        filter.dateTo(),
        filter.categoryId(),
        filter.accountId(),
        filter.paymentMethodId(),
        type,
        filter.searchText());
  }

  private static TransferFilter toTransferFilter(LedgerFilter filter) {
    return new TransferFilter(
        filter.dateFrom(), filter.dateTo(), filter.accountId(), null, filter.searchText());
  }

  private static LedgerSummary summaryOf(Map<CategoryType, BigDecimal> totals) {
    BigDecimal income = totals.getOrDefault(CategoryType.INCOME, BigDecimal.ZERO).setScale(2);
    BigDecimal expense = totals.getOrDefault(CategoryType.EXPENSE, BigDecimal.ZERO).setScale(2);
    return new LedgerSummary(income, expense, income.subtract(expense));
  }
}
