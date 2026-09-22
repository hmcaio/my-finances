package com.chm.myfinances.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.testsupport.AccountMother;
import com.chm.myfinances.testsupport.FakeTransactionRepository;
import com.chm.myfinances.testsupport.FakeTransferRepository;
import com.chm.myfinances.testsupport.TransactionMother;
import com.chm.myfinances.testsupport.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AccountBalanceQuery} (F003 spec, extended by F004 and F005): opening balance
 * plus real transaction and transfer activity. A transaction's contribution is signed by its type
 * and the account's own type (PRD S5.3/S5.4) - an expense reduces an asset account's balance and
 * increases a credit card account's owed balance; income does the reverse. A transfer's
 * contribution (PRD S5.5) is simpler: the source account's balance always decreases by the transfer
 * amount; the destination account's balance increases if it's an asset account, or its owed amount
 * decreases if it's a credit card account.
 */
class AccountBalanceQueryTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final AccountBalanceQuery query =
      new AccountBalanceQuery(transactionRepository, transferRepository);

  private static Transaction transactionOn(
      LocalDate date, BigDecimal amount, CategoryType type, UUID accountId) {
    return TransactionMother.expense()
        .withDate(date)
        .withAmount(amount)
        .withType(type)
        .withAccountId(accountId)
        .withDescription("Test transaction")
        .build();
  }

  private static Transfer transferOn(
      LocalDate date, BigDecimal amount, UUID fromAccountId, UUID toAccountId) {
    return TransferMother.transfer()
        .withDate(date)
        .withFromAccountId(fromAccountId)
        .withToAccountId(toAccountId)
        .withAmount(amount)
        .withDescription("Test transfer")
        .build();
  }

  @Test
  void balanceAsOfReturnsOpeningBalanceWhenNoActivityExists() {
    Account account =
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("321.45"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();

    BigDecimal balance = query.balanceAsOf(account, LocalDate.now());

    assertThat(balance).isEqualByComparingTo("321.45");
  }

  @Test
  void balanceAsOfOnAssetAccountAddsIncomeAndSubtractsExpense() {
    Account account =
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("100.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
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
        AccountMother.creditCard()
            .withName("Nubank")
            .withOpeningBalance(new BigDecimal("200.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
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
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("100.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
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
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("100.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
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
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("100.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
    transactionRepository.save(
        transactionOn(
            LocalDate.of(2026, 1, 5),
            new BigDecimal("500.00"),
            CategoryType.INCOME,
            UUID.randomUUID()));

    BigDecimal balance = query.balanceAsOf(account, LocalDate.of(2026, 1, 31));

    assertThat(balance).isEqualByComparingTo("100.00");
  }

  @Test
  void balanceAsOfOnAssetToAssetTransferDecreasesSourceAndIncreasesDestination() {
    Account checking =
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("100.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
    Account savings =
        AccountMother.savings()
            .withOpeningBalance(new BigDecimal("50.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
    transferRepository.save(
        transferOn(
            LocalDate.of(2026, 1, 10), new BigDecimal("30.00"), checking.getId(), savings.getId()));

    BigDecimal checkingBalance = query.balanceAsOf(checking, LocalDate.of(2026, 1, 31));
    BigDecimal savingsBalance = query.balanceAsOf(savings, LocalDate.of(2026, 1, 31));

    // Source decreases: 100 - 30 = 70. Destination (asset) increases: 50 + 30 = 80.
    assertThat(checkingBalance).isEqualByComparingTo("70.00");
    assertThat(savingsBalance).isEqualByComparingTo("80.00");
  }

  @Test
  void balanceAsOfOnAssetToCreditCardTransferDecreasesSourceAndDecreasesDestinationOwedAmount() {
    Account checking =
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("200.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
    Account creditCard =
        AccountMother.creditCard()
            .withName("Nubank")
            .withOpeningBalance(new BigDecimal("150.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
    transferRepository.save(
        transferOn(
            LocalDate.of(2026, 1, 10),
            new BigDecimal("100.00"),
            checking.getId(),
            creditCard.getId()));

    BigDecimal checkingBalance = query.balanceAsOf(checking, LocalDate.of(2026, 1, 31));
    BigDecimal creditCardBalance = query.balanceAsOf(creditCard, LocalDate.of(2026, 1, 31));

    // Source decreases: 200 - 100 = 100. Destination (credit card) owed amount decreases:
    // 150 - 100 = 50. No net-worth impact: -100 (asset) + 100 (liability reduction) = 0.
    assertThat(checkingBalance).isEqualByComparingTo("100.00");
    assertThat(creditCardBalance).isEqualByComparingTo("50.00");
  }

  @Test
  void balanceAsOfExcludesTransfersAfterAsOfDateAndOnOtherAccounts() {
    Account checking =
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("100.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build();
    Account savings =
        AccountMother.savings().withOpeningBalanceDate(LocalDate.of(2026, 1, 1)).build();
    transferRepository.save(
        transferOn(
            LocalDate.of(2026, 2, 1), new BigDecimal("999.00"), checking.getId(), savings.getId()));
    transferRepository.save(
        transferOn(
            LocalDate.of(2026, 1, 5),
            new BigDecimal("40.00"),
            UUID.randomUUID(),
            UUID.randomUUID()));

    BigDecimal balance = query.balanceAsOf(checking, LocalDate.of(2026, 1, 31));

    assertThat(balance).isEqualByComparingTo("100.00");
  }

  @Test
  void balanceAsOfOnAnInvestmentAccountIsZeroUntilSnapshotsExist() {
    // F008 stub: no snapshots exist yet, and the account has no opening balance to start from.
    // Transactions/transfers pointing at it don't contribute (F009 replaces this branch with the
    // sum of the products' latest snapshots).
    Account investment = AccountMother.investment().build();
    transferRepository.save(
        transferOn(
            LocalDate.of(2026, 2, 1),
            new BigDecimal("500.00"),
            UUID.randomUUID(),
            investment.getId()));

    BigDecimal balance = query.balanceAsOf(investment, LocalDate.of(2026, 3, 1));

    assertThat(balance).isEqualByComparingTo("0");
  }
}
