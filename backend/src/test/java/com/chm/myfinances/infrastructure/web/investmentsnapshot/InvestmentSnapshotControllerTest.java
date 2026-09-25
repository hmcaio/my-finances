package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link InvestmentSnapshotController} (F009), against a real
 * Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc}. Also covers what a snapshot does
 * to the product endpoints: {@code hasHistory}/delete-safety now comes from the real checker (a
 * snapshot blocks a hard delete), {@code latestSnapshot} on the response, and the close guard.
 */
@WebIntegrationTest
class InvestmentSnapshotControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;

  private MockMvc mockMvc;
  private UUID productId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    UUID brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Snapshot Web Test",
                AccountType.INVESTMENT)
            .getId();
    UUID categoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Category Snapshot Web Test"))
            .getId();
    productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), brokerId, categoryId, null, "Product Snapshot Web Test"))
            .getId();
  }

  private ResultActions recordSnapshot(UUID product, String date, String balance) throws Exception {
    return mockMvc.perform(
        post("/api/investment-products/" + product + "/snapshots")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(Map.of("date", date, "balance", balance))));
  }

  @Test
  void recordCreatesASnapshotWith201() throws Exception {
    recordSnapshot(productId, "2026-03-31", "1234.56")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productId").value(productId.toString()))
        .andExpect(jsonPath("$.date").value("2026-03-31"))
        .andExpect(jsonPath("$.balance").value(1234.56));
  }

  @Test
  void aSecondSnapshotOnTheSameDayReplacesTheFirstWith200() throws Exception {
    recordSnapshot(productId, "2026-03-31", "100.00").andExpect(status().isCreated());

    recordSnapshot(productId, "2026-03-31", "120.00")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(120.00));

    mockMvc
        .perform(get("/api/investment-products/" + productId + "/snapshots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].balance").value(120.00));
  }

  @Test
  void aZeroBalanceIsAccepted() throws Exception {
    recordSnapshot(productId, "2026-03-31", "0.00")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balance").value(0));
  }

  @Test
  void aNegativeBalanceIsRejectedWith400() throws Exception {
    recordSnapshot(productId, "2026-03-31", "-0.01").andExpect(status().isBadRequest());
  }

  @Test
  void aMissingDateOrBalanceIsRejectedWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-products/" + productId + "/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\": 1.00}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/investment-products/" + productId + "/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"2026-03-31\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anUnknownProductIs404OnBothEndpoints() throws Exception {
    recordSnapshot(UUID.randomUUID(), "2026-03-31", "1.00").andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/investment-products/" + UUID.randomUUID() + "/snapshots"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listReturnsSnapshotsMostRecentFirst() throws Exception {
    recordSnapshot(productId, "2026-01-31", "1.00");
    recordSnapshot(productId, "2026-03-31", "3.00");
    recordSnapshot(productId, "2026-02-28", "2.00");

    mockMvc
        .perform(get("/api/investment-products/" + productId + "/snapshots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2026-03-31"))
        .andExpect(jsonPath("$[1].date").value("2026-02-28"))
        .andExpect(jsonPath("$[2].date").value("2026-01-31"));
  }

  @Test
  void aSnapshotBlocksHardDeletingTheProductAndTheProductReportsHistory() throws Exception {
    mockMvc
        .perform(get("/api/investment-products/" + productId))
        .andExpect(jsonPath("$.hasHistory").value(false))
        .andExpect(jsonPath("$.latestSnapshot").doesNotExist());

    recordSnapshot(productId, "2026-03-31", "100.00");

    mockMvc
        .perform(get("/api/investment-products/" + productId))
        .andExpect(jsonPath("$.hasHistory").value(true))
        .andExpect(jsonPath("$.latestSnapshot.date").value("2026-03-31"))
        .andExpect(jsonPath("$.latestSnapshot.balance").value(100.00))
        .andExpect(jsonPath("$.needsSnapshot").value(false));
    mockMvc
        .perform(delete("/api/investment-products/" + productId))
        .andExpect(status().isConflict());
  }

  @Test
  void closingIsRejectedWithANonZeroLatestSnapshotAndAllowedOnceItIsZero() throws Exception {
    recordSnapshot(productId, "2026-03-31", "100.00");

    mockMvc
        .perform(post("/api/investment-products/" + productId + "/close"))
        .andExpect(status().isConflict());

    recordSnapshot(productId, "2026-04-30", "0.00");

    mockMvc
        .perform(post("/api/investment-products/" + productId + "/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true));
  }

  @Test
  void aProductWithNoSnapshotCanBeClosed() throws Exception {
    mockMvc
        .perform(post("/api/investment-products/" + productId + "/close"))
        .andExpect(status().isOk());
  }

  private String snapshotId(UUID product, String date, String balance) throws Exception {
    String body =
        recordSnapshot(product, date, balance)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private ResultActions updateSnapshot(UUID product, String id, String date, String balance)
      throws Exception {
    return mockMvc.perform(
        put("/api/investment-products/" + product + "/snapshots/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(Map.of("date", date, "balance", balance))));
  }

  @Test
  void updateChangesDateAndBalance() throws Exception {
    String id = snapshotId(productId, "2026-03-31", "100.00");

    updateSnapshot(productId, id, "2026-03-30", "90.50")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.date").value("2026-03-30"))
        .andExpect(jsonPath("$.balance").value(90.50));

    mockMvc
        .perform(get("/api/investment-products/" + productId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2026-03-30"));
  }

  @Test
  void updateOntoAnOccupiedDateIs409() throws Exception {
    snapshotId(productId, "2026-03-30", "50.00");
    String id = snapshotId(productId, "2026-03-31", "100.00");

    updateSnapshot(productId, id, "2026-03-30", "1.00").andExpect(status().isConflict());
  }

  @Test
  void updateValidatesTheBodyLikeRecord() throws Exception {
    String id = snapshotId(productId, "2026-03-31", "100.00");

    updateSnapshot(productId, id, "2026-03-31", "-0.01").andExpect(status().isBadRequest());
    updateSnapshot(productId, id, "2026-03-31", "0.00").andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/investment-products/" + productId + "/snapshots/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\": 1.00}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateAndDeleteReturn404ForUnknownProductOrSnapshotOrOtherProductsSnapshot()
      throws Exception {
    String id = snapshotId(productId, "2026-03-31", "100.00");
    InvestmentProduct first = productRepository.findById(productId).orElseThrow();
    UUID otherProduct =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(),
                    first.getAccountId(),
                    first.getInvestmentCategoryId(),
                    null,
                    "Other Product Snapshot Web Test"))
            .getId();

    updateSnapshot(productId, UUID.randomUUID().toString(), "2026-03-31", "1.00")
        .andExpect(status().isNotFound());
    updateSnapshot(otherProduct, id, "2026-03-31", "1.00").andExpect(status().isNotFound());
    updateSnapshot(UUID.randomUUID(), id, "2026-03-31", "1.00").andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/investment-products/" + productId + "/snapshots/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/investment-products/" + otherProduct + "/snapshots/" + id))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/investment-products/" + UUID.randomUUID() + "/snapshots/" + id))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRemovesTheSnapshotWith204() throws Exception {
    String id = snapshotId(productId, "2026-03-31", "100.00");

    mockMvc
        .perform(delete("/api/investment-products/" + productId + "/snapshots/" + id))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/investment-products/" + productId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(get("/api/investment-products/" + productId))
        .andExpect(jsonPath("$.hasHistory").value(false));
  }

  @Test
  void editAndDeleteThatWouldLeaveAClosedProductNonZeroAre409() throws Exception {
    snapshotId(productId, "2026-03-31", "100.00");
    String zeroId = snapshotId(productId, "2026-04-30", "0.00");
    mockMvc
        .perform(post("/api/investment-products/" + productId + "/close"))
        .andExpect(status().isOk());

    updateSnapshot(productId, zeroId, "2026-04-30", "5.00").andExpect(status().isConflict());
    mockMvc
        .perform(delete("/api/investment-products/" + productId + "/snapshots/" + zeroId))
        .andExpect(status().isConflict());
    updateSnapshot(productId, zeroId, "2026-04-29", "0.00").andExpect(status().isOk());
  }

  @Test
  void theProductListCarriesNeedsSnapshotAndLatestSnapshotToo() throws Exception {
    recordSnapshot(productId, "2026-03-31", "55.00");

    mockMvc
        .perform(get("/api/investment-products"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == '" + productId + "')].latestSnapshot.balance")
                .value(hasItem(55.00)))
        .andExpect(
            jsonPath("$[?(@.id == '" + productId + "')].needsSnapshot").value(hasItem(false)));
  }
}
