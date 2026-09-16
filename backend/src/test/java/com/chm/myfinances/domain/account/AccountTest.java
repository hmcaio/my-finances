package com.chm.myfinances.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Account} (PRD S5.4, F003 spec). Pure JUnit - no Spring
 * context, no database (ADR 0004) - written before {@link Account} itself, per F003's plan.md.
 */
class AccountTest {

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    LocalDate openingDate = LocalDate.of(2026, 1, 1);

    Account account =
        Account.create(
            id,
            "Itau Checking",
            "Itau",
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            openingDate);

    assertThat(account.getId()).isEqualTo(id);
    assertThat(account.getName()).isEqualTo("Itau Checking");
    assertThat(account.getInstitution()).isEqualTo("Itau");
    assertThat(account.getType()).isEqualTo(AccountType.CHECKING);
    assertThat(account.getOpeningBalance()).isEqualByComparingTo("100.00");
    assertThat(account.getOpeningBalanceDate()).isEqualTo(openingDate);
    assertThat(account.getClosedDate()).isNull();
    assertThat(account.isClosed()).isFalse();
  }

  @Test
  void createAllowsNullInstitution() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Cash Wallet",
            null,
            AccountType.CASH_WALLET,
            BigDecimal.ZERO,
            LocalDate.now());

    assertThat(account.getInstitution()).isNull();
  }

  @Test
  void editChangesNameAndInstitutionOnly() {
    LocalDate openingDate = LocalDate.of(2026, 1, 1);
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Nubank",
            "Nubank",
            AccountType.CREDIT_CARD,
            new BigDecimal("500.00"),
            openingDate);

    account.edit("Nubank Credit Card", "Nu Pagamentos");

    assertThat(account.getName()).isEqualTo("Nubank Credit Card");
    assertThat(account.getInstitution()).isEqualTo("Nu Pagamentos");
    // Opening balance/date and type must never change via edit().
    assertThat(account.getOpeningBalance()).isEqualByComparingTo("500.00");
    assertThat(account.getOpeningBalanceDate()).isEqualTo(openingDate);
    assertThat(account.getType()).isEqualTo(AccountType.CREDIT_CARD);
  }

  @Test
  void openingBalanceAndDateAreImmutableAfterCreation() {
    // Account exposes no mutator at all for openingBalance/openingBalanceDate/type - this is
    // enforced structurally (final fields with only getters), not by a runtime check, because
    // retroactively changing them would silently rewrite every past balance/net-worth
    // calculation (F003 spec). edit() above is the only mutation surface and it never touches
    // them; this test just documents the invariant explicitly at the class level.
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Savings",
            null,
            AccountType.SAVINGS,
            new BigDecimal("1000.00"),
            LocalDate.of(2026, 3, 1));

    assertThat(account.getOpeningBalance()).isEqualByComparingTo("1000.00");
    assertThat(account.getOpeningBalanceDate()).isEqualTo(LocalDate.of(2026, 3, 1));
  }

  @Test
  void closeSetsClosedDateAndMarksAccountClosed() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Old Account",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());

    account.close();

    assertThat(account.isClosed()).isTrue();
    assertThat(account.getClosedDate()).isNotNull();
    assertThat(account.getClosedDate()).isEqualTo(LocalDate.now());
  }

  @Test
  void closeThrowsWhenAlreadyClosed() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Old Account",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    account.close();

    assertThatThrownBy(account::close).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void requireOpenDoesNotThrowWhileOpen() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());

    account.requireOpen();
  }

  @Test
  void requireOpenThrowsAfterClose() {
    // F003 spec: a closed account "rejects any attempt to post new activity to it (enforced
    // here at the domain level, not just at the API layer)". requireOpen() is the hook
    // F004 (Transaction)/F005 (Transfer) will call before attaching activity to this account.
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    account.close();

    assertThatThrownBy(account::requireOpen).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    " ",
                    null,
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullType() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(), "Checking", null, null, BigDecimal.ZERO, LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullOpeningBalance() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    null,
                    AccountType.CHECKING,
                    null,
                    LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullOpeningBalanceDate() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    null,
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createAcceptsNameAtMaxLength() {
    String maxLengthName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);

    Account account =
        Account.create(
            UUID.randomUUID(),
            maxLengthName,
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());

    assertThat(account.getName()).isEqualTo(maxLengthName);
  }

  @Test
  void createRejectsNameOverMaxLength() {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    tooLongName,
                    null,
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsInstitutionOverMaxLength() {
    String tooLongInstitution = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    tooLongInstitution,
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsBlankName() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());

    assertThatThrownBy(() -> account.edit(" ", null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsNameOverMaxLength() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> account.edit(tooLongName, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
