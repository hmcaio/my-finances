package com.chm.myfinances.application.allocationplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanRepository;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link AllocationPlanService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context (F026 spec). Covers plan.md's explicit
 * test-first items: creates a new version going forward, same-month replace, rejects a sum != 100,
 * rejects a non-FII product, rejects an unknown product, rejects a duplicate product within one
 * call; {@code getCurrent} resolves correctly; creates the implicit {@code AllocationPlan} row on
 * first use.
 */
class AllocationPlanServiceTest {

  private final FakeAllocationPlanRepository planRepository = new FakeAllocationPlanRepository();
  private final FakeAllocationPlanVersionRepository versionRepository =
      new FakeAllocationPlanVersionRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final AllocationPlanService service =
      new AllocationPlanService(
          planRepository,
          versionRepository,
          productRepository,
          subcategoryRepository,
          idGenerator,
          new AuditRecorder(auditLog));

  private UUID fiiId;
  private UUID knri11Id;
  private UUID hglg11Id;
  private UUID nonFiiProductId;

  @BeforeEach
  void setUp() {
    UUID variableIncomeId = UUID.randomUUID();
    fiiId =
        subcategoryRepository
            .save(
                InvestmentSubcategory.create(
                    UUID.randomUUID(),
                    variableIncomeId,
                    AllocationPlanService.FII_SUBCATEGORY_NAME))
            .getId();
    UUID otherSubcategoryId =
        subcategoryRepository
            .save(InvestmentSubcategory.create(UUID.randomUUID(), variableIncomeId, "Stocks Test"))
            .getId();

    knri11Id =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), variableIncomeId, fiiId, "KNRI11 Test", null))
            .getId();
    hglg11Id =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), variableIncomeId, fiiId, "HGLG11 Test", null))
            .getId();
    nonFiiProductId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), variableIncomeId, otherSubcategoryId, "PETR4 Test", null))
            .getId();
  }

  private AllocationPlanEntry entry(UUID productId, String percentage) {
    return new AllocationPlanEntry(productId, new BigDecimal(percentage));
  }

  @Test
  void setAllocationCreatesTheImplicitPlanRowOnFirstUse() {
    assertThat(planRepository.findFirst()).isEmpty();

    service.setAllocation(List.of(entry(knri11Id, "100.00")), YearMonth.of(2026, 3));

    assertThat(planRepository.findFirst()).isPresent();
  }

  @Test
  void setAllocationCreatesANewVersionGoingForward() {
    service.setAllocation(List.of(entry(knri11Id, "100.00")), YearMonth.of(2026, 1));
    service.setAllocation(List.of(entry(hglg11Id, "100.00")), YearMonth.of(2026, 6));

    assertThat(service.findVersions()).hasSize(2);
  }

  @Test
  void setAllocationForTheSameMonthTwiceReplacesTheSameVersion() {
    AllocationPlanVersion first =
        service.setAllocation(List.of(entry(knri11Id, "100.00")), YearMonth.of(2026, 3));

    AllocationPlanVersion second =
        service.setAllocation(
            List.of(entry(knri11Id, "60.00"), entry(hglg11Id, "40.00")), YearMonth.of(2026, 3));

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(service.findVersions()).hasSize(1);
    assertThat(second.getEntries()).hasSize(2);
  }

  @Test
  void setAllocationForAFutureMonthLeavesTheCurrentMonthUnaffected() {
    service.setAllocation(List.of(entry(knri11Id, "100.00")), YearMonth.of(2026, 1));

    service.setAllocation(List.of(entry(hglg11Id, "100.00")), YearMonth.of(2026, 8));

    Optional<AllocationPlanVersion> currentInMarch = service.getCurrent(YearMonth.of(2026, 3));
    assertThat(currentInMarch).isPresent();
    assertThat(currentInMarch.get().getEntries().get(0).investmentProductId()).isEqualTo(knri11Id);
  }

  @Test
  void setAllocationRejectsASumOtherThan100() {
    assertThatThrownBy(
            () ->
                service.setAllocation(
                    List.of(entry(knri11Id, "60.00"), entry(hglg11Id, "39.00")), YearMonth.now()))
        .isInstanceOf(AllocationPlanSumInvalidException.class);
    assertThat(service.findVersions()).isEmpty();
  }

  @Test
  void setAllocationRejectsANonFiiProduct() {
    assertThatThrownBy(
            () -> service.setAllocation(List.of(entry(nonFiiProductId, "100.00")), YearMonth.now()))
        .isInstanceOf(AllocationPlanEntryNotFiiException.class);
  }

  @Test
  void setAllocationRejectsAnUnknownProduct() {
    assertThatThrownBy(
            () ->
                service.setAllocation(List.of(entry(UUID.randomUUID(), "100.00")), YearMonth.now()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void setAllocationRejectsADuplicateProductWithinOneCall() {
    assertThatThrownBy(
            () ->
                service.setAllocation(
                    List.of(entry(knri11Id, "60.00"), entry(knri11Id, "40.00")), YearMonth.now()))
        .isInstanceOf(AllocationPlanDuplicateProductException.class);
  }

  @Test
  void getCurrentResolvesTheLatestVersionAtOrBeforeTheTargetMonth() {
    service.setAllocation(List.of(entry(knri11Id, "100.00")), YearMonth.of(2026, 1));
    service.setAllocation(List.of(entry(hglg11Id, "100.00")), YearMonth.of(2026, 6));

    Optional<AllocationPlanVersion> resolved = service.getCurrent(YearMonth.of(2026, 4));

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getEntries().get(0).investmentProductId()).isEqualTo(knri11Id);
  }

  @Test
  void getCurrentReturnsEmptyWhenNoAllocationHasEverBeenSet() {
    assertThat(service.getCurrent(YearMonth.now())).isEmpty();
  }

  @Test
  void findVersionsReturnsEmptyWhenNoAllocationHasEverBeenSet() {
    assertThat(service.findVersions()).isEmpty();
  }
}
