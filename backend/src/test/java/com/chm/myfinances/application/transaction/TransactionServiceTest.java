package com.chm.myfinances.application.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.paymentmethod.PaymentMethodNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Application-layer tests for {@link TransactionService}, written first (ADR 0004) against
 * hand-written fakes for every repository port it depends on - plain JUnit, no Spring context.
 */
class TransactionServiceTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakePaymentMethodRepository paymentMethodRepository = new FakePaymentMethodRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final TransactionService service =
      new TransactionService(
          transactionRepository, categoryRepository, accountRepository, paymentMethodRepository, idGenerator);

  private Category expenseCategory;
  private Category incomeCategory;
  private Account openAccount;
  private Account closedAccount;
  private PaymentMethod paymentMethod;

  @BeforeEach
  void setUp() {
    expenseCategory = categoryRepository.save(Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE));
    incomeCategory = categoryRepository.save(Category.create(UUID.randomUUID(), "Salary", CategoryType.INCOME));
    openAccount =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(), "Checking", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now()));
    closedAccount =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(), "Old", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now()));
    closedAccount.close();
    accountRepository.save(closedAccount);
    paymentMethod = paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Debit Card"));
  }

  @Test
  void createAssignsIdFromIdGeneratorAndDerivesTypeFromCategory() {
    UUID nextId = UUID.randomUUID();
    TransactionService service =
        new TransactionService(
            transactionRepository,
            categoryRepository,
            accountRepository,
            paymentMethodRepository,
            new FakeIdGenerator(nextId));

    Transaction created =
        service.create(
            LocalDate.of(2026, 3, 15),
            new BigDecimal("42.50"),
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Weekly groceries");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getType()).isEqualTo(CategoryType.EXPENSE);
    assertThat(transactionRepository.findById(nextId)).isPresent();
  }

  @Test
  void createDerivesIncomeTypeFromAnIncomeCategory() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            incomeCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            null);

    assertThat(created.getType()).isEqualTo(CategoryType.INCOME);
  }

  @Test
  void createRejectsUnknownCategory() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    UUID.randomUUID(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void createRejectsUnknownAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    UUID.randomUUID(),
                    paymentMethod.getId(),
                    null))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void createRejectsUnknownPaymentMethod() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    openAccount.getId(),
                    UUID.randomUUID(),
                    null))
        .isInstanceOf(PaymentMethodNotFoundException.class);
  }

  @Test
  void createRejectsAClosedAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    closedAccount.getId(),
                    paymentMethod.getId(),
                    null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  void editUpdatesFieldsAndReDerivesType() {
    Transaction created =
        service.create(
            LocalDate.of(2026, 1, 1),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Original");

    Transaction edited =
        service.edit(
            created.getId(),
            LocalDate.of(2026, 2, 2),
            new BigDecimal("20.00"),
            incomeCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Edited");

    assertThat(edited.getDate()).isEqualTo(LocalDate.of(2026, 2, 2));
    assertThat(edited.getAmount()).isEqualByComparingTo("20.00");
    assertThat(edited.getCategoryId()).isEqualTo(incomeCategory.getId());
    assertThat(edited.getType()).isEqualTo(CategoryType.INCOME);
    assertThat(edited.getNote()).isEqualTo("Edited");
  }

  @Test
  void editOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(
            () ->
                service.edit(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  void editRejectsMovingATransactionOntoAClosedAccount() {
    Transaction created =
        service.create(
            LocalDate.now(), BigDecimal.TEN, expenseCategory.getId(), openAccount.getId(), paymentMethod.getId(), null);

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    closedAccount.getId(),
                    paymentMethod.getId(),
                    null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void deleteRemovesTheTransaction() {
    Transaction created =
        service.create(
            LocalDate.now(), BigDecimal.TEN, expenseCategory.getId(), openAccount.getId(), paymentMethod.getId(), null);

    service.delete(created.getId());

    assertThat(transactionRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  void findAllDelegatesToRepositoryWithFilterAndPageable() {
    service.create(
        LocalDate.of(2026, 1, 1), BigDecimal.TEN, expenseCategory.getId(), openAccount.getId(), paymentMethod.getId(), null);
    service.create(
        LocalDate.of(2026, 2, 1), BigDecimal.TEN, incomeCategory.getId(), openAccount.getId(), paymentMethod.getId(), null);

    Page<Transaction> page =
        service.findAll(
            new TransactionFilter(null, null, expenseCategory.getId(), null, null), PageRequest.of(0, 20));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).extracting(Transaction::getCategoryId).containsExactly(expenseCategory.getId());
  }

  private static final class FakeCategoryRepository implements CategoryRepository {
    private final Map<UUID, Category> store = new HashMap<>();

    @Override
    public Category save(Category category) {
      store.put(category.getId(), category);
      return category;
    }

    @Override
    public Optional<Category> findById(UUID id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Category> findAll() {
      return List.copyOf(store.values());
    }

    @Override
    public void deleteById(UUID id) {
      store.remove(id);
    }

    @Override
    public boolean existsById(UUID id) {
      return store.containsKey(id);
    }
  }

  private static final class FakeAccountRepository implements AccountRepository {
    private final Map<UUID, Account> store = new HashMap<>();

    @Override
    public Account save(Account account) {
      store.put(account.getId(), account);
      return account;
    }

    @Override
    public Optional<Account> findById(UUID id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Account> findAll() {
      return List.copyOf(store.values());
    }

    @Override
    public boolean existsById(UUID id) {
      return store.containsKey(id);
    }
  }

  private static final class FakePaymentMethodRepository implements PaymentMethodRepository {
    private final Map<UUID, PaymentMethod> store = new HashMap<>();

    @Override
    public PaymentMethod save(PaymentMethod paymentMethod) {
      store.put(paymentMethod.getId(), paymentMethod);
      return paymentMethod;
    }

    @Override
    public Optional<PaymentMethod> findById(UUID id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<PaymentMethod> findAll() {
      return List.copyOf(store.values());
    }

    @Override
    public void deleteById(UUID id) {
      store.remove(id);
    }

    @Override
    public boolean existsById(UUID id) {
      return store.containsKey(id);
    }
  }
}
