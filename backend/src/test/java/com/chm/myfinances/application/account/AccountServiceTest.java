package com.chm.myfinances.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.chm.myfinances.application.institution.InstitutionNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountClosedNotifier;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.testsupport.FakeAccountRepository;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakeInstitutionRepository;
import com.chm.myfinances.testsupport.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.InvestmentProductMother;
import com.chm.myfinances.testsupport.LogCapture;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link AccountService}, written first (ADR 0004) against hand-written
 * fakes for {@link AccountRepository}/{@link IdGenerator}/{@link AccountClosedNotifier} - plain
 * JUnit, no Spring context.
 */
class AccountServiceTest {

  private final FakeAccountRepository repository = new FakeAccountRepository();
  private final FakeInstitutionRepository institutionRepository = new FakeInstitutionRepository();
  private final FakeInvestmentProductRepository investmentProductRepository =
      new FakeInvestmentProductRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAccountClosedNotifier notifier = new FakeAccountClosedNotifier();
  private final UUID institutionId =
      institutionRepository.save(Institution.create(UUID.randomUUID(), "Nubank Test")).getId();
  private final UUID otherInstitutionId =
      institutionRepository.save(Institution.create(UUID.randomUUID(), "Itau Test")).getId();
  private final AccountService service =
      new AccountService(
          repository,
          institutionRepository,
          investmentProductRepository,
          idGenerator,
          notifier,
          Clock.systemDefaultZone());

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    AccountService service =
        new AccountService(
            repository,
            institutionRepository,
            investmentProductRepository,
            new FakeIdGenerator(nextId),
            notifier,
            Clock.systemDefaultZone());

    Account created =
        service.create(
            "Itau Checking",
            institutionId,
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            LocalDate.of(2026, 1, 1));

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Itau Checking");
    assertThat(repository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create(
        "Checking", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());

    assertThatThrownBy(
            () ->
                service.create(
                    "Checking",
                    institutionId,
                    AccountType.SAVINGS,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(AccountNameAlreadyExistsException.class);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void findAllExcludesClosedAccountsByDefault() {
    Account open =
        service.create(
            "Open", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    Account closed =
        service.create(
            "Closed", institutionId, AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.now());
    service.close(closed.getId());

    List<Account> result = service.findAll(false);

    assertThat(result).extracting(Account::getId).containsExactly(open.getId());
  }

  @Test
  void findAllIncludesClosedAccountsWhenRequested() {
    Account open =
        service.create(
            "Open", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    Account closed =
        service.create(
            "Closed", institutionId, AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.now());
    service.close(closed.getId());

    List<Account> result = service.findAll(true);

    assertThat(result)
        .extracting(Account::getId)
        .containsExactlyInAnyOrder(open.getId(), closed.getId());
  }

  @Test
  void editUpdatesNameAndInstitutionOnly() {
    Account created =
        service.create(
            "Nubank",
            institutionId,
            AccountType.CREDIT_CARD,
            new BigDecimal("500.00"),
            LocalDate.now());

    Account edited = service.edit(created.getId(), "Nubank Credit Card", otherInstitutionId);

    assertThat(edited.getName()).isEqualTo("Nubank Credit Card");
    assertThat(edited.getInstitutionId()).isEqualTo(otherInstitutionId);
    assertThat(edited.getOpeningBalance()).isEqualByComparingTo("500.00");
  }

  @Test
  void createRejectsAnUnknownInstitution() {
    assertThatThrownBy(
            () ->
                service.create(
                    "Checking",
                    UUID.randomUUID(),
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(InstitutionNotFoundException.class);
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void editRejectsAnUnknownInstitutionAndLeavesTheAccountUntouched() {
    Account created =
        service.create(
            "Checking", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());

    assertThatThrownBy(() -> service.edit(created.getId(), "Renamed", UUID.randomUUID()))
        .isInstanceOf(InstitutionNotFoundException.class);
    Account reloaded = service.findById(created.getId());
    assertThat(reloaded.getName()).isEqualTo("Checking");
    assertThat(reloaded.getInstitutionId()).isEqualTo(institutionId);
  }

  @Test
  void editMovesTheAccountToAnotherInstitution() {
    Account created =
        service.create(
            "Checking", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());

    service.edit(created.getId(), "Checking", otherInstitutionId);

    assertThat(service.findById(created.getId()).getInstitutionId()).isEqualTo(otherInstitutionId);
  }

  @Test
  void editWorksOnAClosedAccount() {
    Account created =
        service.create(
            "Old", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    service.close(created.getId());

    Account edited = service.edit(created.getId(), "Old", otherInstitutionId);

    assertThat(edited.isClosed()).isTrue();
    assertThat(edited.getInstitutionId()).isEqualTo(otherInstitutionId);
  }

  @Test
  void editToItsOwnCurrentNameIsAllowed() {
    Account created =
        service.create(
            "Nubank",
            institutionId,
            AccountType.CREDIT_CARD,
            new BigDecimal("500.00"),
            LocalDate.now());

    Account edited = service.edit(created.getId(), "Nubank", otherInstitutionId);

    assertThat(edited.getName()).isEqualTo("Nubank");
  }

  @Test
  void editRejectsADuplicateName() {
    service.create(
        "Checking", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    Account savings =
        service.create(
            "Savings", institutionId, AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.now());

    assertThatThrownBy(() -> service.edit(savings.getId(), "Checking", institutionId))
        .isInstanceOf(AccountNameAlreadyExistsException.class);
  }

  @Test
  void editOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.edit(UUID.randomUUID(), "New name", institutionId))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void closeSetsClosedDateAndNotifiesThePort() {
    Account created =
        service.create(
            "Old Account", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());

    Account closed = service.close(created.getId());

    assertThat(closed.isClosed()).isTrue();
    assertThat(notifier.notifiedAccountIds).containsExactly(created.getId());
  }

  @Test
  void closeOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.close(UUID.randomUUID()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void closeOfAlreadyClosedAccountThrowsConflict() {
    Account created =
        service.create(
            "Old Account", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    service.close(created.getId());

    assertThatThrownBy(() -> service.close(created.getId()))
        .isInstanceOf(AccountAlreadyClosedException.class);
    // Only one notification for the one successful close.
    assertThat(notifier.notifiedAccountIds).containsExactly(created.getId());
  }

  @Test
  void createsAnInvestmentAccountWithoutOpeningValues() {
    Account created = service.create("XP Test", institutionId, AccountType.INVESTMENT, null, null);

    assertThat(created.getType()).isEqualTo(AccountType.INVESTMENT);
    assertThat(created.getOpeningBalance()).isNull();
    assertThat(created.getOpeningBalanceDate()).isNull();
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void createRejectsOpeningValuesOnAnInvestmentAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    "XP Test",
                    institutionId,
                    AccountType.INVESTMENT,
                    BigDecimal.TEN,
                    LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void createRejectsMissingOpeningValuesOnANonInvestmentAccount() {
    assertThatThrownBy(
            () -> service.create("Checking", institutionId, AccountType.CHECKING, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void closeOfAnInvestmentAccountIsRejectedWhileAProductIsOpen() {
    Account investment =
        service.create("XP Test", institutionId, AccountType.INVESTMENT, null, null);
    addProductTo(investment.getId());

    assertThatThrownBy(() -> service.close(investment.getId()))
        .isInstanceOf(InvestmentAccountHasOpenProductsException.class);

    assertThat(service.findById(investment.getId()).isClosed()).isFalse();
    assertThat(notifier.notifiedAccountIds).isEmpty();
  }

  @Test
  void closeOfAnInvestmentAccountSucceedsOnceItsProductsAreClosedOrThereAreNone() {
    Account withProducts =
        service.create("XP Test", institutionId, AccountType.INVESTMENT, null, null);
    InvestmentProduct product = addProductTo(withProducts.getId());
    product.close(LocalDate.now());
    Account empty = service.create("Nu Test", institutionId, AccountType.INVESTMENT, null, null);

    assertThat(service.close(withProducts.getId()).isClosed()).isTrue();
    assertThat(service.close(empty.getId()).isClosed()).isTrue();
  }

  @Test
  void openProductsOnAnotherAccountDoNotBlockClosingANonInvestmentAccount() {
    Account checking =
        service.create(
            "Checking", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    addProductTo(UUID.randomUUID());

    assertThat(service.close(checking.getId()).isClosed()).isTrue();
  }

  private InvestmentProduct addProductTo(UUID accountId) {
    return investmentProductRepository.save(
        InvestmentProductMother.product()
            .withAccountId(accountId)
            .withInvestmentSubcategoryId(null)
            .withName("Product Test")
            .build());
  }

  private static final class FakeAccountClosedNotifier implements AccountClosedNotifier {
    private final List<UUID> notifiedAccountIds = new ArrayList<>();

    @Override
    public void accountClosed(UUID accountId) {
      notifiedAccountIds.add(accountId);
    }
  }

  @Test
  void closeLogsOneInfoLineWithTheIdAndNothingElse() {
    Account created =
        service.create(
            "Distinctive Bank Account",
            institutionId,
            AccountType.CHECKING,
            new BigDecimal("1234.56"),
            LocalDate.now());

    try (LogCapture logs = LogCapture.of(AccountService.class)) {
      service.close(created.getId());

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly("Account " + created.getId() + " closed");
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void aRejectedCloseOfAnAlreadyClosedAccountLogsNothing() {
    Account created =
        service.create(
            "Checking", institutionId, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    service.close(created.getId());

    try (LogCapture logs = LogCapture.of(AccountService.class)) {
      assertThatThrownBy(() -> service.close(created.getId()))
          .isInstanceOf(AccountAlreadyClosedException.class);

      assertThat(logs.events()).isEmpty();
    }
  }
}
