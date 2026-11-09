package com.chm.myfinances.infrastructure.web.goal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.testsupport.MutableClock;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link GoalReportController} and the product-side read of {@link
 * GoalAllocationController} (F039, ADR 0034), against a real Testcontainers Postgres (ADR 0010).
 * The projection maths and the queries' rules are the unit tests' job; this adds the HTTP shape,
 * the problem types and wiring to the real database (snapshots, expenses, transfers). Today is
 * pinned to 2026-11-05 in the pinned non-UTC zone, since the figures depend on the month.
 */
@WebIntegrationTest
@Import(GoalReportControllerTest.FixedClockTestConfig.class)
class GoalReportControllerTest {

  private static final String TYPE_PREFIX = "urn:my-finances:error:";
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  /** Overrides the app's {@code Clock} with a {@link MutableClock}, pinned in {@link #setUp()}. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class FixedClockTestConfig {
    @Bean
    @Primary
    public MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private MutableClock testClock;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentSnapshotRepository snapshotRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private TransferRepository transferRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    testClock.set(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
  }

  private String createGoal(
      String name, LocalDate targetDate, String target, String contribution, boolean show)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("targetAmount", target);
    if (targetDate != null) {
      body.put("targetDate", targetDate.toString());
    }
    body.put("plannedMonthlyContribution", contribution);
    body.put("returnPessimistic", "0");
    body.put("returnBase", "0");
    body.put("returnOptimistic", "0");
    body.put("showOnDashboard", show);
    return JsonSupport.idOf(
        mockMvc
            .perform(
                post("/api/goals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JsonSupport.toJson(body)))
            .andExpect(status().isCreated())
            .andReturn());
  }

  /** A product held in a new INVESTMENT account, last valued at {@code balance} on {@code date}. */
  private UUID productWorth(String name, LocalDate date, String balance) {
    UUID categoryId =
        TaxonomySeeder.investmentCategory(jdbcTemplate, "Category " + name + " Goal Report")
            .getId();
    UUID productId =
        productRepository
            .save(InvestmentProduct.create(UUID.randomUUID(), categoryId, null, name, null))
            .getId();
    UUID brokerId =
        TestFixtures.account(
                accountRepository, institutionRepository, "Broker " + name, AccountType.INVESTMENT)
            .getId();
    UUID holdingId =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
            .getId();
    snapshotRepository.save(
        InvestmentSnapshot.create(UUID.randomUUID(), holdingId, date, new BigDecimal(balance)));
    return productId;
  }

  /** Another snapshot for the product's (single) holding. */
  private void snapshotOf(UUID productId, LocalDate date, String balance) {
    UUID holdingId = holdingRepository.findByProductId(productId).get(0).getId();
    snapshotRepository.save(
        InvestmentSnapshot.create(UUID.randomUUID(), holdingId, date, new BigDecimal(balance)));
  }

  private void allocate(String goalId, UUID productId, String percentage) throws Exception {
    Map<String, Object> share = new LinkedHashMap<>();
    share.put("productId", productId.toString());
    share.put("percentage", percentage);
    mockMvc
        .perform(
            put("/api/goals/" + goalId + "/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonSupport.toJson(Map.of("allocations", List.of(share)))))
        .andExpect(status().isOk());
  }

  // --- projection ---

  @Test
  void aDatedGoalReturnsThreeScenariosWithTheShortfallAndRequiredContribution() throws Exception {
    String id = createGoal("Dated Goal Report", LocalDate.of(2027, 11, 15), "1000.00", "50", true);

    mockMvc
        .perform(get("/api/goals/" + id + "/projection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allocatedValue").value(0))
        .andExpect(jsonPath("$.latestSnapshotDate").doesNotExist())
        .andExpect(jsonPath("$.scenarios.length()").value(3))
        .andExpect(jsonPath("$.scenarios[0].scenario").value("PESSIMISTIC"))
        .andExpect(jsonPath("$.scenarios[1].scenario").value("BASE"))
        .andExpect(jsonPath("$.scenarios[2].scenario").value("OPTIMISTIC"))
        .andExpect(jsonPath("$.scenarios[1].points.length()").value(13))
        .andExpect(jsonPath("$.scenarios[1].points[0].month").value("2026-11"))
        .andExpect(jsonPath("$.scenarios[1].points[12].month").value("2027-11"))
        .andExpect(jsonPath("$.scenarios[1].projectedValueAtTargetDate").value(600.00))
        .andExpect(jsonPath("$.scenarios[1].shortfallAtTargetDate").value(400.00))
        .andExpect(jsonPath("$.scenarios[1].requiredMonthlyContribution").value(83.34))
        .andExpect(jsonPath("$.scenarios[1].monthsToTarget").doesNotExist());
  }

  @Test
  void aDatelessGoalReturnsMonthsToTargetInsteadOfTheDatedFigures() throws Exception {
    String id = createGoal("Dateless Goal Report", null, "1000.00", "100", true);

    mockMvc
        .perform(get("/api/goals/" + id + "/projection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenarios[1].monthsToTarget").value(10))
        .andExpect(jsonPath("$.scenarios[1].points.length()").value(11))
        .andExpect(jsonPath("$.scenarios[1].projectedValueAtTargetDate").doesNotExist())
        .andExpect(jsonPath("$.scenarios[1].requiredMonthlyContribution").doesNotExist());
  }

  @Test
  void theProjectionStartsFromTheAllocatedShareOfTheLatestSnapshot() throws Exception {
    String id = createGoal("Allocated Goal Report", LocalDate.of(2027, 11, 15), "5000", "0", true);
    allocate(id, productWorth("Fund Projection", LocalDate.of(2026, 10, 20), "2000.00"), "50");

    mockMvc
        .perform(get("/api/goals/" + id + "/projection"))
        .andExpect(jsonPath("$.allocatedValue").value(1000.00))
        .andExpect(jsonPath("$.latestSnapshotDate").value("2026-10-20"))
        .andExpect(jsonPath("$.scenarios[1].projectedValueAtTargetDate").value(1000.00));
  }

  @Test
  void aGoalWithEnoughHistoryReturnsThePaceBesideTheScenarios() throws Exception {
    String id = createGoal("Pace Goal Report", LocalDate.of(2027, 11, 15), "5000", "0", true);
    UUID product = productWorth("Fund Pace", LocalDate.of(2026, 8, 1), "1000.00");
    snapshotOf(product, LocalDate.of(2026, 9, 1), "1100.00");
    snapshotOf(product, LocalDate.of(2026, 10, 1), "1200.00");
    snapshotOf(product, LocalDate.of(2026, 11, 1), "1300.00");
    allocate(id, product, "100");

    mockMvc
        .perform(get("/api/goals/" + id + "/projection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenarios.length()").value(3))
        .andExpect(jsonPath("$.pace.monthlyPace").value(100.00))
        .andExpect(jsonPath("$.pace.points.length()").value(13))
        .andExpect(jsonPath("$.pace.points[0].month").value("2026-11"))
        .andExpect(jsonPath("$.pace.points[0].value").value(1300.00))
        .andExpect(jsonPath("$.pace.points[12].month").value("2027-11"))
        .andExpect(jsonPath("$.pace.projectedValueAtTargetDate").value(2500.00))
        .andExpect(jsonPath("$.pace.shortfallAtTargetDate").value(2500.00))
        .andExpect(jsonPath("$.pace.monthsToTarget").doesNotExist());
  }

  @Test
  void aGoalWithTooLittleHistoryHasNoPace() throws Exception {
    String id = createGoal("No Pace Goal Report", LocalDate.of(2027, 11, 15), "5000", "0", true);
    allocate(id, productWorth("Fund No Pace", LocalDate.of(2026, 10, 20), "1000.00"), "100");

    mockMvc
        .perform(get("/api/goals/" + id + "/projection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenarios.length()").value(3))
        .andExpect(jsonPath("$.pace").doesNotExist());
  }

  @Test
  void anUnknownGoalIs404OnProjectionAndHistory() throws Exception {
    mockMvc
        .perform(get("/api/goals/" + UUID.randomUUID() + "/projection"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "goal-not-found"));
    mockMvc
        .perform(get("/api/goals/" + UUID.randomUUID() + "/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "goal-not-found"));
  }

  // --- history ---

  @Test
  void historyAppliesTheCurrentSplitToTheProductSeries() throws Exception {
    String id = createGoal("History Goal Report", null, "5000", "0", true);
    allocate(id, productWorth("Fund History", LocalDate.of(2026, 10, 20), "1000.00"), "50");

    mockMvc
        .perform(
            get("/api/goals/" + id + "/history").param("from", "2026-09").param("to", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].month").value("2026-09"))
        .andExpect(jsonPath("$[0].value").value(0))
        .andExpect(jsonPath("$[1].month").value("2026-10"))
        .andExpect(jsonPath("$[1].value").value(500.00));
  }

  @Test
  void historyDefaultsToTheLastTwelveMonthsAndRejectsAReversedRange() throws Exception {
    String id = createGoal("History Default Goal Report", null, "5000", "0", true);

    mockMvc
        .perform(get("/api/goals/" + id + "/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(12))
        .andExpect(jsonPath("$[0].month").value("2025-12"))
        .andExpect(jsonPath("$[11].month").value("2026-11"));
    mockMvc
        .perform(
            get("/api/goals/" + id + "/history").param("from", "2026-10").param("to", "2026-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "invalid-value-series-range"));
  }

  // --- dashboard ---

  @Test
  void theDashboardListsOnlyFlaggedGoalsWithTheirBaseStatusInTheStandardOrder() throws Exception {
    String dated =
        createGoal("Dated Dash Goal Report", LocalDate.of(2027, 11, 15), "2000", "100", true);
    String dateless = createGoal("Dateless Dash Goal Report", null, "300", "100", true);
    String hidden = createGoal("Hidden Dash Goal Report", null, "300", "100", false);
    allocate(dated, productWorth("Fund Dash", LocalDate.of(2026, 10, 20), "1000.00"), "100");

    ResultActions result = mockMvc.perform(get("/api/goals/dashboard")).andExpect(status().isOk());

    result
        .andExpect(jsonPath("$[?(@.id == '" + hidden + "')]").doesNotExist())
        .andExpect(jsonPath("$[0].id").value(dated))
        .andExpect(jsonPath("$[0].allocatedValue").value(1000.00))
        .andExpect(jsonPath("$[0].progressPercent").value(50.00))
        .andExpect(jsonPath("$[0].targetReached").value(false))
        // 1000 now + 12 x 100 = 2200 against 2000: on track (negative shortfall).
        .andExpect(jsonPath("$[0].projectedShortfall").value(-200.00))
        .andExpect(jsonPath("$[0].monthsToTarget").doesNotExist())
        .andExpect(jsonPath("$[1].id").value(dateless))
        .andExpect(jsonPath("$[1].monthsToTarget").value(3))
        .andExpect(jsonPath("$[1].projectedShortfall").doesNotExist());
  }

  @Test
  void anArchivedGoalLeavesTheDashboardAndRestoringDoesNotBringItBack() throws Exception {
    String id =
        createGoal("Archive Dash Goal Report", LocalDate.of(2027, 11, 15), "2000", "100", true);
    Map<String, Object> archive = new LinkedHashMap<>();
    archive.put("name", "Archive Dash Goal Report");
    archive.put("targetAmount", "2000");
    archive.put("targetDate", "2027-11-15");
    archive.put("plannedMonthlyContribution", "100");
    archive.put("returnPessimistic", "0");
    archive.put("returnBase", "0");
    archive.put("returnOptimistic", "0");
    archive.put("status", "ARCHIVED");
    archive.put("showOnDashboard", false);

    mockMvc
        .perform(
            patch("/api/goals/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonSupport.toJson(archive)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/goals/dashboard"))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')]").doesNotExist());

    archive.put("status", "ACTIVE");
    mockMvc
        .perform(
            patch("/api/goals/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonSupport.toJson(archive)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showOnDashboard").value(false));
    mockMvc
        .perform(get("/api/goals/dashboard"))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')]").doesNotExist());
  }

  // --- emergency fund suggestion ---

  @Test
  void theSuggestionAveragesFullMonthExpensesAndIgnoresTransfers() throws Exception {
    UUID checking =
        TestFixtures.checkingAccount(
                accountRepository, institutionRepository, "Cash Suggestion Goal")
            .getId();
    UUID savings =
        TestFixtures.checkingAccount(
                accountRepository, institutionRepository, "Savings Suggestion Goal")
            .getId();
    UUID category =
        TestFixtures.category(categoryRepository, "Suggestion Goal Test", CategoryType.EXPENSE)
            .getId();
    UUID paymentMethod = TaxonomySeeder.paymentMethod(jdbcTemplate, "Suggestion Goal Test").getId();
    for (LocalDate date : List.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10))) {
      transactionRepository.save(
          Transaction.create(
              UUID.randomUUID(),
              date,
              new BigDecimal("1500.00"),
              category,
              CategoryType.EXPENSE,
              checking,
              paymentMethod,
              null,
              "Rent",
              null));
    }
    transactionRepository.save(
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 11, 2),
            new BigDecimal("9999.00"),
            category,
            CategoryType.EXPENSE,
            checking,
            paymentMethod,
            null,
            "This month does not count",
            null));
    transferRepository.save(
        Transfer.create(
            UUID.randomUUID(),
            LocalDate.of(2026, 10, 12),
            checking,
            savings,
            new BigDecimal("5000.00"),
            "A transfer is not an expense",
            null));

    mockMvc
        .perform(get("/api/goals/emergency-fund-suggestion").param("months", "6"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.months").value(6))
        .andExpect(jsonPath("$.monthsAveraged").value(2))
        .andExpect(jsonPath("$.averageMonthlyExpense").value(1500.00))
        .andExpect(jsonPath("$.suggestedTarget").value(9000.00));
  }

  @Test
  void theSuggestionDefaultsToSixMonthsAndRejectsOutOfRangeMonthsWith400() throws Exception {
    mockMvc
        .perform(get("/api/goals/emergency-fund-suggestion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.months").value(6));
    mockMvc
        .perform(get("/api/goals/emergency-fund-suggestion").param("months", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "handler-method-validation"));
    mockMvc
        .perform(get("/api/goals/emergency-fund-suggestion").param("months", "121"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "handler-method-validation"));
  }

  // --- the product side ---

  @Test
  void aProductListsTheNonArchivedGoalsItIsAllocatedTo() throws Exception {
    UUID product = productWorth("Fund Product Side", LocalDate.of(2026, 10, 20), "100.00");
    String car = createGoal("Car Product Side Goal", null, "1000", "0", true);
    allocate(car, product, "60");

    mockMvc
        .perform(get("/api/investment-products/" + product + "/goal-allocations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].goalId").value(car))
        .andExpect(jsonPath("$[0].goalName").value("Car Product Side Goal"))
        .andExpect(jsonPath("$[0].percentage").value(60.00));
    mockMvc
        .perform(get("/api/investment-products/" + UUID.randomUUID() + "/goal-allocations"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-product-not-found"));
  }
}
