package com.chm.myfinances.application.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.investmentcategory.InvestmentCategoryNotFoundException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingService;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.application.investmentsubcategory.InvestmentSubcategoryNotFoundException;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeHasHoldingHistoryChecker;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentProductService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context (F022 spec: pure taxonomy, two-write {@code
 * create}). Includes plan.md's explicit test-first item: hard delete is only allowed at zero
 * holdings (not zero history - the holding itself has the stricter rule).
 */
class InvestmentProductServiceTest {

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeInvestmentCategoryRepository categoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeHasHoldingHistoryChecker holdingHistoryChecker =
      new FakeHasHoldingHistoryChecker();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final InvestmentHoldingService holdingService =
      new InvestmentHoldingService(
          holdingRepository,
          productRepository,
          accountRepository,
          holdingHistoryChecker,
          latestSnapshotQuery,
          idGenerator,
          Clock.systemDefaultZone());
  private final InvestmentProductService service =
      new InvestmentProductService(
          productRepository,
          categoryRepository,
          subcategoryRepository,
          holdingRepository,
          holdingService,
          idGenerator);

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
  void createAssignsIdFromIdGeneratorAndCreatesItsFirstHolding() {
    UUID nextId = UUID.randomUUID();
    FakeIdGenerator singleUseIdGenerator = new FakeIdGenerator(nextId);
    InvestmentProductService service =
        new InvestmentProductService(
            productRepository,
            categoryRepository,
            subcategoryRepository,
            holdingRepository,
            holdingService,
            singleUseIdGenerator);

    InvestmentProduct created =
        service.create(
            xpAccountId, fixedIncomeId, cdbId, "CDB Banco Test 110% CDI", "matures 2030");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
    assertThat(created.getInvestmentSubcategoryId()).isEqualTo(cdbId);
    assertThat(created.getAdditionalNotes()).isEqualTo("matures 2030");
    assertThat(productRepository.findById(nextId)).isPresent();
    assertThat(holdingRepository.findByProductId(nextId))
        .hasSize(1)
        .first()
        .satisfies(h -> assertThat(h.getAccountId()).isEqualTo(xpAccountId));
  }

  @Test
  void createWithoutASubcategorySaves() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);

    assertThat(created.getInvestmentSubcategoryId()).isNull();
  }

  @Test
  void createRejectsAnUnknownAccount() {
    // The fakes here have no real transaction manager, so they can't prove the product insert
    // rolls back - that's InvestmentProductServiceTransactionalTest's job (real Spring context).
    assertThatThrownBy(
            () -> service.create(UUID.randomUUID(), cryptoId, null, "Bitcoin Test", null))
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

    assertThatThrownBy(() -> service.create(checkingId, cryptoId, null, "Bitcoin Test", null))
        .isInstanceOf(
            com.chm.myfinances.application.investmentproduct.InvestmentAccountRequiredException
                .class);
  }

  @Test
  void createRejectsAnUnknownCategory() {
    assertThatThrownBy(
            () -> service.create(xpAccountId, UUID.randomUUID(), null, "Bitcoin Test", null))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
  }

  @Test
  void createRejectsAnUnknownSubcategory() {
    assertThatThrownBy(
            () -> service.create(xpAccountId, fixedIncomeId, UUID.randomUUID(), "CDB Test 2", null))
        .isInstanceOf(InvestmentSubcategoryNotFoundException.class);
  }

  @Test
  void createRejectsASubcategoryThatBelongsToAnotherCategory() {
    assertThatThrownBy(() -> service.create(xpAccountId, cryptoId, cdbId, "Bitcoin Test", null))
        .isInstanceOf(InvestmentSubcategoryMismatchException.class);
    assertThat(productRepository.findAll()).isEmpty();
  }

  @Test
  void createRejectsAGloballyDuplicateName() {
    service.create(xpAccountId, fixedIncomeId, cdbId, "Tesouro Selic Test 2029", null);

    assertThatThrownBy(
            () ->
                service.create(nuAccountId, fixedIncomeId, cdbId, "Tesouro Selic Test 2029", null))
        .isInstanceOf(InvestmentProductNameAlreadyExistsException.class);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void findAllListsEveryProduct() {
    InvestmentProduct one = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);
    InvestmentProduct two = service.create(nuAccountId, cryptoId, null, "Ethereum Test", null);

    assertThat(service.findAll())
        .extracting(InvestmentProduct::getId)
        .containsExactlyInAnyOrder(one.getId(), two.getId());
  }

  @Test
  void editReclassifiesRenamesAndUpdatesNotes() {
    InvestmentProduct created = service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test", null);

    InvestmentProduct edited =
        service.edit(created.getId(), cryptoId, null, "Bitcoin Test", "renamed");

    assertThat(edited.getInvestmentCategoryId()).isEqualTo(cryptoId);
    assertThat(edited.getInvestmentSubcategoryId()).isNull();
    assertThat(edited.getName()).isEqualTo("Bitcoin Test");
    assertThat(edited.getAdditionalNotes()).isEqualTo("renamed");
  }

  @Test
  void editToItsOwnCurrentNameIsAllowed() {
    InvestmentProduct created = service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test", null);

    InvestmentProduct edited =
        service.edit(created.getId(), fixedIncomeId, cdbId, "CDB Test", null);

    assertThat(edited.getName()).isEqualTo("CDB Test");
  }

  @Test
  void editRejectsADuplicateName() {
    service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test", null);
    InvestmentProduct other = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);

    assertThatThrownBy(() -> service.edit(other.getId(), cryptoId, null, "CDB Test", null))
        .isInstanceOf(InvestmentProductNameAlreadyExistsException.class);
  }

  @Test
  void editRunsTheSameReferenceChecksAsCreate() {
    InvestmentProduct created = service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Test", null);

    assertThatThrownBy(() -> service.edit(created.getId(), cryptoId, cdbId, "CDB Test", null))
        .isInstanceOf(InvestmentSubcategoryMismatchException.class);
    assertThatThrownBy(
            () -> service.edit(created.getId(), UUID.randomUUID(), null, "CDB Test", null))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
    // The stored product is untouched by every rejected edit.
    InvestmentProduct reloaded = service.findById(created.getId());
    assertThat(reloaded.getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
    assertThat(reloaded.getInvestmentSubcategoryId()).isEqualTo(cdbId);
  }

  @Test
  void editOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.edit(UUID.randomUUID(), cryptoId, null, "Bitcoin Test", null))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void deleteRemovesAProductWithZeroHoldings() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);
    // Remove the auto-created holding so the product has zero holdings.
    List<InvestmentHolding> holdings = holdingRepository.findByProductId(created.getId());
    holdingRepository.deleteById(holdings.get(0).getId());

    service.delete(created.getId());

    assertThat(productRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteIsBlockedWhileTheProductStillHasAHolding() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InvestmentProductHasHoldingsException.class);
    assertThat(productRepository.findById(created.getId())).isPresent();
  }

  @Test
  void deleteIsBlockedEvenByAClosedEmptyHolding() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);
    InvestmentHolding holding = holdingRepository.findByProductId(created.getId()).get(0);
    holding.close(java.time.LocalDate.now());
    holdingRepository.save(holding);

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InvestmentProductHasHoldingsException.class);
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }
}
