package com.chm.myfinances.infrastructure.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.TestInstitutions;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link AccountRepositoryAdapter}: hits a real, ephemeral
 * Postgres via Testcontainers (ADR 0010), so Flyway's {@code V4__accounts.sql} runs for real too.
 * Same {@code @SpringBootTest} + {@code @Import(TestcontainersConfiguration.class)} +
 * {@code @Transactional} pattern as F002's {@code CategoryRepositoryAdapterTest} (Spring Boot 4.x
 * has no {@code @DataJpaTest} slice - see that class's javadoc).
 */
@DatabaseIntegrationTest
class AccountRepositoryAdapterTest {

  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private EntityManager entityManager;

  private UUID institutionId;
  private UUID otherInstitutionId;

  @BeforeEach
  void setUp() {
    institutionId =
        institutionRepository.save(Institution.create(UUID.randomUUID(), "Itau Test")).getId();
    otherInstitutionId =
        institutionRepository
            .save(Institution.create(UUID.randomUUID(), "Renamed Bank Test"))
            .getId();
  }

  @Test
  void savesAndReloadsAnAccount() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Itau Checking",
            institutionId,
            AccountType.CHECKING,
            new BigDecimal("250.50"),
            LocalDate.of(2026, 1, 15));

    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Itau Checking");
    assertThat(reloaded.get().getInstitutionId()).isEqualTo(institutionId);
    assertThat(reloaded.get().getType()).isEqualTo(AccountType.CHECKING);
    assertThat(reloaded.get().getOpeningBalance()).isEqualByComparingTo("250.50");
    assertThat(reloaded.get().getOpeningBalanceDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(reloaded.get().isClosed()).isFalse();
  }

  @Test
  void savesAnAccountAtTheBuiltInInstitution() {
    UUID builtInId = TestInstitutions.builtInId(institutionRepository);
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Cash Wallet",
            builtInId,
            AccountType.CASH_WALLET,
            BigDecimal.ZERO,
            LocalDate.now());

    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getInstitutionId()).isEqualTo(builtInId);
  }

  @Test
  void savesAndReloadsAnInvestmentAccountWithoutOpeningValues() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "XP Investimentos",
            institutionId,
            AccountType.INVESTMENT,
            null,
            null);

    accountRepository.save(account);
    entityManager.flush();
    entityManager.clear();

    Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
    assertThat(reloaded.getType()).isEqualTo(AccountType.INVESTMENT);
    assertThat(reloaded.getOpeningBalance()).isNull();
    assertThat(reloaded.getOpeningBalanceDate()).isNull();
  }

  @Test
  void editPersists() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Original",
            institutionId,
            AccountType.SAVINGS,
            new BigDecimal("100.00"),
            LocalDate.now());
    accountRepository.save(account);

    account.edit("Renamed", otherInstitutionId);
    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed");
    assertThat(reloaded.get().getInstitutionId()).isEqualTo(otherInstitutionId);
    // Opening balance/date must survive the reload untouched.
    assertThat(reloaded.get().getOpeningBalance()).isEqualByComparingTo("100.00");
  }

  @Test
  void closePersists() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Old Account",
            institutionId,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    accountRepository.save(account);

    LocalDate closedDate = LocalDate.now();
    account.close(closedDate);
    accountRepository.save(account);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().isClosed()).isTrue();
    assertThat(reloaded.get().getClosedDate()).isEqualTo(closedDate);
  }

  @Test
  void findAllReturnsEveryAccount() {
    accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            "Account A",
            institutionId,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now()));
    accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            "Account B",
            institutionId,
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
            institutionId,
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
            institutionId,
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
                institutionId,
                AccountType.CHECKING,
                BigDecimal.ZERO,
                LocalDate.now()));

    assertThat(accountRepository.existsByNameAndIdNot("Exclude Self Test", account.getId()))
        .isFalse();
    assertThat(accountRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void existsByInstitutionIdIsTrueOnlyWhileAnAccountReferencesIt() {
    assertThat(accountRepository.existsByInstitutionId(institutionId)).isFalse();

    accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            "Institution Ref Test",
            institutionId,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now()));

    assertThat(accountRepository.existsByInstitutionId(institutionId)).isTrue();
    assertThat(accountRepository.existsByInstitutionId(otherInstitutionId)).isFalse();
  }

  @Test
  void existsByInstitutionIdCountsClosedAccounts() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Closed Ref Test",
            institutionId,
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    account.close(LocalDate.now());
    accountRepository.save(account);

    assertThat(accountRepository.existsByInstitutionId(institutionId)).isTrue();
  }

  @Test
  void theDatabaseRejectsAnAccountPointingAtAnUnknownInstitution() {
    accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            "Dangling Test",
            UUID.randomUUID(),
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now()));

    assertThatThrownBy(entityManager::flush).hasMessageContaining("institution_id");
  }
}
