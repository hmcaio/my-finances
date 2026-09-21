package com.chm.myfinances.application.investmentsubcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentcategory.InvestmentCategoryNotFoundException;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.FakeInvestmentSubcategoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentSubcategoryService}, written first (ADR 0004)
 * against hand-written fakes - plain JUnit, no Spring context (F008 spec). Names are unique per
 * parent category, not globally.
 */
class InvestmentSubcategoryServiceTest {

  private final FakeInvestmentCategoryRepository categoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final InvestmentSubcategoryService service =
      new InvestmentSubcategoryService(
          subcategoryRepository, categoryRepository, productRepository, new FakeIdGenerator());

  private final UUID fixedIncomeId =
      categoryRepository
          .save(InvestmentCategory.create(UUID.randomUUID(), "Fixed Income Test"))
          .getId();
  private final UUID internationalId =
      categoryRepository
          .save(InvestmentCategory.create(UUID.randomUUID(), "International Test"))
          .getId();

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    InvestmentSubcategoryService service =
        new InvestmentSubcategoryService(
            subcategoryRepository,
            categoryRepository,
            productRepository,
            new FakeIdGenerator(nextId));

    InvestmentSubcategory created = service.create(fixedIncomeId, "CDB");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
    assertThat(created.getName()).isEqualTo("CDB");
    assertThat(subcategoryRepository.findById(nextId)).isPresent();
  }

  @Test
  void createUnderAnUnknownParentThrowsNotFound() {
    assertThatThrownBy(() -> service.create(UUID.randomUUID(), "CDB"))
        .isInstanceOf(InvestmentCategoryNotFoundException.class);
    assertThat(subcategoryRepository.findAll()).isEmpty();
  }

  @Test
  void createRejectsADuplicateNameWithinTheSameParent() {
    service.create(fixedIncomeId, "CDB");

    assertThatThrownBy(() -> service.create(fixedIncomeId, "CDB"))
        .isInstanceOf(InvestmentSubcategoryNameAlreadyExistsException.class);
  }

  @Test
  void theSameNameIsAllowedUnderADifferentParent() {
    service.create(fixedIncomeId, "ETFs");

    InvestmentSubcategory other = service.create(internationalId, "ETFs");

    assertThat(other.getInvestmentCategoryId()).isEqualTo(internationalId);
  }

  @Test
  void renameChangesTheNameAndKeepsTheParent() {
    InvestmentSubcategory created = service.create(fixedIncomeId, "CDB");

    InvestmentSubcategory renamed = service.rename(created.getId(), "CDB / RDB");

    assertThat(renamed.getName()).isEqualTo("CDB / RDB");
    assertThat(renamed.getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
  }

  @Test
  void renameToItsOwnCurrentNameIsAllowed() {
    InvestmentSubcategory created = service.create(fixedIncomeId, "CDB");

    assertThat(service.rename(created.getId(), "CDB").getName()).isEqualTo("CDB");
  }

  @Test
  void renameRejectsASiblingsNameButAllowsOneUsedUnderAnotherParent() {
    service.create(fixedIncomeId, "CDB");
    service.create(internationalId, "Bonds");
    InvestmentSubcategory lci = service.create(fixedIncomeId, "LCI");

    assertThatThrownBy(() -> service.rename(lci.getId(), "CDB"))
        .isInstanceOf(InvestmentSubcategoryNameAlreadyExistsException.class);
    assertThat(service.rename(lci.getId(), "Bonds").getName()).isEqualTo("Bonds");
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "Anything"))
        .isInstanceOf(InvestmentSubcategoryNotFoundException.class);
  }

  @Test
  void deleteRemovesAnUnreferencedSubcategory() {
    InvestmentSubcategory created = service.create(fixedIncomeId, "CDB");

    service.delete(created.getId());

    assertThat(subcategoryRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InvestmentSubcategoryNotFoundException.class);
  }

  @Test
  void deleteIsBlockedWhileAProductUsesIt() {
    InvestmentSubcategory created = service.create(fixedIncomeId, "CDB");
    productRepository.save(
        InvestmentProduct.create(
            UUID.randomUUID(), UUID.randomUUID(), fixedIncomeId, created.getId(), "CDB Test"));

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InvestmentSubcategoryInUseException.class);
    assertThat(subcategoryRepository.findById(created.getId())).isPresent();
  }
}
