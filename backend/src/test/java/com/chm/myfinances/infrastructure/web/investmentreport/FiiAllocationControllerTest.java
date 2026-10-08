package com.chm.myfinances.infrastructure.web.investmentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link FiiAllocationController}, against a real Testcontainers
 * Postgres (ADR 0010), hand-built {@link MockMvc} (F026 spec). The underlying computation is
 * covered thoroughly by {@code FiiAllocationQueryTest}; this just proves the four basis/groupBy
 * combinations are wired up and return 200, plus (Addendum - Nested Allocation Charts) that {@code
 * segmentId} is actually serialized on the response.
 */
@WebIntegrationTest
class FiiAllocationControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentSegmentRepository segmentRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentSnapshotRepository snapshotRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;

  private MockMvc mockMvc;
  private UUID fiiSubcategoryId;
  private UUID variableIncomeId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    InvestmentSubcategory fii =
        subcategoryRepository.findAll().stream()
            .filter(s -> AllocationPlanService.FII_SUBCATEGORY_NAME.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    fiiSubcategoryId = fii.getId();
    variableIncomeId = fii.getInvestmentCategoryId();
  }

  @Test
  void actualByTickerReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "ACTUAL").param("groupBy", "TICKER"))
        .andExpect(status().isOk());
  }

  @Test
  void actualBySegmentReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "ACTUAL").param("groupBy", "SEGMENT"))
        .andExpect(status().isOk());
  }

  @Test
  void plannedByTickerReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "PLANNED").param("groupBy", "TICKER"))
        .andExpect(status().isOk());
  }

  @Test
  void plannedBySegmentReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "PLANNED").param("groupBy", "SEGMENT"))
        .andExpect(status().isOk());
  }

  @Test
  void actualByTickerRowSerializesItsOwnSegmentId() throws Exception {
    UUID segmentId =
        segmentRepository
            .save(InvestmentSegment.create(UUID.randomUUID(), "Allocation Controller Test Segment"))
            .getId();
    InvestmentProduct fii =
        productRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(),
                variableIncomeId,
                fiiSubcategoryId,
                "KNRI11 Allocation Controller Test",
                null,
                "KNRI11",
                segmentId));
    Account account =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "KNRI11 Allocation Controller Test Account",
            AccountType.INVESTMENT);
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHolding.create(UUID.randomUUID(), fii.getId(), account.getId(), null));
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), LocalDate.now(), new BigDecimal("500.00")));

    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "ACTUAL").param("groupBy", "TICKER"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.key=='" + fii.getId() + "')].segmentId").value(segmentId.toString()));

    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "ACTUAL").param("groupBy", "SEGMENT"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.key=='" + segmentId + "')].segmentId")
                .value(Matchers.contains(Matchers.nullValue())));
  }

  @Test
  void missingBasisReturns400() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("groupBy", "TICKER"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aPastMonthReturns200() throws Exception {
    mockMvc
        .perform(
            get("/api/fii/allocation")
                .param("basis", "PLANNED")
                .param("groupBy", "TICKER")
                .param("month", YearMonth.now().minusMonths(6).toString()))
        .andExpect(status().isOk());
  }

  @Test
  void theCurrentMonthMatchesTheOmittedMonthDefault() throws Exception {
    String withMonth =
        mockMvc
            .perform(
                get("/api/fii/allocation")
                    .param("basis", "ACTUAL")
                    .param("groupBy", "TICKER")
                    .param("month", YearMonth.now().toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String withoutMonth =
        mockMvc
            .perform(get("/api/fii/allocation").param("basis", "ACTUAL").param("groupBy", "TICKER"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(withMonth).isEqualTo(withoutMonth);
  }
}
