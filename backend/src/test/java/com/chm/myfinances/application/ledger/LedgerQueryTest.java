package com.chm.myfinances.application.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link LedgerQuery} (F037), written first (ADR 0004) against the
 * hand-written fakes: merge order, which side is read for which filter, the summary, and the
 * 1,000-row cap.
 */
class LedgerQueryTest {

  private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final LedgerQuery query = new LedgerQuery(transactionRepository, transferRepository);

  private static LedgerFilter filter(LedgerEntryType type) {
    return new LedgerFilter(null, null, type, null, null, null, null);
  }

  private static LedgerFilter filterByAccount(UUID accountId) {
    return new LedgerFilter(null, null, null, accountId, null, null, null);
  }

  @Test
  void interleavesByDateDescendingThenTransactionsBeforeTransfers() {
    Transfer oldTransfer =
        transferRepository.save(TransferMother.transfer().withDate(MARCH.minusDays(5)).build());
    Transfer sameDayTransfer =
        transferRepository.save(TransferMother.transfer().withDate(MARCH).build());
    Transaction sameDayExpense =
        transactionRepository.save(TransactionMother.expense().withDate(MARCH).build());
    Transaction newestIncome =
        transactionRepository.save(TransactionMother.income().withDate(MARCH.plusDays(2)).build());

    Ledger ledger = query.find(LedgerFilter.none());

    assertThat(ledger.entries())
        .extracting(LedgerEntry::id)
        .containsExactly(
            newestIncome.getId(),
            sameDayExpense.getId(),
            sameDayTransfer.getId(),
            oldTransfer.getId());
    assertThat(ledger.entries())
        .extracting(LedgerEntry::type)
        .containsExactly(
            LedgerEntryType.INCOME,
            LedgerEntryType.EXPENSE,
            LedgerEntryType.TRANSFER,
            LedgerEntryType.TRANSFER);
  }

  @Test
  void breaksTiesOnTheSameDayAndKindByIdDescending() {
    for (int i = 0; i < 10; i++) {
      transactionRepository.save(TransactionMother.expense().withDate(MARCH).build());
    }

    Ledger ledger = query.find(LedgerFilter.none());

    assertThat(ledger.entries().stream().map(e -> e.id().toString()).toList())
        .isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  void typeTransferReadsNoTransactions() {
    transactionRepository.save(TransactionMother.expense().withDate(MARCH).build());
    Transfer transfer = transferRepository.save(TransferMother.transfer().withDate(MARCH).build());

    Ledger ledger = query.find(filter(LedgerEntryType.TRANSFER));

    assertThat(ledger.entries()).extracting(LedgerEntry::id).containsExactly(transfer.getId());
    assertThat(ledger.summary().expense()).isEqualByComparingTo("0");
  }

  @Test
  void typeIncomeOrExpenseReadsNoTransfers() {
    Transaction income =
        transactionRepository.save(TransactionMother.income().withDate(MARCH).build());
    Transaction expense =
        transactionRepository.save(TransactionMother.expense().withDate(MARCH).build());
    transferRepository.save(TransferMother.transfer().withDate(MARCH).build());

    assertThat(query.find(filter(LedgerEntryType.INCOME)).entries())
        .extracting(LedgerEntry::id)
        .containsExactly(income.getId());
    assertThat(query.find(filter(LedgerEntryType.EXPENSE)).entries())
        .extracting(LedgerEntry::id)
        .containsExactly(expense.getId());
  }

  @Test
  void aCategoryOrPaymentMethodFilterExcludesTransfers() {
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    Transaction matching =
        transactionRepository.save(
            TransactionMother.expense()
                .withDate(MARCH)
                .withCategoryId(categoryId)
                .withPaymentMethodId(paymentMethodId)
                .build());
    transferRepository.save(TransferMother.transfer().withDate(MARCH).build());

    Ledger byCategory =
        query.find(new LedgerFilter(null, null, null, null, categoryId, null, null));
    Ledger byPaymentMethod =
        query.find(new LedgerFilter(null, null, null, null, null, paymentMethodId, null));

    assertThat(byCategory.entries()).extracting(LedgerEntry::id).containsExactly(matching.getId());
    assertThat(byPaymentMethod.entries())
        .extracting(LedgerEntry::id)
        .containsExactly(matching.getId());
  }

  @Test
  void transferTypeWithACategoryIsAnEmptyResultNotAnError() {
    transactionRepository.save(TransactionMother.expense().withDate(MARCH).build());
    transferRepository.save(TransferMother.transfer().withDate(MARCH).build());

    Ledger ledger =
        query.find(
            new LedgerFilter(
                null, null, LedgerEntryType.TRANSFER, null, UUID.randomUUID(), null, null));

    assertThat(ledger.entries()).isEmpty();
    assertThat(ledger.truncated()).isFalse();
    assertThat(ledger.summary().net()).isEqualByComparingTo("0");
  }

  @Test
  void anAccountFilterMatchesATransactionsAccountAndEitherSideOfATransfer() {
    UUID accountId = UUID.randomUUID();
    Transaction onAccount =
        transactionRepository.save(
            TransactionMother.expense().withDate(MARCH).withAccountId(accountId).build());
    Transfer fromAccount =
        transferRepository.save(
            TransferMother.transfer().withDate(MARCH).withFromAccountId(accountId).build());
    Transfer toAccount =
        transferRepository.save(
            TransferMother.transfer().withDate(MARCH).withToAccountId(accountId).build());
    transactionRepository.save(TransactionMother.expense().withDate(MARCH).build());
    transferRepository.save(TransferMother.transfer().withDate(MARCH).build());

    Ledger ledger = query.find(filterByAccount(accountId));

    assertThat(ledger.entries())
        .extracting(LedgerEntry::id)
        .containsExactlyInAnyOrder(onAccount.getId(), fromAccount.getId(), toAccount.getId());
  }

  @Test
  void summaryExcludesTransfersAndNetIsIncomeMinusExpense() {
    transactionRepository.save(
        TransactionMother.income().withDate(MARCH).withAmount(new BigDecimal("1000.00")).build());
    transactionRepository.save(
        TransactionMother.income().withDate(MARCH).withAmount(new BigDecimal("200.50")).build());
    transactionRepository.save(
        TransactionMother.expense().withDate(MARCH).withAmount(new BigDecimal("300.25")).build());
    transferRepository.save(
        TransferMother.transfer().withDate(MARCH).withAmount(new BigDecimal("5000.00")).build());

    LedgerSummary summary = query.find(LedgerFilter.none()).summary();

    assertThat(summary.income()).isEqualByComparingTo("1200.50");
    assertThat(summary.expense()).isEqualByComparingTo("300.25");
    assertThat(summary.net()).isEqualByComparingTo("900.25");
  }

  @Test
  void summaryIsZeroWhenNothingMatches() {
    LedgerSummary summary = query.find(LedgerFilter.none()).summary();

    assertThat(summary.income()).isEqualByComparingTo("0");
    assertThat(summary.expense()).isEqualByComparingTo("0");
    assertThat(summary.net()).isEqualByComparingTo("0");
  }

  @Test
  void aCombinedThousandAndOneRowsYieldAThousandEntriesAndTruncated() {
    saveRows(600, 401);

    Ledger ledger = query.find(LedgerFilter.none());

    assertThat(ledger.entries()).hasSize(LedgerQuery.MAX_ENTRIES);
    assertThat(ledger.truncated()).isTrue();
  }

  @Test
  void exactlyAThousandRowsAreNotTruncated() {
    saveRows(600, 400);

    Ledger ledger = query.find(LedgerFilter.none());

    assertThat(ledger.entries()).hasSize(LedgerQuery.MAX_ENTRIES);
    assertThat(ledger.truncated()).isFalse();
  }

  @Test
  void theCutKeepsTheNewestRowsAcrossBothKinds() {
    LocalDate newest = MARCH.plusDays(30);
    Transfer newestTransfer =
        transferRepository.save(TransferMother.transfer().withDate(newest).build());
    saveRows(600, 400);

    Ledger ledger = query.find(LedgerFilter.none());

    assertThat(ledger.truncated()).isTrue();
    assertThat(ledger.entries().get(0).id()).isEqualTo(newestTransfer.getId());
  }

  @Test
  void theSummaryStaysCorrectWhenTheListIsTruncated() {
    for (int i = 0; i < LedgerQuery.MAX_ENTRIES + 5; i++) {
      transactionRepository.save(
          TransactionMother.expense()
              .withDate(MARCH.plusDays(i % 20))
              .withAmount(new BigDecimal("1.00"))
              .build());
    }

    Ledger ledger = query.find(LedgerFilter.none());

    assertThat(ledger.truncated()).isTrue();
    assertThat(ledger.entries()).hasSize(LedgerQuery.MAX_ENTRIES);
    assertThat(ledger.summary().expense()).isEqualByComparingTo("1005.00");
  }

  @Test
  void aBlankSearchTextBecomesNull() {
    assertThat(new LedgerFilter(null, null, null, null, null, null, "   ").searchText()).isNull();
    assertThat(new LedgerFilter(null, null, null, null, null, null, "").searchText()).isNull();
    assertThat(new LedgerFilter(null, null, null, null, null, null, null).searchText()).isNull();
    assertThat(new LedgerFilter(null, null, null, null, null, null, "  café ").searchText())
        .isEqualTo("café");
  }

  @Test
  void searchNarrowsBothSidesAndTheSummary() {
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(MARCH)
            .withDescription("Café da manhã")
            .withAmount(new BigDecimal("12.00"))
            .build());
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(MARCH)
            .withDescription("Aluguel")
            .withAmount(new BigDecimal("900.00"))
            .build());
    Transfer matchingTransfer =
        transferRepository.save(
            TransferMother.transfer().withDate(MARCH).withDescription("Cafe fund").build());
    transferRepository.save(TransferMother.transfer().withDate(MARCH).build());

    Ledger ledger = query.find(new LedgerFilter(null, null, null, null, null, null, "CAFE"));

    List<UUID> ids = ledger.entries().stream().map(LedgerEntry::id).toList();
    assertThat(ids).hasSize(2).contains(matchingTransfer.getId());
    assertThat(ledger.summary().expense()).isEqualByComparingTo("12.00");
  }

  private void saveRows(int transactions, int transfers) {
    for (int i = 0; i < transactions; i++) {
      transactionRepository.save(
          TransactionMother.expense().withDate(MARCH.minusDays(i % 25)).build());
    }
    for (int i = 0; i < transfers; i++) {
      transferRepository.save(TransferMother.transfer().withDate(MARCH.minusDays(i % 25)).build());
    }
  }
}
