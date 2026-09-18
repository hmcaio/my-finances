package com.chm.myfinances.infrastructure.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer integration test for {@link AccountRepositoryAdapter}: hits a real, ephemeral
 * Postgres via Testcontainers (ADR 0010), so Flyway's {@code V4__accounts.sql} runs for real too.
 * Same {@code @SpringBootTest} + {@code @Import(TestcontainersConfiguration.class)} +
 * {@code @Transactional} pattern as F002's {@code CategoryRepositoryAdapterTest} (Spring Boot 4.x
 * has no {@code @DataJpaTest} slice - see that class's javadoc).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountRepositoryAdapterTest {

  @Autowired private AccountRepository accountRepository;

  @Test
  void savesAndReloadsAnAccount() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Itau Checking",
            "Itau",
            AccountType.CHECKING,
            new BigDecimal("250.50"),
            LocalDate.of(2026, 1, 15));

    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Itau Checking");
    assertThat(reloaded.get().getInstitution()).isEqualTo("Itau");
    assertThat(reloaded.get().getType()).isEqualTo(AccountType.CHECKING);
    assertThat(reloaded.get().getOpeningBalance()).isEqualByComparingTo("250.50");
    assertThat(reloaded.get().getOpeningBalanceDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(reloaded.get().isClosed()).isFalse();
  }

  @Test
  void savesAnAccountWithNoInstitution() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Cash Wallet",
            null,
            AccountType.CASH_WALLET,
            BigDecimal.ZERO,
            LocalDate.now());

    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getInstitution()).isNull();
  }

  @Test
  void editPersists() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Original",
            "Original Bank",
            AccountType.SAVINGS,
            new BigDecimal("100.00"),
            LocalDate.now());
    accountRepository.save(account);

    account.edit("Renamed", "Renamed Bank");
    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed");
    assertThat(reloaded.get().getInstitution()).isEqualTo("Renamed Bank");
    // Opening balance/date must survive the reload untouched.
    assertThat(reloaded.get().getOpeningBalance()).isEqualByComparingTo("100.00");
  }

  @Test
  void closePersists() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Old Account",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    accountRepository.save(account);

    account.close();
    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().isClosed()).isTrue();
    assertThat(reloaded.get().getClosedDate()).isEqualTo(LocalDate.now());
  }

  @Test
  void findAllReturnsEveryAccount() {
    accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            "Account A",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now()));
    accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            "Account B",
            null,
            AccountType.SAVINGS,
            BigDecimal.ZERO,
            LocalDate.now()));

    assertThat(accountRepository.findAll())
        .extracting(Account::getName)
        .contains("Account A", "Account B");
  }

  @Test
  void existsByIdReflectsPersistedState() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());

    assertThat(accountRepository.existsById(account.getId())).isFalse();

    accountRepository.save(account);

    assertThat(accountRepository.existsById(account.getId())).isTrue();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            "Unique Name Test",
            null,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now()));

    assertThat(accountRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(accountRepository.existsByName("unique name test")).isFalse();
    assertThat(accountRepository.existsByName("Something Else")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    Account account =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Exclude Self Test",
                null,
                AccountType.CHECKING,
                BigDecimal.ZERO,
                LocalDate.now()));

    assertThat(accountRepository.existsByNameAndIdNot("Exclude Self Test", account.getId()))
        .isFalse();
    assertThat(accountRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }
}
