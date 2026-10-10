package com.chm.myfinances.application.auditlog;

import static com.chm.myfinances.testsupport.AuditSnapshotCompleteness.declaredFieldNames;
import static com.chm.myfinances.testsupport.AuditSnapshotCompleteness.technicalFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.infrastructure.persistence.account.AccountJpaEntity;
import com.chm.myfinances.infrastructure.persistence.allocationplan.AllocationPlanEntryJpaEntity;
import com.chm.myfinances.infrastructure.persistence.allocationplan.AllocationPlanVersionJpaEntity;
import com.chm.myfinances.infrastructure.persistence.budget.BudgetVersionJpaEntity;
import com.chm.myfinances.infrastructure.persistence.category.CategoryJpaEntity;
import com.chm.myfinances.infrastructure.persistence.institution.InstitutionJpaEntity;
import com.chm.myfinances.infrastructure.persistence.investmentcategory.InvestmentCategoryJpaEntity;
import com.chm.myfinances.infrastructure.persistence.investmentholding.InvestmentHoldingJpaEntity;
import com.chm.myfinances.infrastructure.persistence.investmentproduct.InvestmentProductJpaEntity;
import com.chm.myfinances.infrastructure.persistence.investmentsegment.InvestmentSegmentJpaEntity;
import com.chm.myfinances.infrastructure.persistence.investmentsnapshot.InvestmentSnapshotJpaEntity;
import com.chm.myfinances.infrastructure.persistence.investmentsubcategory.InvestmentSubcategoryJpaEntity;
import com.chm.myfinances.infrastructure.persistence.paymentmethod.PaymentMethodJpaEntity;
import com.chm.myfinances.infrastructure.persistence.recurringtemplate.RecurringTemplateJpaEntity;
import com.chm.myfinances.infrastructure.persistence.recurringtemplate.RecurringTemplateVersionJpaEntity;
import com.chm.myfinances.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.chm.myfinances.infrastructure.persistence.transfer.TransferJpaEntity;
import com.chm.myfinances.infrastructure.persistence.transfer.TransferTradeLineJpaEntity;
import com.chm.myfinances.infrastructure.persistence.vehicle.VehicleJpaEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Guards that {@code toAuditSnapshot()} covers every persisted field, per aggregate, with an
 * explicit exclude list (F025 spec, ADR 0022: "a completeness test with an explicit exclude list
 * catches silent gaps" - a field added later, like F024's fuel columns on {@code Transaction},
 * can't be silently unaudited). PR1 covers the three aggregates it instruments; PR2 extends this to
 * every other aggregate.
 */
class AuditSnapshotCompletenessTest {

  @Test
  void transactionSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(TransactionJpaEntity.class);
    persisted.removeAll(technicalFields());

    Transaction transaction =
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            BigDecimal.TEN,
            UUID.randomUUID(),
            CategoryType.EXPENSE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "desc",
            null);

    assertThat(transaction.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void accountSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(AccountJpaEntity.class);
    persisted.removeAll(technicalFields());

    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            UUID.randomUUID(),
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.of(2026, 1, 1));

    assertThat(account.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  /**
   * {@code Transfer}'s own columns, excluding the trade confirmation's lines (a separate child
   * table, checked below) - every other persisted field on {@code transfers} must be a top-level
   * snapshot key.
   */
  @Test
  void transferSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(TransferJpaEntity.class);
    persisted.removeAll(technicalFields());

    Transfer transfer =
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.TEN,
            "desc",
            null);

    assertThat(transfer.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  /**
   * {@code transfer_trade_lines}'s own columns, excluding {@code id} (technical) and {@code
   * transferId} (the FK back to the parent, already identified by the entry itself) - every other
   * field must appear in each line's flattened map under {@code tradeConfirmationLines}.
   */
  @Test
  void transferTradeLineSnapshotCoversEveryPersistedFieldExceptTechnicalAndParentFk() {
    Set<String> persisted = declaredFieldNames(TransferTradeLineJpaEntity.class);
    persisted.removeAll(technicalFields());
    persisted.remove("transferId");

    UUID productId = UUID.randomUUID();
    TradeConfirmation confirmation =
        TradeConfirmation.of(
            List.of(
                new TradeConfirmationLine(
                    productId, TradeSide.BUY, BigDecimal.TEN, BigDecimal.ONE, null, false)));
    Transfer transfer =
        Transfer.createTradeConfirmation(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "desc",
            null,
            BigDecimal.ONE,
            confirmation);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines =
        (List<Map<String, Object>>) transfer.toAuditSnapshot().get("tradeConfirmationLines");

    assertThat(lines).isNotEmpty();
    assertThat(lines.get(0).keySet()).containsAll(persisted);
  }

  @Test
  void categorySnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(CategoryJpaEntity.class);
    persisted.removeAll(technicalFields());

    Category category = Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE);

    assertThat(category.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void paymentMethodSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(PaymentMethodJpaEntity.class);
    persisted.removeAll(technicalFields());

    PaymentMethod paymentMethod = PaymentMethod.create(UUID.randomUUID(), "Debit Card");

    assertThat(paymentMethod.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void institutionSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(InstitutionJpaEntity.class);
    persisted.removeAll(technicalFields());

    Institution institution = Institution.create(UUID.randomUUID(), "Nubank");

    assertThat(institution.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void vehicleSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(VehicleJpaEntity.class);
    persisted.removeAll(technicalFields());

    Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "Civic");

    assertThat(vehicle.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void investmentSegmentSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(InvestmentSegmentJpaEntity.class);
    persisted.removeAll(technicalFields());

    InvestmentSegment segment = InvestmentSegment.create(UUID.randomUUID(), "Shoppings");

    assertThat(segment.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void investmentCategorySnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(InvestmentCategoryJpaEntity.class);
    persisted.removeAll(technicalFields());

    InvestmentCategory category = InvestmentCategory.create(UUID.randomUUID(), "Fixed Income");

    assertThat(category.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void investmentSubcategorySnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(InvestmentSubcategoryJpaEntity.class);
    persisted.removeAll(technicalFields());

    InvestmentSubcategory subcategory =
        InvestmentSubcategory.create(UUID.randomUUID(), UUID.randomUUID(), "CDB");

    assertThat(subcategory.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void investmentProductSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(InvestmentProductJpaEntity.class);
    persisted.removeAll(technicalFields());

    InvestmentProduct product =
        InvestmentProduct.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "KNRI11",
            null,
            "KNRI11",
            UUID.randomUUID());

    assertThat(product.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void investmentHoldingSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(InvestmentHoldingJpaEntity.class);
    persisted.removeAll(technicalFields());

    InvestmentHolding holding =
        InvestmentHolding.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);

    assertThat(holding.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void investmentSnapshotSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(InvestmentSnapshotJpaEntity.class);
    persisted.removeAll(technicalFields());

    InvestmentSnapshot snapshot =
        InvestmentSnapshot.create(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 1), BigDecimal.TEN);

    assertThat(snapshot.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void budgetVersionSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(BudgetVersionJpaEntity.class);
    persisted.removeAll(technicalFields());

    BudgetVersion version =
        BudgetVersion.create(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("500.00"), YearMonth.of(2026, 1));

    assertThat(version.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void recurringTemplateSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(RecurringTemplateJpaEntity.class);
    persisted.removeAll(technicalFields());

    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Rent");

    assertThat(template.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  @Test
  void recurringTemplateVersionSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(RecurringTemplateVersionJpaEntity.class);
    persisted.removeAll(technicalFields());

    RecurringTemplateVersion version =
        RecurringTemplateVersion.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("1500.00"),
            5,
            YearMonth.of(2026, 1));

    assertThat(version.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  /** {@code AllocationPlanVersion}'s own columns, excluding {@code entries} (checked below). */
  @Test
  void allocationPlanVersionSnapshotCoversEveryPersistedFieldExceptTechnicalOnes() {
    Set<String> persisted = declaredFieldNames(AllocationPlanVersionJpaEntity.class);
    persisted.removeAll(technicalFields());

    AllocationPlanVersion version =
        AllocationPlanVersion.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(new AllocationPlanEntry(UUID.randomUUID(), new BigDecimal("100"))),
            YearMonth.of(2026, 1));

    assertThat(version.toAuditSnapshot().keySet()).containsAll(persisted);
  }

  /**
   * {@code allocation_plan_entries}'s own columns, excluding {@code id} (technical) and {@code
   * versionId} (the FK back to the parent) - every other field must appear in each entry's
   * flattened map under {@code entries}.
   */
  @Test
  void allocationPlanEntrySnapshotCoversEveryPersistedFieldExceptTechnicalAndParentFk() {
    Set<String> persisted = declaredFieldNames(AllocationPlanEntryJpaEntity.class);
    persisted.removeAll(technicalFields());
    persisted.remove("versionId");

    AllocationPlanVersion version =
        AllocationPlanVersion.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(new AllocationPlanEntry(UUID.randomUUID(), new BigDecimal("100"))),
            YearMonth.of(2026, 1));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries =
        (List<Map<String, Object>>) version.toAuditSnapshot().get("entries");

    assertThat(entries).isNotEmpty();
    assertThat(entries.get(0).keySet()).containsAll(persisted);
  }
}
