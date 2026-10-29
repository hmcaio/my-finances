package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
import com.chm.myfinances.application.investmentreport.FiiAllocationQuery;
import com.chm.myfinances.application.investmentreport.FiiPortfolioQuery;
import com.chm.myfinances.application.investmentreport.InvestmentAllocationQuery;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanRepository;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInstitutionRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentOpeningPositionRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferTradeLineRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InvestmentAllocationReportGenerator} (F030 spec), written first (ADR 0004): two
 * independent sections - every holding by institution, and the FII-only actual-by-segment breakdown
 * - rendered as one CSV.
 */
class InvestmentAllocationReportGeneratorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeTransferTradeLineRepository tradeLineRepository =
      new FakeTransferTradeLineRepository();
  private final FakeInvestmentSegmentRepository segmentRepository =
      new FakeInvestmentSegmentRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeInstitutionRepository institutionRepository = new FakeInstitutionRepository();
  private final FakeInvestmentCategoryRepository categoryRepository =
      new FakeInvestmentCategoryRepository();
  private final FakeAllocationPlanRepository planRepository = new FakeAllocationPlanRepository();
  private final FakeAllocationPlanVersionRepository planVersionRepository =
      new FakeAllocationPlanVersionRepository();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final InvestmentSnapshotFreshnessQuery freshnessQuery =
      new InvestmentSnapshotFreshnessQuery(
          latestSnapshotQuery,
          tradeLineRepository,
          holdingRepository,
          new FakeInvestmentOpeningPositionRepository());
  private final InvestmentAllocationQuery investmentAllocationQuery =
      new InvestmentAllocationQuery(
          productRepository,
          holdingRepository,
          categoryRepository,
          subcategoryRepository,
          accountRepository,
          institutionRepository,
          latestSnapshotQuery,
          freshnessQuery);
  private final FiiPortfolioQuery portfolioQuery =
      new FiiPortfolioQuery(
          productRepository,
          subcategoryRepository,
          holdingRepository,
          tradeLineRepository,
          new FakeInvestmentOpeningPositionRepository(),
          latestSnapshotQuery,
          freshnessQuery,
          clock);
  private final AllocationPlanService allocationPlanService =
      new AllocationPlanService(
          planRepository,
          planVersionRepository,
          productRepository,
          subcategoryRepository,
          new FakeIdGenerator(),
          new AuditRecorder(new FakeAuditLog(), AuditReferenceLabels.none()));
  private final FiiAllocationQuery fiiAllocationQuery =
      new FiiAllocationQuery(
          portfolioQuery, allocationPlanService, productRepository, segmentRepository, clock);
  private final InvestmentAllocationReportGenerator generator =
      new InvestmentAllocationReportGenerator(investmentAllocationQuery, fiiAllocationQuery, clock);

  private final UUID fiiSubcategoryId =
      subcategoryRepository
          .save(
              InvestmentSubcategory.reconstitute(
                  UUID.randomUUID(), UUID.randomUUID(), AllocationPlanService.FII_SUBCATEGORY_NAME))
          .getId();

  @Test
  void includesEveryHoldingByInstitutionAndFiiOnlyBySegment() {
    UUID institutionId =
        institutionRepository.save(Institution.create(UUID.randomUUID(), "XP R Test")).getId();
    Account broker =
        accountRepository.save(
            AccountMother.investment()
                .withName("Broker R Test")
                .withInstitutionId(institutionId)
                .build());
    UUID segmentId =
        segmentRepository
            .save(InvestmentSegment.reconstitute(UUID.randomUUID(), "Shoppings R Test"))
            .getId();
    InvestmentProduct fii =
        productRepository.save(
            InvestmentProductMother.product()
                .withName("Kinea R Test")
                .withTicker("KNRI11")
                .withInvestmentSubcategoryId(fiiSubcategoryId)
                .withSegmentId(segmentId)
                .build());
    InvestmentProduct nonFii =
        productRepository.save(InvestmentProductMother.product().withName("CDB R Test").build());
    InvestmentHolding fiiHolding =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(fii.getId())
                .withAccountId(broker.getId())
                .build());
    InvestmentHolding cdbHolding =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(nonFii.getId())
                .withAccountId(broker.getId())
                .build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            fiiHolding.getId(),
            LocalDate.of(2026, 5, 31),
            new BigDecimal("1000.00")));
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            cdbHolding.getId(),
            LocalDate.of(2026, 5, 31),
            new BigDecimal("500.00")));

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(
                null,
                null,
                LocalDate.of(2026, 5, 31),
                null,
                null,
                null,
                YearMonth.of(2026, 5),
                null));

    List<List<Map<String, String>>> sections =
        CsvTestSupport.parseSections(files.get(0).content(), 2);
    assertThat(sections.get(0)).hasSize(1);
    assertThat(sections.get(0).get(0).get("institution_name")).isEqualTo("XP R Test");
    // Institution total includes both the FII and the non-FII holding.
    assertThat(sections.get(0).get(0).get("total_value")).isEqualTo("1500.00");

    assertThat(sections.get(1)).hasSize(1);
    assertThat(sections.get(1).get(0).get("segment_name")).isEqualTo("Shoppings R Test");
    // Segment total is FII-only (the 500.00 CDB holding is excluded).
    assertThat(sections.get(1).get(0).get("total_value")).isEqualTo("1000.00");
    assertThat(sections.get(1).get(0).get("percentage")).isEqualTo("100.00");
  }
}
