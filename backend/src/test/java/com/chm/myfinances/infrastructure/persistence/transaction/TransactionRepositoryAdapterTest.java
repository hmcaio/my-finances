package com.chm.myfinances.infrastructure.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.testsupport.TestInstitutions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

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
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TransactionRepositoryAdapterTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;

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
    return categoryRepository.save(Category.create(UUID.randomUUID(), name, type));
  }

  private Account persistAccount(String name) {
    return accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            name,
            TestInstitutions.builtInId(institutionRepository),
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now()));
  }

  private PaymentMethod persistPaymentMethod(String name) {
    return paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), name));
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
}
