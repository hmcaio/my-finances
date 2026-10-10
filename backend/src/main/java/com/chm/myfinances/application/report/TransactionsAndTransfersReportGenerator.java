package com.chm.myfinances.application.report;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Report 1 (F030 spec): every transaction and transfer, merged into one chronological statement
 * with a {@code type} column distinguishing the two - filtered by date range + account + category
 * (a transfer has no category, so the category filter naturally only narrows the transaction side,
 * same "ignored where it has no dimension" rule the spec states generally).
 */
@Service
public class TransactionsAndTransfersReportGenerator implements ReportGenerator {

  private static final List<String> COLUMNS =
      List.of(
          "date",
          "type",
          "description",
          "additional_notes",
          "amount",
          "category_name",
          "account_name",
          "from_account_name",
          "to_account_name");

  private final TransactionRepository transactionRepository;
  private final TransferRepository transferRepository;
  private final CategoryRepository categoryRepository;
  private final AccountRepository accountRepository;

  public TransactionsAndTransfersReportGenerator(
      TransactionRepository transactionRepository,
      TransferRepository transferRepository,
      CategoryRepository categoryRepository,
      AccountRepository accountRepository) {
    this.transactionRepository = transactionRepository;
    this.transferRepository = transferRepository;
    this.categoryRepository = categoryRepository;
    this.accountRepository = accountRepository;
  }

  @Override
  public ReportType type() {
    return ReportType.TRANSACTIONS_TRANSFERS;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    Map<UUID, String> categoryNames =
        nameIndex(categoryRepository.findAll(), Category::getId, Category::getName);
    Map<UUID, String> accountNames =
        nameIndex(accountRepository.findAll(), Account::getId, Account::getName);

    List<Row> rows = new ArrayList<>();
    for (Transaction t :
        transactionRepository
            .findAll(
                new TransactionFilter(
                    filter.dateFrom(),
                    filter.dateTo(),
                    filter.categoryId(),
                    filter.accountId(),
                    null),
                Pageable.unpaged())
            .getContent()) {
      rows.add(
          new Row(
              t.getDate(),
              "TRANSACTION",
              t.getDescription(),
              t.getAdditionalNotes(),
              t.getAmount(),
              categoryNames.get(t.getCategoryId()),
              accountNames.get(t.getAccountId()),
              null,
              null));
    }
    for (Transfer t :
        transferRepository
            .findAll(
                new TransferFilter(filter.dateFrom(), filter.dateTo(), filter.accountId(), null),
                Pageable.unpaged())
            .getContent()) {
      rows.add(
          new Row(
              t.getDate(),
              "TRANSFER",
              t.getDescription(),
              t.getAdditionalNotes(),
              t.getAmount(),
              null,
              null,
              accountNames.get(t.getFromAccountId()),
              accountNames.get(t.getToAccountId())));
    }
    rows.sort(Comparator.comparing(Row::date).thenComparing(Row::type));

    List<List<Object>> dataRows = new ArrayList<>();
    for (Row row : rows) {
      dataRows.add(
          List.of(
              row.date(),
              row.type(),
              row.description() == null ? "" : row.description(),
              row.additionalNotes() == null ? "" : row.additionalNotes(),
              row.amount(),
              nullToEmpty(row.categoryName()),
              nullToEmpty(row.accountName()),
              nullToEmpty(row.fromAccountName()),
              nullToEmpty(row.toAccountName())));
    }
    byte[] csv =
        CsvSectionWriter.write(List.of(new CsvSectionWriter.Section(null, COLUMNS, dataRows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static <T> Map<UUID, String> nameIndex(
      List<T> rows, Function<T, UUID> id, Function<T, String> name) {
    Map<UUID, String> map = new HashMap<>();
    rows.forEach(r -> map.put(id.apply(r), name.apply(r)));
    return map;
  }

  private record Row(
      LocalDate date,
      String type,
      String description,
      String additionalNotes,
      BigDecimal amount,
      String categoryName,
      String accountName,
      String fromAccountName,
      String toAccountName) {}
}
