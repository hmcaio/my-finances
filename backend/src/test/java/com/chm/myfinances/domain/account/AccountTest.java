package com.chm.myfinances.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Domain-level unit tests for {@link Account} (PRD S5.4, F003 spec; F017 replaced the free-text
 * institution with a required {@code institutionId}). Pure JUnit - no Spring context, no database
 * (ADR 0004) - written before {@link Account} itself, per F003's/F017's plan.md.
 */
class AccountTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    LocalDate openingDate = LocalDate.of(2026, 1, 1);

    Account account =
        Account.create(
            id,
            "Itau Checking",
            INSTITUTION_ID,
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            openingDate);

    assertThat(account.getId()).isEqualTo(id);
    assertThat(account.getName()).isEqualTo("Itau Checking");
    assertThat(account.getInstitutionId()).isEqualTo(INSTITUTION_ID);
    assertThat(account.getType()).isEqualTo(AccountType.CHECKING);
    assertThat(account.getOpeningBalance()).isEqualByComparingTo("100.00");
    assertThat(account.getOpeningBalanceDate()).isEqualTo(openingDate);
    assertThat(account.getClosedDate()).isNull();
    assertThat(account.isClosed()).isFalse();
  }

  @Test
  void reconstitutePreservesTheInstitutionId() {
    Account account =
        Account.reconstitute(
            UUID.randomUUID(),
            "Itau Checking",
            INSTITUTION_ID,
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 1));

    assertThat(account.getInstitutionId()).isEqualTo(INSTITUTION_ID);
    assertThat(account.isClosed()).isTrue();
  }

  @Test
  void createRejectsNullInstitutionId() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    "Cash Wallet",
                    null,
                    AccountType.CASH_WALLET,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void reconstituteRejectsNullInstitutionId() {
    assertThatThrownBy(
            () ->
                Account.reconstitute(
                    UUID.randomUUID(),
                    "Cash Wallet",
                    null,
                    AccountType.CASH_WALLET,
                    BigDecimal.ZERO,
                    LocalDate.now(),
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void editChangesNameAndInstitutionOnly() {
    LocalDate openingDate = LocalDate.of(2026, 1, 1);
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Nubank",
            INSTITUTION_ID,
            AccountType.CREDIT_CARD,
            new BigDecimal("500.00"),
            openingDate);
    UUID otherInstitutionId = UUID.randomUUID();

    account.edit("Nubank Credit Card", otherInstitutionId);

    assertThat(account.getName()).isEqualTo("Nubank Credit Card");
    assertThat(account.getInstitutionId()).isEqualTo(otherInstitutionId);
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
            INSTITUTION_ID,
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
            INSTITUTION_ID,
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
            INSTITUTION_ID,
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
            INSTITUTION_ID,
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
            INSTITUTION_ID,
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
                    INSTITUTION_ID,
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
                    UUID.randomUUID(),
                    "Checking",
                    INSTITUTION_ID,
                    null,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullOpeningBalance() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    INSTITUTION_ID,
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
                    INSTITUTION_ID,
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
            INSTITUTION_ID,
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
                    INSTITUTION_ID,
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsBlankName() {
    Account account = newChecking();

    assertThatThrownBy(() -> account.edit(" ", INSTITUTION_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsNameOverMaxLength() {
    Account account = newChecking();
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> account.edit(tooLongName, INSTITUTION_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editRejectsNullInstitutionIdAndKeepsTheOldOne() {
    Account account = newChecking();

    assertThatThrownBy(() -> account.edit("Renamed", null))
        .isInstanceOf(NullPointerException.class);
    assertThat(account.getInstitutionId()).isEqualTo(INSTITUTION_ID);
  }

  @Test
  void investmentAccountCreatesWithoutOpeningBalanceOrDate() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "XP Investimentos",
            INSTITUTION_ID,
            AccountType.INVESTMENT,
            null,
            null);

    assertThat(account.getType()).isEqualTo(AccountType.INVESTMENT);
    assertThat(account.getOpeningBalance()).isNull();
    assertThat(account.getOpeningBalanceDate()).isNull();
    assertThat(account.isClosed()).isFalse();
  }

  @Test
  void investmentAccountRejectsAnOpeningBalance() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    "XP Investimentos",
                    INSTITUTION_ID,
                    AccountType.INVESTMENT,
                    BigDecimal.TEN,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void investmentAccountRejectsAnOpeningBalanceDate() {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(),
                    "XP Investimentos",
                    INSTITUTION_ID,
                    AccountType.INVESTMENT,
                    null,
                    LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteEnforcesTheInvestmentInvariantToo() {
    Account investment =
        Account.reconstitute(
            UUID.randomUUID(),
            "XP Investimentos",
            INSTITUTION_ID,
            AccountType.INVESTMENT,
            null,
            null,
            null);
    assertThat(investment.getOpeningBalance()).isNull();

    assertThatThrownBy(
            () ->
                Account.reconstitute(
                    UUID.randomUUID(),
                    "XP Investimentos",
                    INSTITUTION_ID,
                    AccountType.INVESTMENT,
                    BigDecimal.ZERO,
                    LocalDate.now(),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Account.reconstitute(
                    UUID.randomUUID(),
                    "Checking",
                    INSTITUTION_ID,
                    AccountType.CHECKING,
                    null,
                    null,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = AccountType.class,
      names = {"CHECKING", "SAVINGS", "CASH_WALLET", "CREDIT_CARD"})
  void everyNonInvestmentTypeRequiresBothOpeningFields(AccountType type) {
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(), "Any", INSTITUTION_ID, type, null, LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                Account.create(
                    UUID.randomUUID(), "Any", INSTITUTION_ID, type, BigDecimal.ZERO, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(
            Account.create(
                    UUID.randomUUID(),
                    "Any",
                    INSTITUTION_ID,
                    type,
                    BigDecimal.ZERO,
                    LocalDate.now())
                .getOpeningBalance())
        .isEqualByComparingTo("0");
  }

  @Test
  void anInvestmentAccountStillEditsAndCloses() {
    Account account =
        Account.create(
            UUID.randomUUID(), "Broker", INSTITUTION_ID, AccountType.INVESTMENT, null, null);

    account.edit("Broker renamed", INSTITUTION_ID);
    account.close();

    assertThat(account.getName()).isEqualTo("Broker renamed");
    assertThat(account.isClosed()).isTrue();
    assertThat(account.getOpeningBalance()).isNull();
  }

  private static Account newChecking() {
    return Account.create(
        UUID.randomUUID(),
        "Checking",
        INSTITUTION_ID,
        AccountType.CHECKING,
        BigDecimal.ZERO,
        LocalDate.now());
  }
}
