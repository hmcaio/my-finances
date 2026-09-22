package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Test data builder for {@link Account} (issue #31, B6): a valid, in-memory account with sensible
 * defaults that a test overrides only where it cares. Does not touch a repository - {@link
 * TestFixtures} is the real-DB counterpart for tests that need a persisted row.
 *
 * <p>Start from one of the per-type factories ({@link #checking()}, {@link #savings()}, {@link
 * #cashWallet()}, {@link #creditCard()}, {@link #investment()}), override what the test needs, then
 * {@link #build()}. A test asserting on {@link Account#create}'s own validation (blank name,
 * negative amount, the {@code INVESTMENT} opening-balance invariant) should keep calling {@code
 * Account.create(...)} directly instead - going through this builder would obscure what's being
 * tested.
 */
public final class AccountMother {

  private UUID id = UUID.randomUUID();
  private String name = "Checking";
  private UUID institutionId = UUID.randomUUID();
  private AccountType type = AccountType.CHECKING;
  private BigDecimal openingBalance = BigDecimal.ZERO;
  private LocalDate openingBalanceDate = LocalDate.now();

  private AccountMother() {}

  public static AccountMother checking() {
    return new AccountMother();
  }

  public static AccountMother savings() {
    return new AccountMother().withType(AccountType.SAVINGS).withName("Savings");
  }

  public static AccountMother cashWallet() {
    return new AccountMother().withType(AccountType.CASH_WALLET).withName("Cash Wallet");
  }

  public static AccountMother creditCard() {
    return new AccountMother().withType(AccountType.CREDIT_CARD).withName("Credit Card");
  }

  /** {@code INVESTMENT} accounts have no opening balance/date (ADR 0012). */
  public static AccountMother investment() {
    AccountMother mother = new AccountMother();
    mother.type = AccountType.INVESTMENT;
    mother.name = "Broker";
    mother.openingBalance = null;
    mother.openingBalanceDate = null;
    return mother;
  }

  public AccountMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public AccountMother withName(String name) {
    this.name = name;
    return this;
  }

  public AccountMother withInstitutionId(UUID institutionId) {
    this.institutionId = institutionId;
    return this;
  }

  public AccountMother withType(AccountType type) {
    this.type = type;
    return this;
  }

  public AccountMother withOpeningBalance(BigDecimal openingBalance) {
    this.openingBalance = openingBalance;
    return this;
  }

  public AccountMother withOpeningBalanceDate(LocalDate openingBalanceDate) {
    this.openingBalanceDate = openingBalanceDate;
    return this;
  }

  public Account build() {
    return Account.create(id, name, institutionId, type, openingBalance, openingBalanceDate);
  }
}
