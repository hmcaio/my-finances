package com.chm.myfinances.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.testsupport.FakeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AccountBalanceQuery} (F003 spec, extended by F004): opening balance plus real
 * transaction activity, with the sign of each transaction's contribution derived from its type and
 * the account's own type (PRD S5.3/S5.4) - an expense reduces an asset account's balance and
 * increases a credit card account's owed balance; income does the reverse.
 */
class AccountBalanceQueryTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final AccountBalanceQuery query = new AccountBalanceQuery(transactionRepository);

  private static Transaction transactionOn(
      LocalDate date, BigDecimal amount, CategoryType type, UUID accountId) {
    return Transaction.create(
        UUID.randomUUID(),
        date,
        amount,
        UUID.randomUUID(),
        type,
        accountId,
        UUID.randomUUID(),
        null,
        "Test transaction",
        null);
  }

  @Test
  void balanceAsOfReturnsOpeningBalanceWhenNoActivityExists() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            new BigDecimal("321.45"),
            LocalDate.of(2026, 1, 1));

    BigDecimal balance = query.balanceAsOf(account, LocalDate.now());

    assertThat(balance).isEqualByComparingTo("321.45");
  }

  @Test
  void balanceAsOfOnAssetAccountAddsIncomeAndSubtractsExpense() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            LocalDate.of(2026, 1, 1));
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 1, 5),
            new BigDecimal("50.00"),
            CategoryType.INCOME,
            account.getId()));
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 1, 10),
            new BigDecimal("30.00"),
            CategoryType.EXPENSE,
            account.getId()));

    BigDecimal balance = query.balanceAsOf(account, LocalDate.of(2026, 1, 31));

    // 100 + 50 - 30 = 120
    assertThat(balance).isEqualByComparingTo("120.00");
  }

  @Test
  void balanceAsOfOnCreditCardAccountExpenseIncreasesAndIncomeDecreasesOwedAmount() {
    Account creditCard =
        Account.create(
            UUID.randomUUID(),
            "Nubank",
            "Nubank",
            AccountType.CREDIT_CARD,
            new BigDecimal("200.00"),
            LocalDate.of(2026, 1, 1));
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 1, 5),
            new BigDecimal("75.00"),
            CategoryType.EXPENSE,
            creditCard.getId()));
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 1, 10),
            new BigDecimal("25.00"),
            CategoryType.INCOME,
            creditCard.getId()));

    BigDecimal balance = query.balanceAsOf(creditCard, LocalDate.of(2026, 1, 31));

    // 200 (owed) + 75 (expense increases owed) - 25 (income/refund decreases owed) = 250
    assertThat(balance).isEqualByComparingTo("250.00");
  }

  @Test
  void balanceAsOfExcludesTransactionsAfterAsOfDate() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            LocalDate.of(2026, 1, 1));
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 2, 1),
            new BigDecimal("999.00"),
            CategoryType.INCOME,
            account.getId()));

    BigDecimal balance = query.balanceAsOf(account, LocalDate.of(2026, 1, 31));

    assertThat(balance).isEqualByComparingTo("100.00");
  }

  @Test
  void balanceAsOfIncludesTransactionsOnTheAsOfDateItself() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            LocalDate.of(2026, 1, 1));
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 1, 31),
            new BigDecimal("15.00"),
            CategoryType.INCOME,
            account.getId()));

    BigDecimal balance = query.balanceAsOf(account, LocalDate.of(2026, 1, 31));

    assertThat(balance).isEqualByComparingTo("115.00");
  }

  @Test
  void balanceAsOfExcludesTransactionsOnOtherAccounts() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            new BigDecimal("100.00"),
            LocalDate.of(2026, 1, 1));
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 1, 5),
            new BigDecimal("500.00"),
            CategoryType.INCOME,
            UUID.randomUUID()));

    BigDecimal balance = query.balanceAsOf(account, LocalDate.of(2026, 1, 31));

    assertThat(balance).isEqualByComparingTo("100.00");
  }
}
