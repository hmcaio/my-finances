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
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link FiiPortfolioController}, against a real Testcontainers
 * Postgres (ADR 0010), hand-built {@link MockMvc} (F026 spec).
 */
@WebIntegrationTest
class FiiPortfolioControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private TransferRepository transferRepository;

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
  void portfolioListsOnlyFiiProducts() throws Exception {
    InvestmentProduct fii =
        productRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(),
                variableIncomeId,
                fiiSubcategoryId,
                "KNRI11 Portfolio Controller Test",
                null));
    productRepository.save(
        InvestmentProduct.create(
            UUID.randomUUID(), variableIncomeId, null, "PETR4 Portfolio Controller Test", null));

    mockMvc
        .perform(get("/api/fii/portfolio").param("status", "ALL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.productId=='" + fii.getId() + "')]").exists())
        .andExpect(jsonPath("$[?(@.name=='PETR4 Portfolio Controller Test')]").doesNotExist());
  }

  @Test
  void statusFilterDefaultsToOpen() throws Exception {
    InvestmentProduct fii =
        productRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(),
                variableIncomeId,
                fiiSubcategoryId,
                "HGLG11 Portfolio Controller Test",
                null));
    Account account =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "HGLG11 Portfolio Controller Test Account",
            AccountType.INVESTMENT);
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHolding.create(UUID.randomUUID(), fii.getId(), account.getId(), null));
    holding.close(java.time.LocalDate.now());
    holdingRepository.save(holding);

    mockMvc
        .perform(get("/api/fii/portfolio"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.productId=='" + fii.getId() + "')]").doesNotExist());
  }

  @Test
  void aPastMonthExcludesATradeMadeAfterThatMonthAndNeverFlagsNeedsSnapshot() throws Exception {
    InvestmentProduct fii =
        productRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(),
                variableIncomeId,
                fiiSubcategoryId,
                "XPLG11 Portfolio Controller Test",
                null));
    Account investmentAccount =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "XPLG11 Portfolio Controller Test Account",
            AccountType.INVESTMENT);
    Account checkingAccount =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "XPLG11 Portfolio Controller Test Checking",
            AccountType.CHECKING);
    holdingRepository.save(
        InvestmentHolding.create(UUID.randomUUID(), fii.getId(), investmentAccount.getId(), null));
    // Today's trade: must not show up when asking for last month, and must not flag
    // needsSnapshot (that flag is suppressed entirely for a past month, never computed).
    transferRepository.save(
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.now(),
            checkingAccount.getId(),
            investmentAccount.getId(),
            new BigDecimal("100.00"),
            "XPLG11 buy",
            null,
            fii.getId(),
            new InvestmentTradeDetails(new BigDecimal("1"), BigDecimal.ONE, null)));

    YearMonth lastMonth = YearMonth.now().minusMonths(1);
    mockMvc
        .perform(
            get("/api/fii/portfolio").param("status", "ALL").param("month", lastMonth.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.productId=='" + fii.getId() + "')].cotasHeld").value(0))
        .andExpect(jsonPath("$[?(@.productId=='" + fii.getId() + "')].needsSnapshot").value(false));
  }

  @Test
  void theCurrentMonthMatchesTheOmittedMonthDefault() throws Exception {
    productRepository.save(
        InvestmentProduct.create(
            UUID.randomUUID(),
            variableIncomeId,
            fiiSubcategoryId,
            "VISC11 Portfolio Controller Test",
            null));

    YearMonth currentMonth = YearMonth.now();
    String withMonth =
        mockMvc
            .perform(
                get("/api/fii/portfolio")
                    .param("status", "ALL")
                    .param("month", currentMonth.toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String withoutMonth =
        mockMvc
            .perform(get("/api/fii/portfolio").param("status", "ALL"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(withMonth).isEqualTo(withoutMonth);
  }
}
