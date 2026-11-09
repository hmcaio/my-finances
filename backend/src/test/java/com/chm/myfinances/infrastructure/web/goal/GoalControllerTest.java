package com.chm.myfinances.infrastructure.web.goal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link GoalController} and {@link GoalAllocationController}
 * (F039, ADR 0034), against a real Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc}.
 * The business rules are {@code GoalServiceTest}'s job; this adds what only the HTTP layer shows:
 * the statuses, the DTO {@code 400}s, the response shape, one case per rejection exception class
 * (problem {@code type}), and wiring to the real database (the percentage sum over other goals, the
 * cascade on delete, the product delete guard).
 */
@WebIntegrationTest
class GoalControllerTest {

  private static final String TYPE_PREFIX = "urn:my-finances:error:";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private MockMvc mockMvc;
  private UUID productId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    UUID categoryId =
        TaxonomySeeder.investmentCategory(jdbcTemplate, "Category Goal Web Test").getId();
    productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), categoryId, null, "Product Goal Web Test", null))
            .getId();
  }

  private static Map<String, Object> goalBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Car");
    body.put("targetAmount", "80000.00");
    body.put("targetDate", LocalDate.now().plusYears(2).toString());
    body.put("plannedMonthlyContribution", "2000.00");
    body.put("returnPessimistic", "2");
    body.put("returnBase", "4");
    body.put("returnOptimistic", "6");
    return body;
  }

  private static Map<String, Object> updateBody() {
    Map<String, Object> body = goalBody();
    body.put("status", "ACTIVE");
    body.put("showOnDashboard", true);
    return body;
  }

  private ResultActions postGoal(Map<String, Object> body) throws Exception {
    return mockMvc.perform(
        post("/api/goals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(body)));
  }

  private String createGoal() throws Exception {
    return JsonSupport.idOf(postGoal(goalBody()).andExpect(status().isCreated()).andReturn());
  }

  private ResultActions patchGoal(String id, Map<String, Object> body) throws Exception {
    return mockMvc.perform(
        patch("/api/goals/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(body)));
  }

  private ResultActions putAllocations(String goalId, List<Map<String, Object>> allocations)
      throws Exception {
    return mockMvc.perform(
        put("/api/goals/" + goalId + "/allocations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(Map.of("allocations", allocations))));
  }

  private Map<String, Object> share(UUID product, String percentage) {
    Map<String, Object> share = new LinkedHashMap<>();
    share.put("productId", product.toString());
    share.put("percentage", percentage);
    return share;
  }

  private void assertType(ResultActions result, String slug) throws Exception {
    result.andExpect(jsonPath("$.type").value(TYPE_PREFIX + slug));
  }

  // --- create / read ---

  @Test
  void postCreatesWith201ReturnsTheGoalAndDefaultsTheDashboardFlagOn() throws Exception {
    postGoal(goalBody())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Car"))
        .andExpect(jsonPath("$.targetAmount").value(80000.00))
        .andExpect(jsonPath("$.plannedMonthlyContribution").value(2000.00))
        .andExpect(jsonPath("$.returnBase").value(4.0))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.showOnDashboard").value(true));
  }

  @Test
  void aDatelessGoalWithZeroContributionIsAccepted() throws Exception {
    Map<String, Object> body = goalBody();
    body.remove("targetDate");
    body.put("plannedMonthlyContribution", "0");

    postGoal(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetDate").doesNotExist());
  }

  @Test
  void getReturnsTheGoalAndAnUnknownIdIs404() throws Exception {
    String id = createGoal();

    mockMvc
        .perform(get("/api/goals/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    assertType(
        mockMvc.perform(get("/api/goals/" + UUID.randomUUID())).andExpect(status().isNotFound()),
        "goal-not-found");
  }

  @Test
  void theListHidesArchivedGoalsUnlessAsked() throws Exception {
    String active = createGoal();
    String archived = createGoal();
    Map<String, Object> archive = updateBody();
    archive.put("status", "ARCHIVED");
    patchGoal(archived, archive).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/goals"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + active + "')]").exists())
        .andExpect(jsonPath("$[?(@.id == '" + archived + "')]").doesNotExist());
    mockMvc
        .perform(get("/api/goals").param("includeArchived", "true"))
        .andExpect(jsonPath("$[?(@.id == '" + archived + "')]").exists());
  }

  // --- validation ---

  @Test
  void aBlankOrTooLongNameOrTooLongNotesIs400WithTheViolationCodes() throws Exception {
    Map<String, Object> blank = goalBody();
    blank.put("name", " ");
    assertType(postGoal(blank).andExpect(status().isBadRequest()), "method-argument-not-valid");
    postGoal(blank)
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].code").value("not-blank"));

    Map<String, Object> longName = goalBody();
    longName.put("name", "n".repeat(101));
    postGoal(longName)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].code").value("size"));

    Map<String, Object> longNotes = goalBody();
    longNotes.put("notes", "n".repeat(501));
    postGoal(longNotes)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("notes"))
        .andExpect(jsonPath("$.errors[0].code").value("size"));
  }

  @Test
  void aNonPositiveTargetANegativeContributionAndOutOfRangeReturnsAre400() throws Exception {
    Map<String, Object> zeroTarget = goalBody();
    zeroTarget.put("targetAmount", "0");
    postGoal(zeroTarget)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("targetAmount"))
        .andExpect(jsonPath("$.errors[0].code").value("positive"));

    Map<String, Object> negative = goalBody();
    negative.put("plannedMonthlyContribution", "-1");
    postGoal(negative)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("plannedMonthlyContribution"))
        .andExpect(jsonPath("$.errors[0].code").value("positive-or-zero"));

    Map<String, Object> highReturn = goalBody();
    highReturn.put("returnOptimistic", "100.01");
    postGoal(highReturn)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("returnOptimistic"));

    Map<String, Object> lowReturn = goalBody();
    lowReturn.put("returnPessimistic", "-50.01");
    postGoal(lowReturn)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("returnPessimistic"));

    Map<String, Object> missing = goalBody();
    missing.remove("returnBase");
    postGoal(missing)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("not-null"));
  }

  @Test
  void unorderedReturnsAndAPastTargetDateAreInvalidGoal() throws Exception {
    Map<String, Object> unordered = goalBody();
    unordered.put("returnPessimistic", "5");
    assertType(postGoal(unordered).andExpect(status().isBadRequest()), "invalid-goal");

    Map<String, Object> past = goalBody();
    past.put("targetDate", LocalDate.now().toString());
    assertType(postGoal(past).andExpect(status().isBadRequest()), "invalid-goal");
  }

  // --- edit / dashboard flag / delete ---

  @Test
  void patchReplacesTheGoalAndAnUnknownIdIs404() throws Exception {
    String id = createGoal();
    Map<String, Object> body = updateBody();
    body.put("name", "House");
    body.put("status", "ACHIEVED");

    patchGoal(id, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("House"))
        .andExpect(jsonPath("$.status").value("ACHIEVED"));
    assertType(
        patchGoal(UUID.randomUUID().toString(), body).andExpect(status().isNotFound()),
        "goal-not-found");
  }

  @Test
  void patchWithoutAStatusIs400() throws Exception {
    String id = createGoal();
    Map<String, Object> body = updateBody();
    body.remove("status");

    patchGoal(id, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("status"));
  }

  @Test
  void showOnDashboardTogglesAndIsRejectedWith409ForAnArchivedGoal() throws Exception {
    String id = createGoal();

    mockMvc
        .perform(
            patch("/api/goals/" + id + "/show-on-dashboard")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showOnDashboard\": false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showOnDashboard").value(false));

    Map<String, Object> archive = updateBody();
    archive.put("status", "ARCHIVED");
    patchGoal(id, archive).andExpect(status().isOk());
    assertType(
        mockMvc
            .perform(
                patch("/api/goals/" + id + "/show-on-dashboard")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"showOnDashboard\": true}"))
            .andExpect(status().isConflict()),
        "goal-archived");
    mockMvc
        .perform(
            patch("/api/goals/" + id + "/show-on-dashboard")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            patch("/api/goals/" + UUID.randomUUID() + "/show-on-dashboard")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showOnDashboard\": false}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRemovesTheGoalAndItsAllocationsAndAnUnknownIdIs404() throws Exception {
    String id = createGoal();
    putAllocations(id, List.of(share(productId, "50"))).andExpect(status().isOk());

    mockMvc.perform(delete("/api/goals/" + id)).andExpect(status().isNoContent());
    // The test shares one transaction with the requests, so make the delete (and its database
    // cascade) visible to the next request's existence query.
    entityManager.flush();

    mockMvc.perform(get("/api/goals/" + id)).andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/investment-products/" + productId))
        .andExpect(status().isNoContent());
    assertType(
        mockMvc.perform(delete("/api/goals/" + id)).andExpect(status().isNotFound()),
        "goal-not-found");
  }

  // --- allocations ---

  @Test
  void putAllocationsReplacesTheSetAndReturnsTheStoredList() throws Exception {
    String id = createGoal();

    putAllocations(id, List.of(share(productId, "60.5")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$[0].percentage").value(60.5));
    putAllocations(id, List.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void aBadPercentageIs400WithTheViolationCodes() throws Exception {
    String id = createGoal();

    putAllocations(id, List.of(share(productId, "0")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("positive"));
    putAllocations(id, List.of(share(productId, "100.01")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("decimal-max"));
    putAllocations(id, List.of(share(productId, "10.123")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("digits"));
  }

  @Test
  void aRepeatedProductIsInvalidGoal() throws Exception {
    String id = createGoal();

    assertType(
        putAllocations(id, List.of(share(productId, "10"), share(productId, "20")))
            .andExpect(status().isBadRequest()),
        "invalid-goal");
  }

  @Test
  void anUnknownProductOrGoalIs404() throws Exception {
    String id = createGoal();

    assertType(
        putAllocations(id, List.of(share(UUID.randomUUID(), "10")))
            .andExpect(status().isNotFound()),
        "investment-product-not-found");
    assertType(
        putAllocations(UUID.randomUUID().toString(), List.of(share(productId, "10")))
            .andExpect(status().isNotFound()),
        "goal-not-found");
  }

  @Test
  void anArchivedGoalCannotBeReallocatedAndAnOverflowingProductIs409() throws Exception {
    String first = createGoal();
    String second = createGoal();
    putAllocations(first, List.of(share(productId, "70"))).andExpect(status().isOk());

    assertType(
        putAllocations(second, List.of(share(productId, "40"))).andExpect(status().isConflict()),
        "goal-allocation-exceeds-product");

    Map<String, Object> archive = updateBody();
    archive.put("status", "ARCHIVED");
    patchGoal(first, archive).andExpect(status().isOk());
    putAllocations(second, List.of(share(productId, "40"))).andExpect(status().isOk());
    assertType(
        putAllocations(first, List.of(share(productId, "10"))).andExpect(status().isConflict()),
        "goal-archived");
  }

  @Test
  void restoringAGoalWhosePercentagesNoLongerFitIs409() throws Exception {
    String first = createGoal();
    String second = createGoal();
    putAllocations(first, List.of(share(productId, "70"))).andExpect(status().isOk());
    Map<String, Object> archive = updateBody();
    archive.put("status", "ARCHIVED");
    patchGoal(first, archive).andExpect(status().isOk());
    putAllocations(second, List.of(share(productId, "50"))).andExpect(status().isOk());

    assertType(
        patchGoal(first, updateBody()).andExpect(status().isConflict()),
        "goal-allocation-exceeds-product");
  }

  @Test
  void aProductAllocatedToAGoalCannotBeDeletedAndIsA409NotAForeignKeyError() throws Exception {
    String id = createGoal();
    putAllocations(id, List.of(share(productId, "30"))).andExpect(status().isOk());

    ResultActions result =
        mockMvc
            .perform(delete("/api/investment-products/" + productId))
            .andExpect(status().isConflict());

    result.andExpect(
        jsonPath("$.type").value(TYPE_PREFIX + "investment-product-allocated-to-goal"));
  }
}
