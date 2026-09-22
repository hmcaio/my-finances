package com.chm.myfinances.application.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.investmentcategory.InvestmentCategoryNotFoundException;
import com.chm.myfinances.application.investmentsubcategory.InvestmentSubcategoryNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.AccountMother;
import com.chm.myfinances.testsupport.FakeAccountRepository;
import com.chm.myfinances.testsupport.FakeHasInvestmentHistoryChecker;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.FakeInvestmentSubcategoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentProductService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context (F008 spec). Includes plan.md's explicit
 * test-first item: hard delete is only allowed at zero history, and history comes from the {@code
 * HasInvestmentHistoryChecker} port (a fake standing in for F009's snapshots/trades).
 */
class InvestmentProductServiceTest {

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeInvestmentCategoryRepository categoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeHasInvestmentHistoryChecker historyChecker =
      new FakeHasInvestmentHistoryChecker();
  private final InvestmentProductService service =
      new InvestmentProductService(
          productRepository,
          accountRepository,
          categoryRepository,
          subcategoryRepository,
          historyChecker,
          new FakeIdGenerator(),
          Clock.systemDefaultZone());

  private final UUID institutionId = UUID.randomUUID();
  private final UUID xpAccountId = saveInvestmentAccount("XP Test").getId();
  private final UUID nuAccountId = saveInvestmentAccount("Nubank Test").getId();
  private final UUID fixedIncomeId =
      categoryRepository
          .save(InvestmentCategory.create(UUID.randomUUID(), "Fixed Income Test"))
          .getId();
  private final UUID cryptoId =
      categoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), "Crypto Test")).getId();
  private final UUID cdbId =
      subcategoryRepository
          .save(InvestmentSubcategory.create(UUID.randomUUID(), fixedIncomeId, "CDB Test"))
          .getId();

  private Account saveInvestmentAccount(String name) {
    return accountRepository.save(
        AccountMother.investment().withName(name).withInstitutionId(institutionId).build());
  }

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    InvestmentProductService service =
        new InvestmentProductService(
            productRepository,
            accountRepository,
            categoryRepository,
            subcategoryRepository,
            historyChecker,
            new FakeIdGenerator(nextId),
            Clock.systemDefaultZone());

    InvestmentProduct created =
        service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Banco Test 110% CDI");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getAccountId()).isEqualTo(xpAccountId);
    assertThat(created.getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
    assertThat(created.getInvestmentSubcategoryId()).isEqualTo(cdbId);
    assertThat(created.isClosed()).isFalse();
    assertThat(productRepository.findById(nextId)).isPresent();
  }

  @Test
  void createWithoutASubcategorySaves() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");

    assertThat(created.getInvestmentSubcategoryId()).isNull();
  }

  @Test
  void createRejectsAnUnknownAccount() {
    assertThatThrownBy(() -> service.create(UUID.randomUUID(), cryptoId, null, "Bitcoin Test"))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void createRejectsANonInvestmentAccount() {
    UUID checkingId =
        accountRepository
            .save(
                AccountMother.checking()
                    .withName("Checking Test")
                    .withInstitutionId(institutionId)
                    .build())
            .getId();

    assertThatThrownBy(() -> service.create(checkingId, cryptoId, null, "Bitcoin Test"))
        .isInstanceOf(InvestmentAccountRequiredException.class);
    assertThat(productRepository.findAll()).isEmpty();
  }

  @Test
  void createRejectsAClosedInvestmentAccount() {
    Account closed = accountRepository.findById(xpAccountId).orElseThrow();
    closed.close(LocalDate.now());
    accountRepository.save(closed);

    assertThatThrownBy(() -> service.create(xpAccountId, cryptoId, null, "Bitcoin Test"))
        .isInstanceOf(InvestmentAccountRequiredException.class);
  }

  @Test
  void createRejectsAnUnknownCategory() {
    assertThatThrownBy(() -> service.create(xpAccountId, UUID.randomUUID(), null, "Bitcoin Test"))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
  }

  @Test
  void createRejectsAnUnknownSubcategory() {
    assertThatThrownBy(
            () -> service.create(xpAccountId, fixedIncomeId, UUID.randomUUID(), "CDB Test 2"))
        .isInstanceOf(InvestmentSubcategoryNotFoundException.class);
  }

  @Test
  void createRejectsASubcategoryThatBelongsToAnotherCategory() {
    assertThatThrownBy(() -> service.create(xpAccountId, cryptoId, cdbId, "Bitcoin Test"))
        .isInstanceOf(InvestmentSubcategoryMismatchException.class);
    assertThat(productRepository.findAll()).isEmpty();
  }

  @Test
  void createRejectsADuplicateNameWithinTheSameAccount() {
    service.create(xpAccountId, fixedIncomeId, cdbId, "Tesouro Selic Test 2029");

    assertThatThrownBy(
            () -> service.create(xpAccountId, fixedIncomeId, cdbId, "Tesouro Selic Test 2029"))
        .isInstanceOf(InvestmentProductNameAlreadyExistsException.class);
  }

  @Test
  void theSameNameIsAllowedInAnotherAccount() {
    service.create(xpAccountId, fixedIncomeId, cdbId, "Tesouro Selic Test 2029");

    InvestmentProduct other =
        service.create(nuAccountId, fixedIncomeId, cdbId, "Tesouro Selic Test 2029");

    assertThat(other.getAccountId()).isEqualTo(nuAccountId);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void findAllFiltersByAccountWhenGiven() {
    InvestmentProduct atXp = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");
    InvestmentProduct atNu = service.create(nuAccountId, cryptoId, null, "Bitcoin Test");

    assertThat(service.findAll(null))
        .extracting(InvestmentProduct::getId)
        .containsExactlyInAnyOrder(atXp.getId(), atNu.getId());
    assertThat(service.findAll(xpAccountId))
        .extracting(InvestmentProduct::getId)
        .containsExactly(atXp.getId());
  }

  @Test
  void editReclassifiesAndRenames() {
    InvestmentProduct created = service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test");

    InvestmentProduct edited =
        service.edit(created.getId(), xpAccountId, cryptoId, null, "Bitcoin Test");

    assertThat(edited.getInvestmentCategoryId()).isEqualTo(cryptoId);
    assertThat(edited.getInvestmentSubcategoryId()).isNull();
    assertThat(edited.getName()).isEqualTo("Bitcoin Test");
  }

  @Test
  void editToItsOwnCurrentNameIsAllowed() {
    InvestmentProduct created = service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test");

    InvestmentProduct edited =
        service.edit(created.getId(), xpAccountId, fixedIncomeId, cdbId, "CDB Test");

    assertThat(edited.getName()).isEqualTo("CDB Test");
  }

  @Test
  void editRejectsADuplicateNameWithinTheAccount() {
    service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test");
    InvestmentProduct other = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");

    assertThatThrownBy(() -> service.edit(other.getId(), xpAccountId, cryptoId, null, "CDB Test"))
        .isInstanceOf(InvestmentProductNameAlreadyExistsException.class);
  }

  @Test
  void editRunsTheSameReferenceChecksAsCreate() {
    InvestmentProduct created = service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test");

    assertThatThrownBy(
            () -> service.edit(created.getId(), UUID.randomUUID(), fixedIncomeId, null, "CDB Test"))
        .isInstanceOf(AccountNotFoundException.class);
    assertThatThrownBy(
            () -> service.edit(created.getId(), xpAccountId, cryptoId, cdbId, "CDB Test"))
        .isInstanceOf(InvestmentSubcategoryMismatchException.class);
    assertThatThrownBy(
            () -> service.edit(created.getId(), xpAccountId, UUID.randomUUID(), null, "CDB Test"))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
    // The stored product is untouched by every rejected edit.
    InvestmentProduct reloaded = service.findById(created.getId());
    assertThat(reloaded.getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
    assertThat(reloaded.getInvestmentSubcategoryId()).isEqualTo(cdbId);
  }

  @Test
  void editOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(
            () -> service.edit(UUID.randomUUID(), xpAccountId, cryptoId, null, "Bitcoin Test"))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void closeSetsTheClosedDate() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");

    InvestmentProduct closed = service.close(created.getId());

    assertThat(closed.isClosed()).isTrue();
    assertThat(productRepository.findById(created.getId()).orElseThrow().isClosed()).isTrue();
  }

  @Test
  void closeOfAnAlreadyClosedProductThrowsConflict() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");
    service.close(created.getId());

    assertThatThrownBy(() -> service.close(created.getId()))
        .isInstanceOf(InvestmentProductAlreadyClosedException.class);
  }

  @Test
  void closeOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.close(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void deleteRemovesAProductWithZeroHistory() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");

    service.delete(created.getId());

    assertThat(productRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteIsBlockedOnceTheHistoryCheckerReportsHistory() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");
    historyChecker.markHasHistory(created.getId());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InvestmentProductHasHistoryException.class);
    assertThat(productRepository.findById(created.getId())).isPresent();
  }

  @Test
  void aProductWithHistoryCanStillBeClosed() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");
    historyChecker.markHasHistory(created.getId());

    assertThat(service.close(created.getId()).isClosed()).isTrue();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void hasHistoryDelegatesToTheChecker() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test");
    assertThat(service.hasHistory(created.getId())).isFalse();

    historyChecker.markHasHistory(created.getId());

    assertThat(service.hasHistory(created.getId())).isTrue();
  }

  @Test
  void hasHistoryOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.hasHistory(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }
}
