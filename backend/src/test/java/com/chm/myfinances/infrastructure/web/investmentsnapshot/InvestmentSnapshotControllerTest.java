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
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
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
 * REST-layer integration test for {@link InvestmentSnapshotController} (F009 spec, moved from the
 * product to the holding by F022/ADR 0020), against a real Testcontainers Postgres (ADR 0010),
 * hand-built {@link MockMvc}. Also covers what a snapshot does to the holding endpoints: {@code
 * hasHistory}/delete-safety now comes from the real checker (a snapshot blocks a hard delete), and
 * {@code latestSnapshot}/{@code needsSnapshot} on the holding response and list, and the close
 * guard.
 */
@WebIntegrationTest
class InvestmentSnapshotControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;

  private MockMvc mockMvc;
  private UUID productId;
  private UUID holdingId;

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
                    UUID.randomUUID(), categoryId, null, "Product Snapshot Web Test", null))
            .getId();
    holdingId =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
            .getId();
  }

  private ResultActions recordSnapshot(UUID holding, String date, String balance) throws Exception {
    return mockMvc.perform(
        post("/api/investment-holdings/" + holding + "/snapshots")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(Map.of("date", date, "balance", balance))));
  }

  @Test
  void recordCreatesASnapshotWith201() throws Exception {
    recordSnapshot(holdingId, "2026-03-31", "1234.56")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.holdingId").value(holdingId.toString()))
        .andExpect(jsonPath("$.date").value("2026-03-31"))
        .andExpect(jsonPath("$.balance").value(1234.56));
  }

  @Test
  void aSecondSnapshotOnTheSameDayReplacesTheFirstWith200() throws Exception {
    recordSnapshot(holdingId, "2026-03-31", "100.00").andExpect(status().isCreated());

    recordSnapshot(holdingId, "2026-03-31", "120.00")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(120.00));

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].balance").value(120.00));
  }

  @Test
  void aZeroBalanceIsAccepted() throws Exception {
    recordSnapshot(holdingId, "2026-03-31", "0.00")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balance").value(0));
  }

  @Test
  void aNegativeBalanceIsRejectedWith400() throws Exception {
    recordSnapshot(holdingId, "2026-03-31", "-0.01").andExpect(status().isBadRequest());
  }

  @Test
  void aMissingDateOrBalanceIsRejectedWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-holdings/" + holdingId + "/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\": 1.00}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/investment-holdings/" + holdingId + "/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"2026-03-31\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anUnknownHoldingIs404OnBothEndpoints() throws Exception {
    recordSnapshot(UUID.randomUUID(), "2026-03-31", "1.00").andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/investment-holdings/" + UUID.randomUUID() + "/snapshots"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listReturnsSnapshotsMostRecentFirst() throws Exception {
    recordSnapshot(holdingId, "2026-01-31", "1.00");
    recordSnapshot(holdingId, "2026-03-31", "3.00");
    recordSnapshot(holdingId, "2026-02-28", "2.00");

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2026-03-31"))
        .andExpect(jsonPath("$[1].date").value("2026-02-28"))
        .andExpect(jsonPath("$[2].date").value("2026-01-31"));
  }

  @Test
  void aSnapshotBlocksHardDeletingTheHoldingAndTheHoldingReportsHistory() throws Exception {
    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.hasHistory").value(false))
        .andExpect(jsonPath("$.latestSnapshot").doesNotExist());

    recordSnapshot(holdingId, "2026-03-31", "100.00");

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.hasHistory").value(true))
        .andExpect(jsonPath("$.latestSnapshot.date").value("2026-03-31"))
        .andExpect(jsonPath("$.latestSnapshot.balance").value(100.00))
        .andExpect(jsonPath("$.needsSnapshot").value(false));
    mockMvc
        .perform(delete("/api/investment-holdings/" + holdingId))
        .andExpect(status().isConflict());
  }

  @Test
  void closingIsRejectedWithANonZeroLatestSnapshotAndAllowedOnceItIsZero() throws Exception {
    recordSnapshot(holdingId, "2026-03-31", "100.00");

    mockMvc
        .perform(post("/api/investment-holdings/" + holdingId + "/close"))
        .andExpect(status().isConflict());

    recordSnapshot(holdingId, "2026-04-30", "0.00");

    mockMvc
        .perform(post("/api/investment-holdings/" + holdingId + "/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true));
  }

  @Test
  void aHoldingWithNoSnapshotCanBeClosed() throws Exception {
    mockMvc
        .perform(post("/api/investment-holdings/" + holdingId + "/close"))
        .andExpect(status().isOk());
  }

  private String snapshotId(UUID holding, String date, String balance) throws Exception {
    String body =
        recordSnapshot(holding, date, balance)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private ResultActions updateSnapshot(UUID holding, String id, String date, String balance)
      throws Exception {
    return mockMvc.perform(
        put("/api/investment-holdings/" + holding + "/snapshots/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(Map.of("date", date, "balance", balance))));
  }

  @Test
  void updateChangesDateAndBalance() throws Exception {
    String id = snapshotId(holdingId, "2026-03-31", "100.00");

    updateSnapshot(holdingId, id, "2026-03-30", "90.50")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.date").value("2026-03-30"))
        .andExpect(jsonPath("$.balance").value(90.50));

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2026-03-30"));
  }

  @Test
  void updateOntoAnOccupiedDateIs409() throws Exception {
    snapshotId(holdingId, "2026-03-30", "50.00");
    String id = snapshotId(holdingId, "2026-03-31", "100.00");

    updateSnapshot(holdingId, id, "2026-03-30", "1.00").andExpect(status().isConflict());
  }

  @Test
  void updateValidatesTheBodyLikeRecord() throws Exception {
    String id = snapshotId(holdingId, "2026-03-31", "100.00");

    updateSnapshot(holdingId, id, "2026-03-31", "-0.01").andExpect(status().isBadRequest());
    updateSnapshot(holdingId, id, "2026-03-31", "0.00").andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/investment-holdings/" + holdingId + "/snapshots/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\": 1.00}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateAndDeleteReturn404ForUnknownHoldingOrSnapshotOrOtherHoldingsSnapshot()
      throws Exception {
    String id = snapshotId(holdingId, "2026-03-31", "100.00");
    UUID otherHolding =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, saveAnotherBroker(), null))
            .getId();

    updateSnapshot(holdingId, UUID.randomUUID().toString(), "2026-03-31", "1.00")
        .andExpect(status().isNotFound());
    updateSnapshot(otherHolding, id, "2026-03-31", "1.00").andExpect(status().isNotFound());
    updateSnapshot(UUID.randomUUID(), id, "2026-03-31", "1.00").andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/investment-holdings/" + holdingId + "/snapshots/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/investment-holdings/" + otherHolding + "/snapshots/" + id))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/investment-holdings/" + UUID.randomUUID() + "/snapshots/" + id))
        .andExpect(status().isNotFound());
  }

  private UUID saveAnotherBroker() {
    return TestFixtures.account(
            accountRepository,
            institutionRepository,
            "Other Broker Snapshot Web Test",
            AccountType.INVESTMENT)
        .getId();
  }

  @Test
  void deleteRemovesTheSnapshotWith204() throws Exception {
    String id = snapshotId(holdingId, "2026-03-31", "100.00");

    mockMvc
        .perform(delete("/api/investment-holdings/" + holdingId + "/snapshots/" + id))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.hasHistory").value(false));
  }

  @Test
  void editAndDeleteThatWouldLeaveAClosedHoldingNonZeroAre409() throws Exception {
    snapshotId(holdingId, "2026-03-31", "100.00");
    String zeroId = snapshotId(holdingId, "2026-04-30", "0.00");
    mockMvc
        .perform(post("/api/investment-holdings/" + holdingId + "/close"))
        .andExpect(status().isOk());

    updateSnapshot(holdingId, zeroId, "2026-04-30", "5.00").andExpect(status().isConflict());
    mockMvc
        .perform(delete("/api/investment-holdings/" + holdingId + "/snapshots/" + zeroId))
        .andExpect(status().isConflict());
    updateSnapshot(holdingId, zeroId, "2026-04-29", "0.00").andExpect(status().isOk());
  }

  @Test
  void theHoldingListCarriesNeedsSnapshotAndLatestSnapshotToo() throws Exception {
    recordSnapshot(holdingId, "2026-03-31", "55.00");

    mockMvc
        .perform(get("/api/investment-holdings").param("productId", productId.toString()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == '" + holdingId + "')].latestSnapshot.balance")
                .value(hasItem(55.00)))
        .andExpect(
            jsonPath("$[?(@.id == '" + holdingId + "')].needsSnapshot").value(hasItem(false)));
  }
}
