package com.chm.myfinances.application.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInstitutionRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakePaymentMethodRepository;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.fakes.FakeVehicleRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.RecurringTemplateMother;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuditReferenceLabels}' real, repository-backed constructor: every field it knows
 * how to resolve, resolving to the referenced entity's single label field, plus the explicit
 * non-goals (an unknown field, and an id that doesn't exist) that keep {@link AuditRecorder}'s
 * fallback to a bare id safe.
 */
class AuditReferenceLabelsTest {

  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeInstitutionRepository institutionRepository = new FakeInstitutionRepository();
  private final FakePaymentMethodRepository paymentMethodRepository =
      new FakePaymentMethodRepository();
  private final FakeVehicleRepository vehicleRepository = new FakeVehicleRepository();
  private final FakeInvestmentCategoryRepository investmentCategoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeInvestmentSubcategoryRepository investmentSubcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentSegmentRepository investmentSegmentRepository =
      new FakeInvestmentSegmentRepository();
  private final FakeInvestmentProductRepository investmentProductRepository =
      new FakeInvestmentProductRepository();
  private final FakeRecurringTemplateRepository recurringTemplateRepository =
      new FakeRecurringTemplateRepository();

  private final AuditReferenceLabels referenceLabels =
      new AuditReferenceLabels(
          accountRepository,
          categoryRepository,
          institutionRepository,
          paymentMethodRepository,
          vehicleRepository,
          investmentCategoryRepository,
          investmentSubcategoryRepository,
          investmentSegmentRepository,
          investmentProductRepository,
          recurringTemplateRepository);

  @Test
  void resolvesInstitutionIdToTheInstitutionsName() {
    Institution institution =
        institutionRepository.save(Institution.create(UUID.randomUUID(), "Nubank"));

    assertThat(referenceLabels.resolves("institutionId")).isTrue();
    assertThat(referenceLabels.labelFor("institutionId", institution.getId().toString()))
        .contains("Nubank");
  }

  @Test
  void resolvesCategoryIdToTheCategorysName() {
    Category category =
        categoryRepository.save(
            Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE));

    assertThat(referenceLabels.labelFor("categoryId", category.getId().toString()))
        .contains("Groceries");
  }

  @Test
  void resolvesEveryAccountFieldNameToTheAccountsName() {
    Account account = accountRepository.save(AccountMother.checking().withName("Checking").build());
    String id = account.getId().toString();

    assertThat(referenceLabels.labelFor("accountId", id)).contains("Checking");
    assertThat(referenceLabels.labelFor("fromAccountId", id)).contains("Checking");
    assertThat(referenceLabels.labelFor("toAccountId", id)).contains("Checking");
  }

  @Test
  void resolvesPaymentMethodIdToItsName() {
    PaymentMethod paymentMethod =
        paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Credit card"));

    assertThat(referenceLabels.labelFor("paymentMethodId", paymentMethod.getId().toString()))
        .contains("Credit card");
  }

  @Test
  void resolvesVehicleIdToItsName() {
    Vehicle vehicle = vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Civic"));

    assertThat(referenceLabels.labelFor("vehicleId", vehicle.getId().toString())).contains("Civic");
  }

  @Test
  void resolvesInvestmentCategoryIdToItsName() {
    InvestmentCategory category =
        investmentCategoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), "FII"));

    assertThat(referenceLabels.labelFor("investmentCategoryId", category.getId().toString()))
        .contains("FII");
  }

  @Test
  void resolvesInvestmentSubcategoryIdToItsName() {
    InvestmentSubcategory subcategory =
        investmentSubcategoryRepository.save(
            InvestmentSubcategory.create(UUID.randomUUID(), UUID.randomUUID(), "Papel"));

    assertThat(referenceLabels.labelFor("investmentSubcategoryId", subcategory.getId().toString()))
        .contains("Papel");
  }

  @Test
  void resolvesSegmentIdToTheInvestmentSegmentsName() {
    InvestmentSegment segment =
        investmentSegmentRepository.save(InvestmentSegment.create(UUID.randomUUID(), "Logistics"));

    assertThat(referenceLabels.labelFor("segmentId", segment.getId().toString()))
        .contains("Logistics");
  }

  @Test
  void resolvesBothProductFieldNamesToTheInvestmentProductsName() {
    InvestmentProduct product =
        investmentProductRepository.save(
            InvestmentProductMother.product().withName("KNRI11").build());
    String id = product.getId().toString();

    assertThat(referenceLabels.labelFor("productId", id)).contains("KNRI11");
    assertThat(referenceLabels.labelFor("investmentProductId", id)).contains("KNRI11");
  }

  @Test
  void resolvesTemplateIdToTheRecurringTemplatesDescription() {
    RecurringTemplate template =
        recurringTemplateRepository.save(
            RecurringTemplateMother.template().withDescription("Rent").build());

    assertThat(referenceLabels.labelFor("templateId", template.getId().toString()))
        .contains("Rent");
  }

  @Test
  void anUnknownFieldDoesNotResolve() {
    assertThat(referenceLabels.resolves("recurringTemplateVersionId")).isFalse();
    assertThat(referenceLabels.labelFor("recurringTemplateVersionId", UUID.randomUUID().toString()))
        .isEmpty();
  }

  @Test
  void anIdThatDoesNotExistResolvesToEmpty() {
    assertThat(referenceLabels.labelFor("institutionId", UUID.randomUUID().toString())).isEmpty();
  }

  @Test
  void noneResolvesNothing() {
    AuditReferenceLabels none = AuditReferenceLabels.none();

    assertThat(none.resolves("institutionId")).isFalse();
  }
}
