package com.chm.myfinances.infrastructure.web.investmentreport;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer tests for {@link InvestmentAllocationController} and {@link
 * InvestmentValueSeriesController} (F009), against a real Testcontainers Postgres (ADR 0010). The
 * shared database also holds the seeded investment taxonomy and rows other tests commit, so every
 * assertion here looks at this test's own uniquely named categories/products only.
 */
@WebIntegrationTest
class InvestmentReportControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;

  private MockMvc mockMvc;
  private UUID checkingId;
  private UUID brokerId;
  private UUID reportCategoryId;
  private UUID reportSubcategoryId;
  private UUID cdbProductId;
  private UUID cdbHoldingId;
  private UUID bareProductId;
  private UUID bareHoldingId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    checkingId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Checking Report Test",
                AccountType.CHECKING)
            .getId();
    brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Report Test",
                AccountType.INVESTMENT)
            .getId();
    reportCategoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Alloc Category Report Test"))
            .getId();
    reportSubcategoryId =
        subcategoryRepository
            .save(
                InvestmentSubcategory.create(
                    UUID.randomUUID(), reportCategoryId, "Alloc Sub Report Test"))
            .getId();
    cdbProductId = product("CDB Report Test", reportSubcategoryId);
    cdbHoldingId = holdingOf(cdbProductId);
    bareProductId = product("Bare Report Test", null);
    bareHoldingId = holdingOf(bareProductId);
  }

  private UUID product(String name, UUID subcategoryId) {
    return productRepository
        .save(
            InvestmentProduct.create(
                UUID.randomUUID(), reportCategoryId, subcategoryId, name, null))
        .getId();
  }

  private UUID holdingOf(UUID productId) {
    return holdingRepository
        .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
        .getId();
  }

  private void snapshot(UUID holdingId, String date, String balance) throws Exception {
    mockMvc
        .perform(
            post("/api/investment-holdings/" + holdingId + "/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonSupport.toJson(Map.of("date", date, "balance", balance))))
        .andExpect(status().is2xxSuccessful());
  }

  /**
   * A single-line trade confirmation (F027, ADR 0024): direction is derived from which of {@code
   * from}/{@code to} is the (only) {@code INVESTMENT} account in this test, {@code brokerId}.
   * {@code quantity} defaults to {@code "1"} when {@code null} is passed (every line's quantity is
   * now mandatory, unlike the old all-optional {@code InvestmentTradeDetails}) - callers that used
   * to pass {@code null} only cared that cash moved, not about units.
   */
  private void trade(
      UUID from, UUID to, UUID productId, String date, String amount, String quantity)
      throws Exception {
    boolean buy = to.equals(brokerId);
    UUID cashAccountId = buy ? from : to;
    UUID investmentAccountId = buy ? to : from;
    String qty = quantity == null ? "1" : quantity;
    BigDecimal unitPrice =
        new BigDecimal(amount).divide(new BigDecimal(qty), 8, RoundingMode.HALF_UP);

    Map<String, Object> line = new LinkedHashMap<>();
    line.put("productId", productId.toString());
    line.put("side", buy ? "BUY" : "SELL");
    line.put("quantity", qty);
    line.put("unitPrice", unitPrice.toPlainString());
    Map<String, Object> confirmation = new LinkedHashMap<>();
    confirmation.put("taxes", "0.00");
    confirmation.put("lines", List.of(line));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date);
    body.put("description", "Trade");
    body.put("cashAccountId", cashAccountId.toString());
    body.put("investmentAccountId", investmentAccountId.toString());
    body.put("tradeConfirmation", confirmation);

    mockMvc
        .perform(
            post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonSupport.toJson(body)))
        .andExpect(status().isCreated());
  }

  private String categoryRowPath(String field) {
    return "$[?(@.categoryId == '" + reportCategoryId + "')]." + field;
  }

  @Test
  void allocationByCategoryDefaultsToCategoryAndSumsTheLatestSnapshots() throws Exception {
    snapshot(cdbHoldingId, "2026-01-31", "100.00");
    snapshot(cdbHoldingId, "2026-02-28", "150.00");
    snapshot(bareHoldingId, "2026-02-28", "25.50");

    mockMvc
        .perform(get("/api/investments/allocation").param("asOf", "2026-03-31"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(categoryRowPath("categoryName")).value(hasItem("Alloc Category Report Test")))
        .andExpect(jsonPath(categoryRowPath("totalValue")).value(hasItem(175.50)))
        .andExpect(jsonPath(categoryRowPath("needsSnapshot")).value(hasItem(false)))
        .andExpect(jsonPath(categoryRowPath("subcategoryId")).value(contains((Object) null)));
  }

  @Test
  void allocationBySubcategoryHasANullSliceForProductsWithoutOne() throws Exception {
    snapshot(cdbHoldingId, "2026-02-28", "150.00");
    snapshot(bareHoldingId, "2026-02-28", "25.50");

    mockMvc
        .perform(
            get("/api/investments/allocation")
                .param("asOf", "2026-03-31")
                .param("groupBy", "SUBCATEGORY"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(categoryRowPath("subcategoryName"))
                .value(contains("Alloc Sub Report Test", null)))
        .andExpect(jsonPath(categoryRowPath("totalValue")).value(contains(150.00, 25.50)));
  }

  @Test
  void allocationRespectsAsOfAndFlagsAStaleProduct() throws Exception {
    snapshot(cdbHoldingId, "2026-02-28", "150.00");
    trade(checkingId, brokerId, cdbProductId, "2026-03-10", "10.00", null);

    mockMvc
        .perform(get("/api/investments/allocation").param("asOf", "2026-03-31"))
        .andExpect(jsonPath(categoryRowPath("needsSnapshot")).value(hasItem(true)));
    mockMvc
        .perform(get("/api/investments/allocation").param("asOf", "2026-03-01"))
        .andExpect(jsonPath(categoryRowPath("needsSnapshot")).value(hasItem(false)));
    mockMvc
        .perform(get("/api/investments/allocation").param("asOf", "2026-01-31"))
        .andExpect(jsonPath(categoryRowPath("totalValue")).isEmpty());
  }

  @Test
  void allocationByAccountSumsHoldingsIntoTheirAccountWithNoCategoryFields() throws Exception {
    snapshot(cdbHoldingId, "2026-02-28", "150.00");
    snapshot(bareHoldingId, "2026-02-28", "25.50");

    mockMvc
        .perform(
            get("/api/investments/allocation")
                .param("asOf", "2026-03-31")
                .param("groupBy", "ACCOUNT"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.accountId == '" + brokerId + "')].accountName")
                .value(hasItem("Broker Report Test")))
        .andExpect(
            jsonPath("$[?(@.accountId == '" + brokerId + "')].totalValue").value(hasItem(175.50)))
        .andExpect(
            jsonPath("$[?(@.accountId == '" + brokerId + "')].categoryId")
                .value(contains((Object) null)))
        .andExpect(
            jsonPath("$[?(@.accountId == '" + brokerId + "')].categoryName")
                .value(contains((Object) null)));
  }

  @Test
  void allocationRejectsAnUnknownGroupByOrBadDateWith400() throws Exception {
    mockMvc
        .perform(get("/api/investments/allocation").param("groupBy", "INSTITUTION"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/investments/allocation").param("asOf", "not-a-date"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void valueSeriesReturnsMonthEndValuesContributionsAndUnitsForAProduct() throws Exception {
    snapshot(cdbHoldingId, "2026-01-31", "1000.00");
    snapshot(cdbHoldingId, "2026-02-28", "1500.00");
    trade(checkingId, brokerId, cdbProductId, "2026-01-10", "1000.00", "10");
    trade(checkingId, brokerId, cdbProductId, "2026-02-10", "400.00", "4");
    trade(brokerId, checkingId, cdbProductId, "2026-02-20", "150.00", "1.5");

    mockMvc
        .perform(
            get("/api/investments/value-series")
                .param("from", "2025-12")
                .param("to", "2026-02")
                .param("productId", cdbProductId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].productId").value(cdbProductId.toString()))
        .andExpect(jsonPath("$[0].points.length()").value(3))
        .andExpect(jsonPath("$[0].points[0].month").value("2025-12"))
        .andExpect(jsonPath("$[0].points[0].value").doesNotExist())
        .andExpect(jsonPath("$[0].points[0].contributed").value(0))
        .andExpect(jsonPath("$[0].points[0].units").value(0))
        .andExpect(jsonPath("$[0].points[1].month").value("2026-01"))
        .andExpect(jsonPath("$[0].points[1].value").value(1000.00))
        .andExpect(jsonPath("$[0].points[1].contributed").value(1000.00))
        .andExpect(jsonPath("$[0].points[1].units").value(10))
        .andExpect(jsonPath("$[0].points[2].value").value(1500.00))
        .andExpect(jsonPath("$[0].points[2].contributed").value(250.00))
        .andExpect(jsonPath("$[0].points[2].units").value(12.5));
  }

  @Test
  void valueSeriesUnitsAreComputedFromTheLineSEvenWithoutAnExplicitQuantityArgument()
      throws Exception {
    // F027 (ADR 0024): every line's quantity is now mandatory, unlike the old all-optional
    // InvestmentTradeDetails - a product with any trade at all now always has units, never null.
    trade(checkingId, brokerId, bareProductId, "2026-02-10", "400.00", null);

    mockMvc
        .perform(
            get("/api/investments/value-series")
                .param("from", "2026-02")
                .param("to", "2026-02")
                .param("productId", bareProductId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].points[0].contributed").value(400.00))
        .andExpect(jsonPath("$[0].points[0].units").value(1));
  }

  @Test
  void valueSeriesWithoutAProductIdReturnsOneSeriesPerProduct() throws Exception {
    mockMvc
        .perform(
            get("/api/investments/value-series").param("from", "2026-02").param("to", "2026-03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].productId").value(hasItem(cdbProductId.toString())))
        .andExpect(jsonPath("$[*].productId").value(hasItem(bareProductId.toString())));
  }

  @Test
  void valueSeriesDefaultsToTheLastTwelveMonths() throws Exception {
    mockMvc
        .perform(get("/api/investments/value-series").param("productId", cdbProductId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].points.length()").value(12));
  }

  @Test
  void valueSeriesRejectsAReversedRangeBadMonthAndUnknownProduct() throws Exception {
    mockMvc
        .perform(
            get("/api/investments/value-series").param("from", "2026-03").param("to", "2026-01"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/investments/value-series").param("from", "2026-13"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/investments/value-series").param("productId", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound());
  }
}
