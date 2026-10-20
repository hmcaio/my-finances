package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TransactionsAndTransfersReportGenerator} (F030 spec), written first (ADR 0004):
 * transactions and transfers merge into one date-sorted CSV with a {@code type} column, and the
 * date/account/category filters narrow each side correctly. Fixture names never contain
 * commas/quotes, so rows are split naively here - same convention F013's {@code
 * DataExportServiceTest} used.
 */
class TransactionsAndTransfersReportGeneratorTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final TransactionsAndTransfersReportGenerator generator =
      new TransactionsAndTransfersReportGenerator(
          transactionRepository, transferRepository, categoryRepository, accountRepository);

  @Test
  void mergesTransactionsAndTransfersSortedByDateWithATypeColumn() {
    Category groceries =
        categoryRepository.save(
            Category.create(UUID.randomUUID(), "Groceries R Test", CategoryType.EXPENSE));
    Account checking =
        accountRepository.save(AccountMother.checking().withName("Checking R Test").build());
    Account savings =
        accountRepository.save(AccountMother.savings().withName("Savings R Test").build());
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(LocalDate.of(2026, 3, 5))
            .withCategoryId(groceries.getId())
            .withAccountId(checking.getId())
            .withAmount(new BigDecimal("50.00"))
            .withDescription("Supermarket")
            .build());
    transferRepository.save(
        TransferMother.transfer()
            .withDate(LocalDate.of(2026, 3, 1))
            .withFromAccountId(checking.getId())
            .withToAccountId(savings.getId())
            .withAmount(new BigDecimal("200.00"))
            .withDescription("Move to savings")
            .build());

    List<GeneratedFile> files = generator.generate(ReportFilter.none());

    assertThat(files).hasSize(1);
    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).get("type")).isEqualTo("TRANSFER");
    assertThat(rows.get(0).get("date")).isEqualTo("2026-03-01");
    assertThat(rows.get(0).get("from_account_name")).isEqualTo("Checking R Test");
    assertThat(rows.get(0).get("to_account_name")).isEqualTo("Savings R Test");
    assertThat(rows.get(1).get("type")).isEqualTo("TRANSACTION");
    assertThat(rows.get(1).get("date")).isEqualTo("2026-03-05");
    assertThat(rows.get(1).get("category_name")).isEqualTo("Groceries R Test");
    assertThat(rows.get(1).get("account_name")).isEqualTo("Checking R Test");
    assertThat(rows.get(1).get("amount")).isEqualTo("50.00");
  }

  @Test
  void categoryFilterNarrowsOnlyTransactionsNotTransfers() {
    Category groceries =
        categoryRepository.save(
            Category.create(UUID.randomUUID(), "Groceries R Test 2", CategoryType.EXPENSE));
    Category other =
        categoryRepository.save(
            Category.create(UUID.randomUUID(), "Other R Test 2", CategoryType.EXPENSE));
    Account checking =
        accountRepository.save(AccountMother.checking().withName("Checking R Test 2").build());
    Account savings =
        accountRepository.save(AccountMother.savings().withName("Savings R Test 2").build());
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(LocalDate.of(2026, 3, 5))
            .withCategoryId(groceries.getId())
            .withAccountId(checking.getId())
            .build());
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(LocalDate.of(2026, 3, 6))
            .withCategoryId(other.getId())
            .withAccountId(checking.getId())
            .build());
    transferRepository.save(
        TransferMother.transfer()
            .withDate(LocalDate.of(2026, 3, 1))
            .withFromAccountId(checking.getId())
            .withToAccountId(savings.getId())
            .build());

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(null, null, null, null, groceries.getId(), null, null, null));

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    // Both the transfer (category filter doesn't apply to it) and the matching transaction.
    assertThat(rows).hasSize(2);
    assertThat(rows).anySatisfy(r -> assertThat(r.get("type")).isEqualTo("TRANSFER"));
    assertThat(rows)
        .anySatisfy(
            r -> {
              assertThat(r.get("type")).isEqualTo("TRANSACTION");
              assertThat(r.get("category_name")).isEqualTo("Groceries R Test 2");
            });
  }
}
