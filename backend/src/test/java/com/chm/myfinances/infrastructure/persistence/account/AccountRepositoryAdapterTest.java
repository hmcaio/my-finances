package com.chm.myfinances.infrastructure.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.TestFixtures;
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
        TestFixtures.account(
            accountRepository, institutionRepository, "Cash Wallet", AccountType.CASH_WALLET);

    Optional<Account> reloaded = accountRepository.findById(account.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getInstitutionId()).isEqualTo(builtInId);
  }

  @Test
  void savesAndReloadsAnInvestmentAccountWithoutOpeningValues() {
    Account account =
        TestFixtures.account(
            accountRepository, institutionRepository, "XP Investimentos", AccountType.INVESTMENT);

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
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Old Account");

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
    TestFixtures.checkingAccount(accountRepository, institutionRepository, "Account A");
    TestFixtures.account(
        accountRepository, institutionRepository, "Account B", AccountType.SAVINGS);

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
    TestFixtures.checkingAccount(accountRepository, institutionRepository, "Unique Name Test");

    assertThat(accountRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(accountRepository.existsByName("unique name test")).isFalse();
    assertThat(accountRepository.existsByName("Something Else")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    Account account =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Exclude Self Test");

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
