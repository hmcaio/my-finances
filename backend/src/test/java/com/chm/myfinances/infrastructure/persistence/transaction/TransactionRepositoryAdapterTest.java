package com.chm.myfinances.infrastructure.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Persistence-layer integration test for {@link TransactionRepositoryAdapter}: hits a real,
 * ephemeral Postgres via Testcontainers (ADR 0010), so Flyway's {@code V5__transactions.sql} runs
 * for real too, FK constraints included. Same {@code @SpringBootTest} +
 * {@code @Import(TestcontainersConfiguration.class)} + {@code @Transactional} pattern as F003's
 * {@code AccountRepositoryAdapterTest} (Spring Boot 4.x has no {@code @DataJpaTest} slice).
 *
 * <p>{@code transactions.category_id}/{@code account_id}/{@code payment_method_id} are real FKs, so
 * every test here persists a real {@link Category}/{@link Account}/{@link PaymentMethod} first via
 * their own repositories rather than referencing random UUIDs.
 */
@DatabaseIntegrationTest
class TransactionRepositoryAdapterTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private InvestmentHoldingRepository investmentHoldingRepository;
  @Autowired private InvestmentProductRepository investmentProductRepository;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;

  private UUID categoryId;
  private UUID otherCategoryId;
  private UUID accountId;
  private UUID otherAccountId;
  private UUID paymentMethodId;

  @BeforeEach
  void setUp() {
    categoryId = persistCategory("Groceries Test", CategoryType.EXPENSE).getId();
    otherCategoryId = persistCategory("Salary Test", CategoryType.INCOME).getId();
    accountId = persistAccount("Checking").getId();
    otherAccountId = persistAccount("Savings").getId();
    paymentMethodId = persistPaymentMethod("Debit Card Test").getId();
  }

  private Category persistCategory(String name, CategoryType type) {
    return TestFixtures.category(categoryRepository, name, type);
  }

  private Account persistAccount(String name) {
    return TestFixtures.checkingAccount(accountRepository, institutionRepository, name);
  }

  private PaymentMethod persistPaymentMethod(String name) {
    return TestFixtures.paymentMethod(paymentMethodRepository, name);
  }

  private Transaction newTransaction(LocalDate date, BigDecimal amount) {
    return newTransaction(
        date, amount, categoryId, CategoryType.EXPENSE, accountId, paymentMethodId);
  }

  private Transaction newTransaction(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId) {
    return Transaction.create(
        UUID.randomUUID(),
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        null,
        "Test transaction",
        null);
  }

  @Test
  void findDistinctDatesBetweenReturnsEachDateOnceWithinTheInclusiveRange() {
    transactionRepository.save(newTransaction(LocalDate.of(1990, 1, 9), new BigDecimal("1.00")));
    transactionRepository.save(newTransaction(LocalDate.of(1990, 1, 10), new BigDecimal("2.00")));
    transactionRepository.save(newTransaction(LocalDate.of(1990, 1, 10), new BigDecimal("3.00")));
    transactionRepository.save(newTransaction(LocalDate.of(1990, 1, 20), new BigDecimal("4.00")));
    transactionRepository.save(newTransaction(LocalDate.of(1990, 1, 21), new BigDecimal("5.00")));

    assertThat(
            transactionRepository.findDistinctDatesBetween(
                LocalDate.of(1990, 1, 10), LocalDate.of(1990, 1, 20)))
        .containsExactlyInAnyOrder(LocalDate.of(1990, 1, 10), LocalDate.of(1990, 1, 20));
  }

  @Test
  void savesAndReloadsATransaction() {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            new BigDecimal("42.50"),
            categoryId,
            CategoryType.EXPENSE,
            accountId,
            paymentMethodId,
            null,
            "Weekly groceries",
            "Bought extra for the weekend");

    transactionRepository.save(transaction);

    Optional<Transaction> reloaded = transactionRepository.findById(transaction.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    assertThat(reloaded.get().getAmount()).isEqualByComparingTo("42.50");
    assertThat(reloaded.get().getCategoryId()).isEqualTo(categoryId);
    assertThat(reloaded.get().getType()).isEqualTo(CategoryType.EXPENSE);
    assertThat(reloaded.get().getAccountId()).isEqualTo(accountId);
    assertThat(reloaded.get().getPaymentMethodId()).isEqualTo(paymentMethodId);
    assertThat(reloaded.get().getRecurringTemplateVersionId()).isNull();
    assertThat(reloaded.get().getDescription()).isEqualTo("Weekly groceries");
    assertThat(reloaded.get().getAdditionalNotes()).isEqualTo("Bought extra for the weekend");
  }

  @Test
  void savesAndReloadsAnInvestmentHoldingId() {
    InvestmentCategory category =
        investmentCategoryRepository.save(
            InvestmentCategory.reconstitute(UUID.randomUUID(), "Variable Income Txn Repo Test"));
    InvestmentProduct product =
        investmentProductRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(), category.getId(), null, "KNRI11 Txn Repo Test", null));
    Account investmentAccount =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "Broker Txn Repo Test",
            AccountType.INVESTMENT);
    InvestmentHolding holding =
        investmentHoldingRepository.save(
            InvestmentHolding.create(
                UUID.randomUUID(), product.getId(), investmentAccount.getId(), null));

    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 15),
            new BigDecimal("12.34"),
            otherCategoryId,
            CategoryType.INCOME,
            accountId,
            paymentMethodId,
            null,
            "Dividend",
            null,
            null,
            holding.getId());

    transactionRepository.save(transaction);

    Transaction reloaded = transactionRepository.findById(transaction.getId()).orElseThrow();
    assertThat(reloaded.getInvestmentHoldingId()).isEqualTo(holding.getId());
  }

  @Test
  void investmentHoldingIdIsNullWhenNeverSet() {
    Transaction transaction = newTransaction(LocalDate.of(2026, 1, 1), BigDecimal.TEN);
    transactionRepository.save(transaction);

    Transaction reloaded = transactionRepository.findById(transaction.getId()).orElseThrow();
    assertThat(reloaded.getInvestmentHoldingId()).isNull();
  }

  @Test
  void editPersists() {
    Transaction transaction = newTransaction(LocalDate.of(2026, 1, 1), BigDecimal.TEN);
    transactionRepository.save(transaction);

    transaction.edit(
        LocalDate.of(2026, 2, 2),
        new BigDecimal("20.00"),
        otherCategoryId,
        CategoryType.INCOME,
        transaction.getAccountId(),
        transaction.getPaymentMethodId(),
        "Edited description",
        "Edited notes");
    transactionRepository.save(transaction);

    Optional<Transaction> reloaded = transactionRepository.findById(transaction.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getDate()).isEqualTo(LocalDate.of(2026, 2, 2));
    assertThat(reloaded.get().getAmount()).isEqualByComparingTo("20.00");
    assertThat(reloaded.get().getCategoryId()).isEqualTo(otherCategoryId);
    assertThat(reloaded.get().getType()).isEqualTo(CategoryType.INCOME);
    assertThat(reloaded.get().getDescription()).isEqualTo("Edited description");
    assertThat(reloaded.get().getAdditionalNotes()).isEqualTo("Edited notes");
  }

  @Test
  void deleteByIdRemovesTheTransaction() {
    Transaction transaction = newTransaction(LocalDate.now(), BigDecimal.TEN);
    transactionRepository.save(transaction);

    transactionRepository.deleteById(transaction.getId());

    assertThat(transactionRepository.findById(transaction.getId())).isEmpty();
  }

  @Test
  void existsByIdReflectsPersistedState() {
    Transaction transaction = newTransaction(LocalDate.now(), BigDecimal.TEN);

    assertThat(transactionRepository.existsById(transaction.getId())).isFalse();

    transactionRepository.save(transaction);

    assertThat(transactionRepository.existsById(transaction.getId())).isTrue();
  }

  @Test
  void findByAccountIdOnOrBeforeExcludesOtherAccountsAndLaterDates() {
    Transaction inRange = newTransaction(LocalDate.of(2026, 1, 10), BigDecimal.TEN);
    Transaction onBoundary = newTransaction(LocalDate.of(2026, 1, 15), BigDecimal.TEN);
    Transaction afterAsOf = newTransaction(LocalDate.of(2026, 1, 20), BigDecimal.TEN);
    Transaction otherAccount =
        newTransaction(
            LocalDate.of(2026, 1, 10),
            BigDecimal.TEN,
            categoryId,
            CategoryType.EXPENSE,
            otherAccountId,
            paymentMethodId);
    transactionRepository.save(inRange);
    transactionRepository.save(onBoundary);
    transactionRepository.save(afterAsOf);
    transactionRepository.save(otherAccount);

    var result =
        transactionRepository.findByAccountIdOnOrBefore(accountId, LocalDate.of(2026, 1, 15));

    assertThat(result)
        .extracting(Transaction::getId)
        .containsExactlyInAnyOrder(inRange.getId(), onBoundary.getId());
  }

  @Test
  void existsByCategoryIdAndExistsByPaymentMethodIdReflectPersistedState() {
    UUID freshCategoryId = persistCategory("Fresh Category", CategoryType.EXPENSE).getId();
    UUID freshPaymentMethodId = persistPaymentMethod("Fresh Payment Method").getId();

    assertThat(transactionRepository.existsByCategoryId(freshCategoryId)).isFalse();
    assertThat(transactionRepository.existsByPaymentMethodId(freshPaymentMethodId)).isFalse();

    transactionRepository.save(
        newTransaction(
            LocalDate.now(),
            BigDecimal.TEN,
            freshCategoryId,
            CategoryType.EXPENSE,
            accountId,
            freshPaymentMethodId));

    assertThat(transactionRepository.existsByCategoryId(freshCategoryId)).isTrue();
    assertThat(transactionRepository.existsByPaymentMethodId(freshPaymentMethodId)).isTrue();
  }

  @Test
  void findAllFiltersByEveryDimensionAndPaginates() {
    Transaction matching1 = newTransaction(LocalDate.of(2026, 2, 1), BigDecimal.TEN);
    Transaction matching2 = newTransaction(LocalDate.of(2026, 2, 15), BigDecimal.TEN);
    Transaction wrongCategory =
        newTransaction(
            LocalDate.of(2026, 2, 10),
            BigDecimal.TEN,
            otherCategoryId,
            CategoryType.INCOME,
            accountId,
            paymentMethodId);
    Transaction outOfDateRange = newTransaction(LocalDate.of(2026, 3, 1), BigDecimal.TEN);
    transactionRepository.save(matching1);
    transactionRepository.save(matching2);
    transactionRepository.save(wrongCategory);
    transactionRepository.save(outOfDateRange);

    TransactionFilter filter =
        new TransactionFilter(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 28),
            categoryId,
            accountId,
            paymentMethodId);

    Page<Transaction> page = transactionRepository.findAll(filter, PageRequest.of(0, 1));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getTotalPages()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(1);
  }

  @Test
  void findAllWithNoFilterReturnsEverything() {
    transactionRepository.save(newTransaction(LocalDate.now(), BigDecimal.TEN));
    transactionRepository.save(
        newTransaction(
            LocalDate.now(),
            BigDecimal.TEN,
            otherCategoryId,
            CategoryType.INCOME,
            accountId,
            paymentMethodId));

    Page<Transaction> page =
        transactionRepository.findAll(TransactionFilter.none(), PageRequest.of(0, 20));

    assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void sumAmountByCategoryAndDateRangeSumsOnlyMatchingCategoryWithinRange() {
    transactionRepository.save(newTransaction(LocalDate.of(2026, 2, 1), new BigDecimal("10.00")));
    transactionRepository.save(newTransaction(LocalDate.of(2026, 2, 28), new BigDecimal("5.00")));
    // Out of range, wrong category, and no transactions at all for a fresh category - all must
    // not contribute to the sum.
    transactionRepository.save(newTransaction(LocalDate.of(2026, 3, 1), new BigDecimal("99.00")));
    transactionRepository.save(
        newTransaction(
            LocalDate.of(2026, 2, 15),
            new BigDecimal("77.00"),
            otherCategoryId,
            CategoryType.INCOME,
            accountId,
            paymentMethodId));

    assertThat(
            transactionRepository.sumAmountByCategoryAndDateRange(
                categoryId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .isEqualByComparingTo("15.00");
    assertThat(
            transactionRepository.sumAmountByCategoryAndDateRange(
                persistCategory("Untouched Category", CategoryType.EXPENSE).getId(),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28)))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void sumExpenseAmountByCategoryForDateRangeGroupsByCategoryAndExcludesIncome() {
    UUID expenseCategory2 = persistCategory("Transport Test", CategoryType.EXPENSE).getId();
    transactionRepository.save(newTransaction(LocalDate.of(2026, 4, 1), new BigDecimal("10.00")));
    transactionRepository.save(newTransaction(LocalDate.of(2026, 4, 2), new BigDecimal("3.50")));
    transactionRepository.save(
        newTransaction(
            LocalDate.of(2026, 4, 3),
            new BigDecimal("20.00"),
            expenseCategory2,
            CategoryType.EXPENSE,
            accountId,
            paymentMethodId));
    transactionRepository.save(
        newTransaction(
            LocalDate.of(2026, 4, 4),
            new BigDecimal("500.00"),
            otherCategoryId,
            CategoryType.INCOME,
            accountId,
            paymentMethodId));
    // Outside the range - must not affect the totals.
    transactionRepository.save(newTransaction(LocalDate.of(2026, 5, 1), new BigDecimal("999.00")));

    var totals =
        transactionRepository.sumExpenseAmountByCategoryForDateRange(
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

    // Scale-tolerant (isEqualByComparingTo), since SUM(numeric) in Postgres may not come back at
    // exactly the same scale as the stored column - containsEntry's Map.equals would be brittle.
    assertThat(totals).hasSize(2).doesNotContainKey(otherCategoryId);
    assertThat(totals.get(categoryId)).isEqualByComparingTo("13.50");
    assertThat(totals.get(expenseCategory2)).isEqualByComparingTo("20.00");
  }

  @Test
  void findAllFiltersByType() {
    transactionRepository.save(newTransaction(LocalDate.of(2091, 1, 1), BigDecimal.TEN));
    Transaction income =
        newTransaction(
            LocalDate.of(2091, 1, 2),
            BigDecimal.TEN,
            otherCategoryId,
            CategoryType.INCOME,
            accountId,
            paymentMethodId);
    transactionRepository.save(income);

    TransactionFilter incomeOnly =
        new TransactionFilter(
            LocalDate.of(2091, 1, 1),
            LocalDate.of(2091, 1, 31),
            null,
            accountId,
            null,
            CategoryType.INCOME,
            null);

    assertThat(transactionRepository.findAll(incomeOnly, PageRequest.of(0, 20)).getContent())
        .extracting(Transaction::getId)
        .containsExactly(income.getId());
  }

  @Test
  void sumAmountByTypeTotalsTheWholeFilteredScopeAndOmitsAnAbsentType() {
    transactionRepository.save(newTransaction(LocalDate.of(2092, 2, 1), new BigDecimal("10.00")));
    transactionRepository.save(newTransaction(LocalDate.of(2092, 2, 2), new BigDecimal("3.50")));
    transactionRepository.save(
        newTransaction(
            LocalDate.of(2092, 2, 3),
            new BigDecimal("500.00"),
            otherCategoryId,
            CategoryType.INCOME,
            accountId,
            paymentMethodId));
    // Other account and other month: outside the filter, must not contribute.
    transactionRepository.save(
        newTransaction(
            LocalDate.of(2092, 2, 4),
            new BigDecimal("77.00"),
            categoryId,
            CategoryType.EXPENSE,
            otherAccountId,
            paymentMethodId));
    transactionRepository.save(newTransaction(LocalDate.of(2092, 3, 1), new BigDecimal("999.00")));

    var totals =
        transactionRepository.sumAmountByType(
            new TransactionFilter(
                LocalDate.of(2092, 2, 1), LocalDate.of(2092, 2, 28), null, accountId, null));
    var expenseOnly =
        transactionRepository.sumAmountByType(
            new TransactionFilter(
                LocalDate.of(2092, 2, 1),
                LocalDate.of(2092, 2, 28),
                null,
                accountId,
                null,
                CategoryType.EXPENSE,
                null));
    var none =
        transactionRepository.sumAmountByType(
            new TransactionFilter(
                LocalDate.of(2093, 1, 1), LocalDate.of(2093, 1, 31), null, accountId, null));

    assertThat(totals).hasSize(2);
    assertThat(totals.get(CategoryType.EXPENSE)).isEqualByComparingTo("13.50");
    assertThat(totals.get(CategoryType.INCOME)).isEqualByComparingTo("500.00");
    assertThat(expenseOnly).containsOnlyKeys(CategoryType.EXPENSE);
    assertThat(none).isEmpty();
  }

  /**
   * F037 open question, confirmed: Postgres orders {@code uuid} bytewise, which equals comparing
   * the lower-case canonical strings - so {@code LedgerQuery}'s Java tie-break on {@code
   * id.toString()} agrees with the {@code id DESC} page order the repositories return.
   */
  @Test
  void idDescendingOrdersUuidsLikeTheirLowerCaseStrings() {
    LocalDate day = LocalDate.of(2095, 6, 1);
    for (int i = 0; i < 40; i++) {
      transactionRepository.save(newTransaction(day, BigDecimal.ONE));
    }
    TransactionFilter filter = new TransactionFilter(day, day, null, accountId, null);

    List<String> ids =
        transactionRepository
            .findAll(
                filter,
                PageRequest.of(0, 100, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"))))
            .map(t -> t.getId().toString())
            .getContent();

    assertThat(ids).hasSize(40).isSortedAccordingTo(Comparator.reverseOrder());
  }

  private Transaction saveDescribed(
      LocalDate date, String description, String notes, CategoryType type, String amount) {
    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            date,
            new BigDecimal(amount),
            type == CategoryType.EXPENSE ? categoryId : otherCategoryId,
            type,
            accountId,
            paymentMethodId,
            null,
            description,
            notes);
    return transactionRepository.save(transaction);
  }

  private List<UUID> searchIds(String text, LocalDate from, LocalDate to, CategoryType type) {
    return transactionRepository
        .findAll(
            new TransactionFilter(from, to, null, accountId, null, type, text),
            PageRequest.of(0, 50))
        .map(Transaction::getId)
        .getContent();
  }

  @Test
  void searchIgnoresCaseAndAccentsOnTheDescription() {
    LocalDate day = LocalDate.of(2096, 1, 5);
    Transaction cafe = saveDescribed(day, "Café da manhã", null, CategoryType.EXPENSE, "10.00");
    saveDescribed(day, "Aluguel", null, CategoryType.EXPENSE, "900.00");

    assertThat(searchIds("cafe", day, day, null)).containsExactly(cafe.getId());
    assertThat(searchIds("CAFÉ", day, day, null)).containsExactly(cafe.getId());
    assertThat(searchIds("manha", day, day, null)).containsExactly(cafe.getId());
    assertThat(searchIds("açaí", day, day, null)).isEmpty();
  }

  @Test
  void searchFoldsAnUnaccentedDescriptionAgainstAnAccentedQuery() {
    LocalDate day = LocalDate.of(2096, 1, 6);
    Transaction cafe = saveDescribed(day, "cafe com leite", null, CategoryType.EXPENSE, "10.00");

    assertThat(searchIds("CAFÉ", day, day, null)).containsExactly(cafe.getId());
  }

  @Test
  void searchMatchesAdditionalNotesAndANullNotesColumnNeverErrors() {
    LocalDate day = LocalDate.of(2096, 1, 7);
    Transaction viaNotes =
        saveDescribed(day, "Padaria", "pão e açaí", CategoryType.EXPENSE, "5.00");
    saveDescribed(day, "No notes at all", null, CategoryType.EXPENSE, "5.00");

    assertThat(searchIds("ACAI", day, day, null)).containsExactly(viaNotes.getId());
  }

  @Test
  void searchTreatsPercentUnderscoreAndBackslashLiterally() {
    LocalDate day = LocalDate.of(2096, 1, 8);
    Transaction percent = saveDescribed(day, "50% off", null, CategoryType.EXPENSE, "5.00");
    Transaction underscore = saveDescribed(day, "a_b", null, CategoryType.EXPENSE, "5.00");
    Transaction backslash = saveDescribed(day, "c:\\dir", null, CategoryType.EXPENSE, "5.00");
    saveDescribed(day, "plain", null, CategoryType.EXPENSE, "5.00");

    assertThat(searchIds("%", day, day, null)).containsExactly(percent.getId());
    assertThat(searchIds("50% o", day, day, null)).containsExactly(percent.getId());
    assertThat(searchIds("_", day, day, null)).containsExactly(underscore.getId());
    assertThat(searchIds("\\", day, day, null)).containsExactly(backslash.getId());
    assertThat(searchIds("a%b", day, day, null)).isEmpty();
  }

  @Test
  void searchCombinesWithTheDateRangeAndTypeFilters() {
    LocalDate inRange = LocalDate.of(2096, 2, 10);
    LocalDate outOfRange = LocalDate.of(2096, 4, 10);
    LocalDate from = LocalDate.of(2096, 2, 1);
    LocalDate to = LocalDate.of(2096, 2, 28);
    Transaction expense = saveDescribed(inRange, "Café", null, CategoryType.EXPENSE, "10.00");
    saveDescribed(inRange, "Café refund", null, CategoryType.INCOME, "3.00");
    saveDescribed(outOfRange, "Café", null, CategoryType.EXPENSE, "10.00");

    assertThat(searchIds("cafe", from, to, CategoryType.EXPENSE)).containsExactly(expense.getId());
    assertThat(searchIds("cafe", from, to, null)).hasSize(2);
  }

  @Test
  void aBlankSearchTextDoesNotNarrow() {
    LocalDate day = LocalDate.of(2096, 1, 9);
    saveDescribed(day, "One", null, CategoryType.EXPENSE, "1.00");
    saveDescribed(day, "Two", null, CategoryType.EXPENSE, "1.00");

    assertThat(searchIds("   ", day, day, null)).hasSize(2);
  }

  @Test
  void theSummaryRespectsTheSearchText() {
    LocalDate day = LocalDate.of(2096, 3, 3);
    saveDescribed(day, "Café", null, CategoryType.EXPENSE, "12.00");
    saveDescribed(day, "Aluguel", null, CategoryType.EXPENSE, "900.00");
    saveDescribed(day, "Café refund", null, CategoryType.INCOME, "2.50");

    var totals =
        transactionRepository.sumAmountByType(
            new TransactionFilter(day, day, null, accountId, null, null, "cafe"));

    assertThat(totals.get(CategoryType.EXPENSE)).isEqualByComparingTo("12.00");
    assertThat(totals.get(CategoryType.INCOME)).isEqualByComparingTo("2.50");
  }
}
