package com.chm.myfinances.testsupport.fakes;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Behavior of the in-memory fakes' F037 additions (type filter, accent-insensitive search, {@code
 * sumAmountByType}, unpaged), written first (ADR 0004). The fold uses {@code Normalizer}, which
 * mirrors Postgres {@code unaccent} for decomposable letters only (ADR 0030); the real behavior is
 * asserted by the repository integration tests.
 */
class FakeTextSearchTest {

  private final FakeTransactionRepository transactions = new FakeTransactionRepository();
  private final FakeTransferRepository transfers = new FakeTransferRepository();

  private static TransactionFilter textFilter(String text) {
    return new TransactionFilter(null, null, null, null, null, null, text);
  }

  private static TransactionFilter typeFilter(CategoryType type) {
    return new TransactionFilter(null, null, null, null, null, type, null);
  }

  @Test
  void aFiveArgumentFilterHasNoTypeAndNoSearchText() {
    TransactionFilter filter = new TransactionFilter(null, null, null, null, null);

    assertThat(filter.type()).isNull();
    assertThat(filter.searchText()).isNull();
    assertThat(TransactionFilter.none()).isEqualTo(filter);
    assertThat(new TransferFilter(null, null, null, null).searchText()).isNull();
    assertThat(TransferFilter.none()).isEqualTo(new TransferFilter(null, null, null, null, null));
  }

  @Test
  void typeFilterKeepsOnlyThatType() {
    Transaction income = transactions.save(TransactionMother.income().build());
    Transaction expense = transactions.save(TransactionMother.expense().build());

    assertThat(transactions.findAll(typeFilter(CategoryType.INCOME), Pageable.unpaged()))
        .containsExactly(income);
    assertThat(transactions.findAll(typeFilter(CategoryType.EXPENSE), Pageable.unpaged()))
        .containsExactly(expense);
  }

  @Test
  void unpagedReturnsEveryMatch() {
    for (int i = 0; i < 5; i++) {
      transactions.save(TransactionMother.expense().build());
    }

    assertThat(transactions.findAll(TransactionFilter.none(), Pageable.unpaged()).getContent())
        .hasSize(5);
    assertThat(transactions.findAll(TransactionFilter.none(), PageRequest.of(0, 2)).getContent())
        .hasSize(2);
  }

  @Test
  void searchIgnoresCaseAndAccentsOnDescriptionAndNotes() {
    Transaction cafe =
        transactions.save(TransactionMother.expense().withDescription("Café da manhã").build());
    Transaction viaNotes =
        transactions.save(
            TransactionMother.expense()
                .withDescription("Padaria")
                .withAdditionalNotes("pão e AÇAÍ")
                .build());
    transactions.save(TransactionMother.expense().withDescription("Aluguel").build());

    assertThat(transactions.findAll(textFilter("cafe"), Pageable.unpaged())).containsExactly(cafe);
    assertThat(transactions.findAll(textFilter("CAFÉ"), Pageable.unpaged())).containsExactly(cafe);
    assertThat(transactions.findAll(textFilter("acai"), Pageable.unpaged()))
        .containsExactly(viaNotes);
  }

  @Test
  void searchTreatsPercentUnderscoreAndBackslashLiterally() {
    Transaction percent =
        transactions.save(TransactionMother.expense().withDescription("50% off").build());
    transactions.save(TransactionMother.expense().withDescription("Aluguel").build());

    assertThat(transactions.findAll(textFilter("%"), Pageable.unpaged())).containsExactly(percent);
    assertThat(transactions.findAll(textFilter("_"), Pageable.unpaged())).isEmpty();
    assertThat(transactions.findAll(textFilter("\\"), Pageable.unpaged())).isEmpty();
  }

  @Test
  void blankSearchTextMeansNoSearch() {
    transactions.save(TransactionMother.expense().build());

    assertThat(transactions.findAll(textFilter("   "), Pageable.unpaged()).getContent()).hasSize(1);
  }

  @Test
  void transferSearchMatchesDescriptionAndNotesIgnoringCaseAndAccents() {
    Transfer card =
        transfers.save(TransferMother.transfer().withDescription("Fatura Cartão").build());
    Transfer viaNotes =
        transfers.save(
            TransferMother.transfer()
                .withDescription("Move")
                .withAdditionalNotes("reserva de emergência")
                .build());
    transfers.save(TransferMother.transfer().withDescription("Other").build());

    assertThat(
            transfers.findAll(
                new TransferFilter(null, null, null, null, "cartao"), Pageable.unpaged()))
        .containsExactly(card);
    assertThat(
            transfers.findAll(
                new TransferFilter(null, null, null, null, "EMERGENCIA"), Pageable.unpaged()))
        .containsExactly(viaNotes);
  }

  @Test
  void sumAmountByTypeTotalsPerTypeAndOmitsAnAbsentType() {
    LocalDate day = LocalDate.of(2026, 3, 10);
    transactions.save(
        TransactionMother.income().withDate(day).withAmount(new BigDecimal("100.00")).build());
    transactions.save(
        TransactionMother.income().withDate(day).withAmount(new BigDecimal("50.50")).build());
    transactions.save(
        TransactionMother.expense()
            .withDate(day.plusMonths(2))
            .withAmount(new BigDecimal("30.00"))
            .build());

    var inMarch =
        transactions.sumAmountByType(new TransactionFilter(day, day.plusDays(1), null, null, null));
    var all = transactions.sumAmountByType(TransactionFilter.none());

    assertThat(inMarch).containsOnlyKeys(CategoryType.INCOME);
    assertThat(inMarch.get(CategoryType.INCOME)).isEqualByComparingTo("150.50");
    assertThat(all.get(CategoryType.EXPENSE)).isEqualByComparingTo("30.00");
    assertThat(
            transactions
                .sumAmountByType(typeFilter(CategoryType.EXPENSE))
                .containsKey(CategoryType.INCOME))
        .isFalse();
  }

  @Test
  void sumAmountByTypeRespectsTheSearchText() {
    transactions.save(
        TransactionMother.expense()
            .withDescription("Café")
            .withAmount(new BigDecimal("12.00"))
            .build());
    transactions.save(
        TransactionMother.expense()
            .withDescription("Aluguel")
            .withAmount(new BigDecimal("900.00"))
            .build());

    assertThat(transactions.sumAmountByType(textFilter("cafe")).get(CategoryType.EXPENSE))
        .isEqualByComparingTo("12.00");
  }

  @Test
  void findAllBreaksDateTiesByIdDescendingTheWayPostgresOrdersUuids() {
    LocalDate day = LocalDate.of(2026, 3, 10);
    List<UUID> ids = java.util.stream.Stream.generate(UUID::randomUUID).limit(20).toList();
    ids.forEach(
        id -> transactions.save(TransactionMother.expense().withId(id).withDate(day).build()));

    List<String> returned =
        transactions.findAll(TransactionFilter.none(), Pageable.unpaged()).stream()
            .map(t -> t.getId().toString())
            .toList();

    assertThat(returned).isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }
}
