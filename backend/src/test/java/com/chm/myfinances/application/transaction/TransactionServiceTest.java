package com.chm.myfinances.application.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.application.paymentmethod.PaymentMethodNotFoundException;
import com.chm.myfinances.application.vehicle.VehicleNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakePaymentMethodRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeVehicleRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
  private final FakePaymentMethodRepository paymentMethodRepository =
      new FakePaymentMethodRepository();
  private final FakeVehicleRepository vehicleRepository = new FakeVehicleRepository();
  private final FakeInvestmentHoldingRepository investmentHoldingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final TransactionService service =
      new TransactionService(
          transactionRepository,
          categoryRepository,
          accountRepository,
          paymentMethodRepository,
          vehicleRepository,
          investmentHoldingRepository,
          idGenerator);

  private Category expenseCategory;
  private Category incomeCategory;
  private Category fuelCategory;
  private Category dividendCategory;
  private Account openAccount;
  private Account closedAccount;
  private PaymentMethod paymentMethod;
  private Vehicle vehicle;
  private InvestmentHolding holding;

  @BeforeEach
  void setUp() {
    expenseCategory =
        categoryRepository.save(
            Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE));
    incomeCategory =
        categoryRepository.save(Category.create(UUID.randomUUID(), "Salary", CategoryType.INCOME));
    fuelCategory =
        categoryRepository.save(
            Category.reconstitute(UUID.randomUUID(), "Fuel", CategoryType.EXPENSE, false, true));
    dividendCategory =
        categoryRepository.save(
            Category.reconstitute(
                UUID.randomUUID(), "Dividends", CategoryType.INCOME, false, false, true));
    openAccount = accountRepository.save(AccountMother.checking().build());
    closedAccount = accountRepository.save(AccountMother.checking().withName("Old").build());
    closedAccount.close(LocalDate.now());
    accountRepository.save(closedAccount);
    paymentMethod =
        paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Debit Card"));
    vehicle = vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Civic"));
    holding =
        investmentHoldingRepository.save(
            InvestmentHolding.create(
                UUID.randomUUID(), UUID.randomUUID(), openAccount.getId(), null));
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
            vehicleRepository,
            investmentHoldingRepository,
            new FakeIdGenerator(nextId));

    Transaction created =
        service.create(
            LocalDate.of(2026, 3, 15),
            new BigDecimal("42.50"),
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Weekly groceries",
            null);

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getType()).isEqualTo(CategoryType.EXPENSE);
    assertThat(transactionRepository.findById(nextId)).isPresent();
  }

  @Test
  void createWithARecurringTemplateVersionIdLinksTheResultingTransactionToIt() {
    UUID recurringTemplateVersionId = UUID.randomUUID();

    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            recurringTemplateVersionId,
            "Rent",
            null);

    assertThat(created.getRecurringTemplateVersionId()).isEqualTo(recurringTemplateVersionId);
  }

  @Test
  void createWithoutARecurringTemplateVersionIdLeavesItNull() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Groceries",
            null);

    assertThat(created.getRecurringTemplateVersionId()).isNull();
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
            "Salary",
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
                    "Groceries",
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
                    "Groceries",
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
                    "Groceries",
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
                    "Groceries",
                    null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void createRejectsAnInvestmentAccount() {
    Account investment = accountRepository.save(AccountMother.investment().build());

    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    investment.getId(),
                    paymentMethod.getId(),
                    "Groceries",
                    null))
        .isInstanceOf(AccountTypeNotAllowedException.class);
    assertThat(transactionRepository.findByAccountIdOnOrBefore(investment.getId(), LocalDate.now()))
        .isEmpty();
  }

  @Test
  void editRejectsMovingATransactionOntoAnInvestmentAccount() {
    Account investment = accountRepository.save(AccountMother.investment().build());
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Groceries",
            null);

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    investment.getId(),
                    paymentMethod.getId(),
                    "Groceries",
                    null))
        .isInstanceOf(AccountTypeNotAllowedException.class);
    assertThat(service.findById(created.getId()).getAccountId()).isEqualTo(openAccount.getId());
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
            "Original description",
            "Original note");

    Transaction edited =
        service.edit(
            created.getId(),
            LocalDate.of(2026, 2, 2),
            new BigDecimal("20.00"),
            incomeCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Edited description",
            "Edited note");

    assertThat(edited.getDate()).isEqualTo(LocalDate.of(2026, 2, 2));
    assertThat(edited.getAmount()).isEqualByComparingTo("20.00");
    assertThat(edited.getCategoryId()).isEqualTo(incomeCategory.getId());
    assertThat(edited.getType()).isEqualTo(CategoryType.INCOME);
    assertThat(edited.getDescription()).isEqualTo("Edited description");
    assertThat(edited.getAdditionalNotes()).isEqualTo("Edited note");
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
                    "Groceries",
                    null))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  void editRejectsMovingATransactionOntoAClosedAccount() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Groceries",
            null);

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    closedAccount.getId(),
                    paymentMethod.getId(),
                    "Groceries",
                    null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void deleteRemovesTheTransaction() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Groceries",
            null);

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
        LocalDate.of(2026, 1, 1),
        BigDecimal.TEN,
        expenseCategory.getId(),
        openAccount.getId(),
        paymentMethod.getId(),
        "Groceries",
        null);
    service.create(
        LocalDate.of(2026, 2, 1),
        BigDecimal.TEN,
        incomeCategory.getId(),
        openAccount.getId(),
        paymentMethod.getId(),
        "Salary",
        null);

    Page<Transaction> page =
        service.findAll(
            new TransactionFilter(null, null, expenseCategory.getId(), null, null),
            PageRequest.of(0, 20));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent())
        .extracting(Transaction::getCategoryId)
        .containsExactly(expenseCategory.getId());
  }

  // F024 (ADR 0021): the fuel invariant - fuelDetails present iff the category is the fuel
  // category - on both create and edit, plus the vehicle existence check.

  private FuelDetails fuelDetails() {
    return new FuelDetails(
        vehicle.getId(),
        FuelType.GASOLINA,
        new BigDecimal("40.5"),
        new BigDecimal("5.79"),
        null,
        null);
  }

  @Test
  void createWithFuelCategoryAndFuelDetailsSucceeds() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            fuelCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            null,
            "Fill up",
            null,
            fuelDetails());

    assertThat(created.getFuelDetails()).isEqualTo(fuelDetails());
  }

  @Test
  void createRejectsFuelCategoryWithoutFuelDetails() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    fuelCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null,
                    "Fill up",
                    null,
                    null))
        .isInstanceOf(FuelDetailsCategoryMismatchException.class);
  }

  @Test
  void createRejectsNonFuelCategoryWithFuelDetails() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null,
                    "Groceries",
                    null,
                    fuelDetails()))
        .isInstanceOf(FuelDetailsCategoryMismatchException.class);
  }

  @Test
  void createRejectsAnUnknownVehicleId() {
    FuelDetails unknownVehicle =
        new FuelDetails(
            UUID.randomUUID(), FuelType.GASOLINA, BigDecimal.TEN, BigDecimal.ONE, null, null);

    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    fuelCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null,
                    "Fill up",
                    null,
                    unknownVehicle))
        .isInstanceOf(VehicleNotFoundException.class);
  }

  @Test
  void plainCreateOverloadsAreRejectedWhenTargetingTheFuelCategory() {
    // The 7-arg and 8-arg overloads (RecurringTemplateService's call path) never carry
    // fuelDetails, so targeting the fuel category through them is a mismatch too - the invariant
    // applies uniformly.
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    fuelCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    "Fill up",
                    null))
        .isInstanceOf(FuelDetailsCategoryMismatchException.class);
  }

  @Test
  void editWithFuelCategoryAndFuelDetailsSucceeds() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Groceries",
            null);

    Transaction edited =
        service.edit(
            created.getId(),
            LocalDate.now(),
            BigDecimal.TEN,
            fuelCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Fill up",
            null,
            fuelDetails());

    assertThat(edited.getFuelDetails()).isEqualTo(fuelDetails());
  }

  @Test
  void editRejectsMovingAFuelTransactionsCategoryAwayWithoutClearingFuelDetails() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            fuelCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            null,
            "Fill up",
            null,
            fuelDetails());

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    expenseCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    "Groceries",
                    null,
                    fuelDetails()))
        .isInstanceOf(FuelDetailsCategoryMismatchException.class);
    // Rejected before any write: the transaction still has its original category and fuelDetails.
    assertThat(service.findById(created.getId()).getCategoryId()).isEqualTo(fuelCategory.getId());
  }

  @Test
  void editMovingAFuelTransactionsCategoryAwayAfterClearingFuelDetailsSucceeds() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            fuelCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            null,
            "Fill up",
            null,
            fuelDetails());

    Transaction edited =
        service.edit(
            created.getId(),
            LocalDate.now(),
            BigDecimal.TEN,
            expenseCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Groceries",
            null);

    assertThat(edited.getCategoryId()).isEqualTo(expenseCategory.getId());
    assertThat(edited.getFuelDetails()).isNull();
  }

  @Test
  void findFuelHistoryReturnsOnlyThatVehiclesFuelTransactionsOrderedByDate() {
    Vehicle otherVehicle = vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Corolla"));
    service.create(
        LocalDate.of(2026, 2, 1),
        BigDecimal.TEN,
        fuelCategory.getId(),
        openAccount.getId(),
        paymentMethod.getId(),
        null,
        "Fill up 2",
        null,
        fuelDetails());
    service.create(
        LocalDate.of(2026, 1, 1),
        BigDecimal.TEN,
        fuelCategory.getId(),
        openAccount.getId(),
        paymentMethod.getId(),
        null,
        "Fill up 1",
        null,
        fuelDetails());
    service.create(
        LocalDate.now(),
        BigDecimal.TEN,
        fuelCategory.getId(),
        openAccount.getId(),
        paymentMethod.getId(),
        null,
        "Other vehicle fill up",
        null,
        new FuelDetails(
            otherVehicle.getId(), FuelType.ETANOL, BigDecimal.TEN, BigDecimal.ONE, null, null));

    List<Transaction> history = service.findFuelHistory(vehicle.getId(), null, null);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(history.get(1).getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
  }

  // F026 (ADR 0023): the dividend invariant - investmentHoldingId present iff the category is the
  // dividend category - on both create and edit, plus the holding existence check. Same shape as
  // the fuel invariant tests above.

  @Test
  void createWithDividendCategoryAndInvestmentHoldingIdSucceeds() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            dividendCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            null,
            "KNRI11 dividend",
            null,
            null,
            holding.getId());

    assertThat(created.getInvestmentHoldingId()).isEqualTo(holding.getId());
  }

  @Test
  void createRejectsDividendCategoryWithoutInvestmentHoldingId() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    dividendCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null,
                    "KNRI11 dividend",
                    null,
                    null,
                    null))
        .isInstanceOf(InvestmentHoldingCategoryMismatchException.class);
  }

  @Test
  void createRejectsNonDividendCategoryWithInvestmentHoldingId() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    incomeCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null,
                    "Salary",
                    null,
                    null,
                    holding.getId()))
        .isInstanceOf(InvestmentHoldingCategoryMismatchException.class);
  }

  @Test
  void createRejectsAnUnknownInvestmentHoldingId() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    BigDecimal.TEN,
                    dividendCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    null,
                    "KNRI11 dividend",
                    null,
                    null,
                    UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void editWithDividendCategoryAndInvestmentHoldingIdSucceeds() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            incomeCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Salary",
            null);

    Transaction edited =
        service.edit(
            created.getId(),
            LocalDate.now(),
            BigDecimal.TEN,
            dividendCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "KNRI11 dividend",
            null,
            null,
            holding.getId());

    assertThat(edited.getInvestmentHoldingId()).isEqualTo(holding.getId());
  }

  @Test
  void editRejectsMovingADividendTransactionsCategoryAwayWithoutClearingTheHoldingId() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            dividendCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            null,
            "KNRI11 dividend",
            null,
            null,
            holding.getId());

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    LocalDate.now(),
                    BigDecimal.TEN,
                    incomeCategory.getId(),
                    openAccount.getId(),
                    paymentMethod.getId(),
                    "Salary",
                    null,
                    null,
                    holding.getId()))
        .isInstanceOf(InvestmentHoldingCategoryMismatchException.class);
    // Rejected before any write: the transaction still has its original category and holding id.
    Transaction reloaded = service.findById(created.getId());
    assertThat(reloaded.getCategoryId()).isEqualTo(dividendCategory.getId());
    assertThat(reloaded.getInvestmentHoldingId()).isEqualTo(holding.getId());
  }

  @Test
  void editMovingADividendTransactionsCategoryAwayAfterClearingTheHoldingIdSucceeds() {
    Transaction created =
        service.create(
            LocalDate.now(),
            BigDecimal.TEN,
            dividendCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            null,
            "KNRI11 dividend",
            null,
            null,
            holding.getId());

    Transaction edited =
        service.edit(
            created.getId(),
            LocalDate.now(),
            BigDecimal.TEN,
            incomeCategory.getId(),
            openAccount.getId(),
            paymentMethod.getId(),
            "Salary",
            null);

    assertThat(edited.getCategoryId()).isEqualTo(incomeCategory.getId());
    assertThat(edited.getInvestmentHoldingId()).isNull();
  }
}
