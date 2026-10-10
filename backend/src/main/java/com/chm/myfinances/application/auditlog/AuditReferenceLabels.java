package com.chm.myfinances.application.auditlog;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves a reference field's id to the referenced entity's label, for the handful of {@code
 * toAuditSnapshot()} fields whose referenced aggregate has a single-field name/description ({@code
 * TextFieldConstraints.MAX_NAME_LENGTH}-style flat taxonomy entities, plus {@code
 * RecurringTemplate.description}). Deliberately excludes reference fields whose target has no such
 * single-field label - {@code budgetId}, {@code planId}, {@code holdingId}/{@code
 * investmentHoldingId}, {@code recurringTemplateVersionId} - those already pass {@code null} as
 * their own {@code entityLabel} (see e.g. {@code BudgetService}) because there's no one field to
 * show; resolving them would need a composed label, not a lookup, so they stay bare ids on the
 * frontend for now.
 *
 * <p>Looked up by {@link AuditRecorder} at write time (same transaction as the change, ADR 0022),
 * not at read time - so a later rename or delete never changes what an old audit row shows.
 *
 * <p>Spring autowires the real, repository-backed constructor below (the only public one); {@link
 * #none()} and the package-private map constructor exist only for tests outside this package that
 * build an {@link AuditRecorder} without caring about reference resolution.
 */
@Component
public class AuditReferenceLabels {

  private final Map<String, Function<UUID, Optional<String>>> resolversByField;

  AuditReferenceLabels(Map<String, Function<UUID, Optional<String>>> resolversByField) {
    this.resolversByField = resolversByField;
  }

  /** No fields resolve - every reference field stays a bare id string. */
  public static AuditReferenceLabels none() {
    return new AuditReferenceLabels(Map.of());
  }

  @Autowired
  public AuditReferenceLabels(
      AccountRepository accountRepository,
      CategoryRepository categoryRepository,
      InstitutionRepository institutionRepository,
      PaymentMethodRepository paymentMethodRepository,
      VehicleRepository vehicleRepository,
      InvestmentCategoryRepository investmentCategoryRepository,
      InvestmentSubcategoryRepository investmentSubcategoryRepository,
      InvestmentSegmentRepository investmentSegmentRepository,
      InvestmentProductRepository investmentProductRepository,
      RecurringTemplateRepository recurringTemplateRepository) {
    Function<UUID, Optional<String>> accountName =
        id -> accountRepository.findById(id).map(a -> a.getName());
    Function<UUID, Optional<String>> categoryName =
        id -> categoryRepository.findById(id).map(c -> c.getName());
    Function<UUID, Optional<String>> institutionName =
        id -> institutionRepository.findById(id).map(i -> i.getName());
    Function<UUID, Optional<String>> paymentMethodName =
        id -> paymentMethodRepository.findById(id).map(p -> p.getName());
    Function<UUID, Optional<String>> vehicleName =
        id -> vehicleRepository.findById(id).map(v -> v.getName());
    Function<UUID, Optional<String>> investmentCategoryName =
        id -> investmentCategoryRepository.findById(id).map(c -> c.getName());
    Function<UUID, Optional<String>> investmentSubcategoryName =
        id -> investmentSubcategoryRepository.findById(id).map(s -> s.getName());
    Function<UUID, Optional<String>> investmentSegmentName =
        id -> investmentSegmentRepository.findById(id).map(s -> s.getName());
    Function<UUID, Optional<String>> investmentProductName =
        id -> investmentProductRepository.findById(id).map(p -> p.getName());
    Function<UUID, Optional<String>> recurringTemplateDescription =
        id -> recurringTemplateRepository.findById(id).map(t -> t.getDescription());

    resolversByField =
        Map.ofEntries(
            Map.entry("accountId", accountName),
            Map.entry("fromAccountId", accountName),
            Map.entry("toAccountId", accountName),
            Map.entry("categoryId", categoryName),
            Map.entry("institutionId", institutionName),
            Map.entry("paymentMethodId", paymentMethodName),
            Map.entry("vehicleId", vehicleName),
            Map.entry("investmentCategoryId", investmentCategoryName),
            Map.entry("investmentSubcategoryId", investmentSubcategoryName),
            Map.entry("segmentId", investmentSegmentName),
            Map.entry("productId", investmentProductName),
            Map.entry("investmentProductId", investmentProductName),
            Map.entry("templateId", recurringTemplateDescription));
  }

  boolean resolves(String fieldName) {
    return resolversByField.containsKey(fieldName);
  }

  /** {@code id} must already be a valid UUID string, as every resolvable field's value is. */
  Optional<String> labelFor(String fieldName, String id) {
    Function<UUID, Optional<String>> resolver = resolversByField.get(fieldName);
    if (resolver == null) {
      return Optional.empty();
    }
    return resolver.apply(UUID.fromString(id));
  }
}
