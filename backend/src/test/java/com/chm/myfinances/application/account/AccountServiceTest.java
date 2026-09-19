package com.chm.myfinances.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountClosedNotifier;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.testsupport.FakeAccountRepository;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.LogCapture;
import java.math.BigDecimal;
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
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAccountClosedNotifier notifier = new FakeAccountClosedNotifier();
  private final AccountService service = new AccountService(repository, idGenerator, notifier);

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    AccountService service = new AccountService(repository, new FakeIdGenerator(nextId), notifier);

    Account created =
        service.create(
            "Itau Checking",
            "Itau",
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            LocalDate.of(2026, 1, 1));

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Itau Checking");
    assertThat(repository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create("Checking", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());

    assertThatThrownBy(
            () ->
                service.create(
                    "Checking", null, AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.now()))
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
        service.create("Open", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    Account closed =
        service.create("Closed", null, AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.now());
    service.close(closed.getId());

    List<Account> result = service.findAll(false);

    assertThat(result).extracting(Account::getId).containsExactly(open.getId());
  }

  @Test
  void findAllIncludesClosedAccountsWhenRequested() {
    Account open =
        service.create("Open", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    Account closed =
        service.create("Closed", null, AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.now());
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
            "Nubank", "Nubank", AccountType.CREDIT_CARD, new BigDecimal("500.00"), LocalDate.now());

    Account edited = service.edit(created.getId(), "Nubank Credit Card", "Nu Pagamentos");

    assertThat(edited.getName()).isEqualTo("Nubank Credit Card");
    assertThat(edited.getInstitution()).isEqualTo("Nu Pagamentos");
    assertThat(edited.getOpeningBalance()).isEqualByComparingTo("500.00");
  }

  @Test
  void editToItsOwnCurrentNameIsAllowed() {
    Account created =
        service.create(
            "Nubank", "Nubank", AccountType.CREDIT_CARD, new BigDecimal("500.00"), LocalDate.now());

    Account edited = service.edit(created.getId(), "Nubank", "Nu Pagamentos");

    assertThat(edited.getName()).isEqualTo("Nubank");
  }

  @Test
  void editRejectsADuplicateName() {
    service.create("Checking", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    Account savings =
        service.create("Savings", null, AccountType.SAVINGS, BigDecimal.ZERO, LocalDate.now());

    assertThatThrownBy(() -> service.edit(savings.getId(), "Checking", null))
        .isInstanceOf(AccountNameAlreadyExistsException.class);
  }

  @Test
  void editOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.edit(UUID.randomUUID(), "New name", null))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void closeSetsClosedDateAndNotifiesThePort() {
    Account created =
        service.create("Old Account", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());

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
        service.create("Old Account", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    service.close(created.getId());

    assertThatThrownBy(() -> service.close(created.getId()))
        .isInstanceOf(AccountAlreadyClosedException.class);
    // Only one notification for the one successful close.
    assertThat(notifier.notifiedAccountIds).containsExactly(created.getId());
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
            null,
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
        service.create("Checking", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    service.close(created.getId());

    try (LogCapture logs = LogCapture.of(AccountService.class)) {
      assertThatThrownBy(() -> service.close(created.getId()))
          .isInstanceOf(AccountAlreadyClosedException.class);

      assertThat(logs.events()).isEmpty();
    }
  }
}
