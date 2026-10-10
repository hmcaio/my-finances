package com.chm.myfinances.application.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
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
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeHasHoldingHistoryChecker;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
  private final FakeInvestmentSegmentRepository segmentRepository =
      new FakeInvestmentSegmentRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeHasHoldingHistoryChecker holdingHistoryChecker =
      new FakeHasHoldingHistoryChecker();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final InvestmentHoldingService holdingService =
      new InvestmentHoldingService(
          holdingRepository,
          productRepository,
          accountRepository,
          holdingHistoryChecker,
          latestSnapshotQuery,
          idGenerator,
          Clock.systemDefaultZone(),
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));
  private final InvestmentProductService service =
      new InvestmentProductService(
          productRepository,
          categoryRepository,
          subcategoryRepository,
          segmentRepository,
          holdingRepository,
          holdingService,
          idGenerator,
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));

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
            segmentRepository,
            holdingRepository,
            holdingService,
            singleUseIdGenerator,
            new AuditRecorder(auditLog, AuditReferenceLabels.none()));

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

  // --- F023: paginated/filtered global product list ---

  @Test
  void isClosedIsFalseWhileAnyHoldingIsOpen() {
    InvestmentProduct product =
        service.create(xpAccountId, cryptoId, null, "Open Closed Test", null);

    assertThat(service.isClosed(product.getId())).isFalse();
  }

  @Test
  void isClosedIsTrueWhenEveryHoldingIsClosedOrThereAreNone() {
    InvestmentProduct allClosed =
        service.create(xpAccountId, cryptoId, null, "All Closed Is Closed Test", null);
    closeOnlyHolding(allClosed);
    assertThat(service.isClosed(allClosed.getId())).isTrue();

    InvestmentProduct zeroHoldings =
        service.create(xpAccountId, cryptoId, null, "Zero Holdings Is Closed Test", null);
    holdingRepository.deleteById(
        holdingRepository.findByProductId(zeroHoldings.getId()).get(0).getId());
    assertThat(service.isClosed(zeroHoldings.getId())).isTrue();
  }

  private void closeOnlyHolding(InvestmentProduct product) {
    InvestmentHolding holding = holdingRepository.findByProductId(product.getId()).get(0);
    holding.close(LocalDate.now());
    holdingRepository.save(holding);
  }

  @Test
  void findAllWithFilterDefaultsToOpenStatusWhenStatusIsNull() {
    InvestmentProduct open = service.create(xpAccountId, cryptoId, null, "Open Default Test", null);
    InvestmentProduct closed =
        service.create(nuAccountId, cryptoId, null, "Closed Default Test", null);
    closeOnlyHolding(closed);

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(null, null, null, null, null), Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactly(open.getId());
  }

  @Test
  void findAllFiltersByCategoryAlone() {
    InvestmentProduct fixedIncomeProduct =
        service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Category Test", null);
    service.create(xpAccountId, cryptoId, null, "Bitcoin Category Test", null);

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(
                fixedIncomeId, null, null, null, InvestmentProductStatus.ALL),
            Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactly(fixedIncomeProduct.getId());
  }

  @Test
  void findAllFiltersBySubcategoryAlone() {
    InvestmentProduct withSub =
        service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Sub Test", null);
    service.create(xpAccountId, fixedIncomeId, null, "Bare Sub Test", null);

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(null, cdbId, null, null, InvestmentProductStatus.ALL),
            Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactly(withSub.getId());
  }

  @Test
  void findAllFiltersByAccountAloneMeaningHasAHoldingThere() {
    InvestmentProduct atXp = service.create(xpAccountId, cryptoId, null, "At XP Test", null);
    InvestmentProduct atNu = service.create(nuAccountId, cryptoId, null, "At NU Test", null);

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(null, null, nuAccountId, null, InvestmentProductStatus.ALL),
            Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactly(atNu.getId());
    assertThat(page.getContent()).extracting(InvestmentProduct::getId).doesNotContain(atXp.getId());
  }

  @Test
  void findAllFiltersByNameContainsCaseInsensitive() {
    InvestmentProduct match = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);
    service.create(xpAccountId, cryptoId, null, "Ethereum Test", null);

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(null, null, null, "bITcoin", InvestmentProductStatus.ALL),
            Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactly(match.getId());
  }

  @Test
  void findAllStatusClosedListsProductsWhereEveryHoldingIsClosedOrThereAreNone() {
    InvestmentProduct allClosed =
        service.create(xpAccountId, cryptoId, null, "All Closed Test", null);
    closeOnlyHolding(allClosed);
    service.create(xpAccountId, cryptoId, null, "Still Open Test", null);
    InvestmentProduct zeroHoldings =
        service.create(xpAccountId, cryptoId, null, "Zero Holdings Test", null);
    holdingRepository.deleteById(
        holdingRepository.findByProductId(zeroHoldings.getId()).get(0).getId());

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(null, null, null, null, InvestmentProductStatus.CLOSED),
            Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactlyInAnyOrder(allClosed.getId(), zeroHoldings.getId());
  }

  @Test
  void findAllStatusAllListsEveryProductRegardlessOfHoldingState() {
    InvestmentProduct open = service.create(xpAccountId, cryptoId, null, "Open All Test", null);
    InvestmentProduct closed = service.create(nuAccountId, cryptoId, null, "Closed All Test", null);
    closeOnlyHolding(closed);

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(null, null, null, null, InvestmentProductStatus.ALL),
            Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactlyInAnyOrder(open.getId(), closed.getId());
  }

  @Test
  void findAllCombinesEveryFilterDimensionWithAnd() {
    InvestmentProduct matches =
        service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Combined Test", null);
    service.create(nuAccountId, fixedIncomeId, cdbId, "CDB Combined Other Account Test", null);
    service.create(xpAccountId, cryptoId, null, "Bitcoin Combined Test", null);

    Page<InvestmentProduct> page =
        service.findAll(
            new InvestmentProductFilter(
                fixedIncomeId, cdbId, xpAccountId, "combined", InvestmentProductStatus.ALL),
            Pageable.unpaged());

    assertThat(page.getContent())
        .extracting(InvestmentProduct::getId)
        .containsExactly(matches.getId());
  }

  @Test
  void aProductHeldAtTwoAccountsAppearsExactlyOnceInTheUnfilteredListAndUnderBothAccountFilters() {
    InvestmentProduct product =
        service.create(xpAccountId, cryptoId, null, "Multi-holding Test", null);
    holdingService.create(product.getId(), nuAccountId, null);

    Page<InvestmentProduct> all =
        service.findAll(
            new InvestmentProductFilter(null, null, null, null, InvestmentProductStatus.ALL),
            Pageable.unpaged());
    assertThat(all.getContent()).filteredOn(p -> p.getId().equals(product.getId())).hasSize(1);

    Page<InvestmentProduct> atXp =
        service.findAll(
            new InvestmentProductFilter(null, null, xpAccountId, null, InvestmentProductStatus.ALL),
            Pageable.unpaged());
    Page<InvestmentProduct> atNu =
        service.findAll(
            new InvestmentProductFilter(null, null, nuAccountId, null, InvestmentProductStatus.ALL),
            Pageable.unpaged());
    assertThat(atXp.getContent()).extracting(InvestmentProduct::getId).contains(product.getId());
    assertThat(atNu.getContent()).extracting(InvestmentProduct::getId).contains(product.getId());
  }

  @Test
  void findAllPaginatesTheFilteredResultsSortedByName() {
    service.create(xpAccountId, cryptoId, null, "Alpha Page Test", null);
    service.create(xpAccountId, cryptoId, null, "Bravo Page Test", null);
    service.create(xpAccountId, cryptoId, null, "Charlie Page Test", null);

    Page<InvestmentProduct> firstPage =
        service.findAll(
            new InvestmentProductFilter(null, null, null, "Page Test", InvestmentProductStatus.ALL),
            PageRequest.of(0, 2));

    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getContent())
        .extracting(InvestmentProduct::getName)
        .containsExactly("Alpha Page Test", "Bravo Page Test");

    Page<InvestmentProduct> secondPage =
        service.findAll(
            new InvestmentProductFilter(null, null, null, "Page Test", InvestmentProductStatus.ALL),
            PageRequest.of(1, 2));

    assertThat(secondPage.getContent())
        .extracting(InvestmentProduct::getName)
        .containsExactly("Charlie Page Test");
  }

  // --- F026: optional ticker/segmentId ---

  @Test
  void createCarriesAnOptionalTickerAndSegmentId() {
    UUID segmentId =
        segmentRepository
            .save(
                com.chm.myfinances.domain.investmentsegment.InvestmentSegment.create(
                    UUID.randomUUID(), "Shoppings Test"))
            .getId();

    InvestmentProduct created =
        service.create(xpAccountId, fixedIncomeId, cdbId, "KNRI11 Test", null, "KNRI11", segmentId);

    assertThat(created.getTicker()).isEqualTo("KNRI11");
    assertThat(created.getSegmentId()).isEqualTo(segmentId);
  }

  @Test
  void createRejectsAnUnknownSegmentId() {
    assertThatThrownBy(
            () ->
                service.create(
                    xpAccountId,
                    fixedIncomeId,
                    cdbId,
                    "KNRI11 Test",
                    null,
                    "KNRI11",
                    UUID.randomUUID()))
        .isInstanceOf(
            com.chm.myfinances.application.investmentsegment.InvestmentSegmentNotFoundException
                .class);
    assertThat(productRepository.existsByName("KNRI11 Test")).isFalse();
  }

  @Test
  void editReplacesTickerAndSegmentId() {
    UUID segmentId =
        segmentRepository
            .save(
                com.chm.myfinances.domain.investmentsegment.InvestmentSegment.create(
                    UUID.randomUUID(), "Logistica Test"))
            .getId();
    InvestmentProduct created =
        service.create(xpAccountId, fixedIncomeId, cdbId, "HGLG11 Test", null);

    InvestmentProduct edited =
        service.edit(
            created.getId(), fixedIncomeId, cdbId, "HGLG11 Test", null, "HGLG11", segmentId);

    assertThat(edited.getTicker()).isEqualTo("HGLG11");
    assertThat(edited.getSegmentId()).isEqualTo(segmentId);
  }

  @Test
  void editRejectsAnUnknownSegmentId() {
    InvestmentProduct created =
        service.create(xpAccountId, fixedIncomeId, cdbId, "HGLG11 Test", null);

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    fixedIncomeId,
                    cdbId,
                    "HGLG11 Test",
                    null,
                    "HGLG11",
                    UUID.randomUUID()))
        .isInstanceOf(
            com.chm.myfinances.application.investmentsegment.InvestmentSegmentNotFoundException
                .class);
  }

  @Test
  void createRecordsACreateAuditEntryForTheProductAndItsFirstHolding() {
    InvestmentProduct created =
        service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Banco Test", "matures 2030");

    assertThat(auditLog.entries())
        .anySatisfy(
            entry -> {
              assertThat(entry.entityType())
                  .isEqualTo(
                      com.chm.myfinances.application.auditlog.AuditEntityType.INVESTMENT_PRODUCT);
              assertThat(entry.entityId()).isEqualTo(created.getId());
              assertThat(entry.entityLabel()).isEqualTo("CDB Banco Test");
              assertThat(entry.action())
                  .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CREATE);
            });
    assertThat(auditLog.entries())
        .anySatisfy(
            entry ->
                assertThat(entry.entityType())
                    .isEqualTo(
                        com.chm.myfinances.application.auditlog.AuditEntityType
                            .INVESTMENT_HOLDING));
  }

  @Test
  void editRecordsAnUpdateAuditEntry() {
    InvestmentProduct created =
        service.create(xpAccountId, fixedIncomeId, cdbId, "CDB Banco Test", null);
    auditLog.entries().clear();

    service.edit(created.getId(), fixedIncomeId, cdbId, "CDB Banco Renamed", null);

    var entry = auditLog.onlyEntry();
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.UPDATE);
    assertThat(entry.changes()).containsKey("name");
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    InvestmentProduct created = service.create(xpAccountId, cryptoId, null, "Bitcoin Test", null);
    holdingRepository.deleteById(holdingRepository.findByProductId(created.getId()).get(0).getId());
    auditLog.entries().clear();

    service.delete(created.getId());

    var entry = auditLog.onlyEntry();
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.DELETE);
    assertThat(entry.entityLabel()).isEqualTo("Bitcoin Test");
  }
}
