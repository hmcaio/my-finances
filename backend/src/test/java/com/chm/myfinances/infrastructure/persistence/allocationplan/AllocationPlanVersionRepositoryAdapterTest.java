package com.chm.myfinances.infrastructure.persistence.allocationplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.allocationplan.AllocationPlan;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanRepository;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link AllocationPlanVersionRepositoryAdapter} against a
 * real Testcontainers Postgres (ADR 0010, F026): round trips including the entries child table, and
 * that {@code save} wholesale-replaces a version's entries rather than accumulating rows.
 */
@DatabaseIntegrationTest
class AllocationPlanVersionRepositoryAdapterTest {

  @Autowired private AllocationPlanRepository planRepository;
  @Autowired private AllocationPlanVersionRepository versionRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;

  private UUID planId;
  private UUID productAId;
  private UUID productBId;

  @BeforeEach
  void setUp() {
    planId = planRepository.save(AllocationPlan.create(UUID.randomUUID())).getId();
    UUID categoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Variable Income Plan Repo Test"))
            .getId();
    productAId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), categoryId, null, "KNRI11 Repo Test", null))
            .getId();
    productBId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), categoryId, null, "HGLG11 Repo Test", null))
            .getId();
  }

  @Test
  void savesAndReloadsAVersionWithItsEntries() {
    AllocationPlanVersion version =
        AllocationPlanVersion.create(
            UUID.randomUUID(),
            planId,
            List.of(
                new AllocationPlanEntry(productAId, new BigDecimal("60.00")),
                new AllocationPlanEntry(productBId, new BigDecimal("40.00"))),
            YearMonth.of(2026, 3));

    versionRepository.save(version);

    List<AllocationPlanVersion> reloaded = versionRepository.findByPlanId(planId);
    assertThat(reloaded).hasSize(1);
    assertThat(reloaded.get(0).getEntries()).hasSize(2);
    assertThat(reloaded.get(0).getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 3));
  }

  @Test
  void savingAgainReplacesTheEntriesWholesaleRatherThanAccumulating() {
    AllocationPlanVersion version =
        AllocationPlanVersion.create(
            UUID.randomUUID(),
            planId,
            List.of(new AllocationPlanEntry(productAId, new BigDecimal("100.00"))),
            YearMonth.of(2026, 3));
    versionRepository.save(version);

    version.updateEntries(
        List.of(
            new AllocationPlanEntry(productAId, new BigDecimal("60.00")),
            new AllocationPlanEntry(productBId, new BigDecimal("40.00"))));
    versionRepository.save(version);

    AllocationPlanVersion reloaded =
        versionRepository.findByPlanIdAndEffectiveFrom(planId, YearMonth.of(2026, 3)).orElseThrow();
    assertThat(reloaded.getEntries()).hasSize(2);
  }

  @Test
  void findByPlanIdAndEffectiveFromFindsTheExactMonthOnly() {
    versionRepository.save(
        AllocationPlanVersion.create(
            UUID.randomUUID(),
            planId,
            List.of(new AllocationPlanEntry(productAId, new BigDecimal("100.00"))),
            YearMonth.of(2026, 1)));

    Optional<AllocationPlanVersion> found =
        versionRepository.findByPlanIdAndEffectiveFrom(planId, YearMonth.of(2026, 1));
    Optional<AllocationPlanVersion> notFound =
        versionRepository.findByPlanIdAndEffectiveFrom(planId, YearMonth.of(2026, 2));

    assertThat(found).isPresent();
    assertThat(notFound).isEmpty();
  }
}
