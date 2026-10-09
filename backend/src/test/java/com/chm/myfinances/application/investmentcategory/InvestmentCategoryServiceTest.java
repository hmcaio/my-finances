package com.chm.myfinances.application.investmentcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentCategoryService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context (F008 spec).
 */
class InvestmentCategoryServiceTest {

  private final FakeInvestmentCategoryRepository categoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final InvestmentCategoryService service =
      new InvestmentCategoryService(
          categoryRepository,
          subcategoryRepository,
          productRepository,
          new FakeIdGenerator(),
          new AuditRecorder(auditLog));

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    InvestmentCategoryService service =
        new InvestmentCategoryService(
            categoryRepository,
            subcategoryRepository,
            productRepository,
            new FakeIdGenerator(nextId),
            new AuditRecorder(auditLog));

    InvestmentCategory created = service.create("Fixed Income");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Fixed Income");
    assertThat(categoryRepository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create("Fixed Income");

    assertThatThrownBy(() -> service.create("Fixed Income"))
        .isInstanceOf(InvestmentCategoryNameAlreadyExistsException.class);
  }

  @Test
  void findAllNestsTheSubcategoriesAndSortsByName() {
    InvestmentCategory variable = service.create("Variable Income");
    InvestmentCategory fixed = service.create("Fixed Income");
    InvestmentCategory crypto = service.create("Crypto");
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), fixed.getId(), "LCI"));
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), fixed.getId(), "CDB"));
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), variable.getId(), "ETFs"));

    List<InvestmentCategoryWithSubcategories> all = service.findAllWithSubcategories();

    assertThat(all)
        .extracting(c -> c.category().getName())
        .containsExactly("Crypto", "Fixed Income", "Variable Income");
    assertThat(all.get(0).category().getId()).isEqualTo(crypto.getId());
    assertThat(all.get(0).subcategories()).isEmpty();
    assertThat(all.get(1).subcategories())
        .extracting(InvestmentSubcategory::getName)
        .containsExactly("CDB", "LCI");
    assertThat(all.get(2).subcategories())
        .extracting(InvestmentSubcategory::getName)
        .containsExactly("ETFs");
  }

  @Test
  void findWithSubcategoriesNestsOnlyThatCategorysChildrenSortedByName() {
    InvestmentCategory fixed = service.create("Fixed Income");
    InvestmentCategory variable = service.create("Variable Income");
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), fixed.getId(), "LCI"));
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), fixed.getId(), "CDB"));
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), variable.getId(), "ETFs"));

    InvestmentCategoryWithSubcategories nested = service.findWithSubcategories(fixed.getId());

    assertThat(nested.category().getId()).isEqualTo(fixed.getId());
    assertThat(nested.subcategories())
        .extracting(InvestmentSubcategory::getName)
        .containsExactly("CDB", "LCI");
  }

  @Test
  void findWithSubcategoriesOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findWithSubcategories(UUID.randomUUID()))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
  }

  @Test
  void renameChangesTheName() {
    InvestmentCategory created = service.create("Funds");

    InvestmentCategory renamed = service.rename(created.getId(), "Investment Funds");

    assertThat(renamed.getName()).isEqualTo("Investment Funds");
    assertThat(categoryRepository.findById(created.getId()).orElseThrow().getName())
        .isEqualTo("Investment Funds");
  }

  @Test
  void renameToItsOwnCurrentNameIsAllowed() {
    InvestmentCategory created = service.create("Funds");

    assertThat(service.rename(created.getId(), "Funds").getName()).isEqualTo("Funds");
  }

  @Test
  void renameRejectsADuplicateName() {
    service.create("Funds");
    InvestmentCategory other = service.create("Crypto");

    assertThatThrownBy(() -> service.rename(other.getId(), "Funds"))
        .isInstanceOf(InvestmentCategoryNameAlreadyExistsException.class);
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "Anything"))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
  }

  @Test
  void renameIsAllowedEvenWhenItHasSubcategoriesAndProducts() {
    InvestmentCategory category = service.create("Funds");
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), category.getId(), "Multimercado"));
    productRepository.save(
        InvestmentProductMother.product()
            .withInvestmentCategoryId(category.getId())
            .withInvestmentSubcategoryId(null)
            .withName("Product Test")
            .build());

    assertThat(service.rename(category.getId(), "Investment Funds").getName())
        .isEqualTo("Investment Funds");
  }

  @Test
  void deleteRemovesAnUnreferencedCategory() {
    InvestmentCategory created = service.create("Other");

    service.delete(created.getId());

    assertThat(categoryRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
  }

  @Test
  void deleteIsBlockedWhileItHasSubcategories() {
    InvestmentCategory category = service.create("Funds");
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), category.getId(), "Multimercado"));

    assertThatThrownBy(() -> service.delete(category.getId()))
        .isInstanceOf(InvestmentCategoryInUseException.class);
    assertThat(categoryRepository.findById(category.getId())).isPresent();
  }

  @Test
  void deleteIsBlockedWhileAProductUsesIt() {
    InvestmentCategory category = service.create("Crypto");
    productRepository.save(
        InvestmentProductMother.product()
            .withInvestmentCategoryId(category.getId())
            .withInvestmentSubcategoryId(null)
            .withName("Bitcoin Test")
            .build());

    assertThatThrownBy(() -> service.delete(category.getId()))
        .isInstanceOf(InvestmentCategoryInUseException.class);
    assertThat(categoryRepository.findById(category.getId())).isPresent();
  }

  @Test
  void createRecordsACreateAuditEntry() {
    InvestmentCategory created = service.create("Fixed Income");

    var entry = auditLog.onlyEntry();
    assertThat(entry.entityId()).isEqualTo(created.getId());
    assertThat(entry.entityLabel()).isEqualTo("Fixed Income");
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CREATE);
  }

  @Test
  void renameRecordsAnUpdateAuditEntry() {
    InvestmentCategory created = service.create("Fixed Income");
    auditLog.entries().clear();

    service.rename(created.getId(), "Renda Fixa");

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.UPDATE);
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    InvestmentCategory created = service.create("Fixed Income");
    auditLog.entries().clear();

    service.delete(created.getId());

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.DELETE);
  }
}
