package com.chm.myfinances.infrastructure.web.investmentreport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link DividendController}, against a real Testcontainers
 * Postgres (ADR 0010), hand-built {@link MockMvc} (F026 spec). Registers a dividend through the
 * real {@code POST /api/transactions} endpoint (the dedicated "Register Dividend" form is a
 * frontend-only concept, F026 spec), then checks it shows up here grouped by ticker/month.
 */
@WebIntegrationTest
class DividendControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  private UUID dividendCategoryId() {
    return categoryRepository.findAll().stream()
        .filter(Category::isDividendCategory)
        .map(Category::getId)
        .findFirst()
        .orElseThrow();
  }

  private UUID registerDividend(UUID holdingId, String amount, String date) throws Exception {
    Account account =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "Dividend Controller Test " + UUID.randomUUID(),
            AccountType.CHECKING);
    PaymentMethod paymentMethod =
        TestFixtures.paymentMethod(
            paymentMethodRepository, "Dividend Controller Test " + UUID.randomUUID());
    Map<String, Object> body =
        Map.of(
            "date", date,
            "amount", amount,
            "categoryId", dividendCategoryId().toString(),
            "accountId", account.getId().toString(),
            "paymentMethodId", paymentMethod.getId().toString(),
            "description", "Dividend",
            "investmentHoldingId", holdingId.toString());
    var result =
        mockMvc
            .perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonSupport.idOf(result));
  }

  private UUID fiiHolding(String ticker) {
    UUID categoryId =
        investmentCategoryRepository
            .save(
                InvestmentCategory.create(
                    UUID.randomUUID(), "Variable Income Dividend Test " + UUID.randomUUID()))
            .getId();
    InvestmentProduct product =
        productRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(),
                categoryId,
                null,
                ticker + " Fund Dividend Test",
                null,
                ticker,
                null));
    Account broker =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            ticker + " Broker Dividend Test",
            AccountType.INVESTMENT);
    return holdingRepository
        .save(InvestmentHolding.create(UUID.randomUUID(), product.getId(), broker.getId(), null))
        .getId();
  }

  @Test
  void dividendsListsRegisteredDividends() throws Exception {
    UUID holdingId = fiiHolding("KNRI11");
    registerDividend(holdingId, "50.00", "2026-01-15");

    mockMvc
        .perform(get("/api/fii/dividends"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.ticker=='KNRI11')]").exists());
  }

  @Test
  void totalsByTickerReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/dividends/totals").param("groupBy", "TICKER"))
        .andExpect(status().isOk());
  }

  @Test
  void totalsByMonthReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/dividends/totals").param("groupBy", "MONTH"))
        .andExpect(status().isOk());
  }

  @Test
  void aDividendCategoryTransactionWithoutAHoldingIdIsRejectedWith400() throws Exception {
    Account account =
        TestFixtures.account(
            accountRepository,
            institutionRepository,
            "No Holding Dividend Test",
            AccountType.CHECKING);
    PaymentMethod paymentMethod =
        TestFixtures.paymentMethod(paymentMethodRepository, "No Holding Dividend Test");
    Map<String, Object> body =
        Map.of(
            "date",
            "2026-01-15",
            "amount",
            "50.00",
            "categoryId",
            dividendCategoryId().toString(),
            "accountId",
            account.getId().toString(),
            "paymentMethodId",
            paymentMethod.getId().toString(),
            "description",
            "Dividend");

    mockMvc
        .perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }
}
