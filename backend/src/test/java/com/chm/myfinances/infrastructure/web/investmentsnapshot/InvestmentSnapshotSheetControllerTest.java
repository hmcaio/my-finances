package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
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
 * REST-layer integration test for {@link InvestmentSnapshotSheetController} (F040) against a real
 * Testcontainers Postgres (ADR 0010): the open-holdings read, the atomic batch write and its error
 * bodies. What a failure in the middle of a batch leaves behind, and the concurrent same-day
 * conflict, are proven where there is no test-wide transaction ({@code
 * InvestmentSnapshotSheetTransactionalTest}, {@code InvestmentSnapshotServiceConcurrencyTest}).
 */
@WebIntegrationTest
class InvestmentSnapshotSheetControllerTest {

  private static final String SHEET_URL = "/api/investment-snapshot-sheet";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;

  private MockMvc mockMvc;
  private UUID brokerId;
  private UUID categoryId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Sheet Web Test",
                AccountType.INVESTMENT)
            .getId();
    categoryId = TaxonomySeeder.investmentCategory(jdbcTemplate, "Category Sheet Web Test").getId();
  }

  private UUID newHolding(String productName) {
    UUID productId =
        productRepository
            .save(InvestmentProduct.create(UUID.randomUUID(), categoryId, null, productName, null))
            .getId();
    return holdingRepository
        .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
        .getId();
  }

  private static String entryJson(Object holdingId, String balance) {
    return "{\"holdingId\":" + quote(holdingId) + ",\"balance\":" + balance + "}";
  }

  private static String quote(Object value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private static String body(String date, String... entries) {
    return "{\"date\":" + quote(date) + ",\"entries\":[" + String.join(",", entries) + "]}";
  }

  private ResultActions postSheet(String json) throws Exception {
    return mockMvc.perform(post(SHEET_URL).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  // ---- GET ----

  @Test
  void getListsTheOpenHoldingsWithTheirLatestSnapshotAndLeavesOutClosedOnes() throws Exception {
    UUID withSnapshot = newHolding("Alpha Sheet Web Test");
    UUID without = newHolding("Beta Sheet Web Test");
    UUID closed = newHolding("Gamma Sheet Web Test");
    postSheet(body("2026-05-31", entryJson(withSnapshot, "120.50"))).andExpect(status().isOk());
    postSheet(body("2026-05-31", entryJson(closed, "0"))).andExpect(status().isOk());
    InvestmentHolding closedHolding = holdingRepository.findById(closed).orElseThrow();
    closedHolding.close(java.time.LocalDate.of(2026, 5, 31));
    holdingRepository.save(closedHolding);

    String json =
        mockMvc
            .perform(get(SHEET_URL))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Map<String, Object>> rows =
        JsonPath.read(json, "$[?(@.holdingId=='" + withSnapshot + "')]");
    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("productName")).isEqualTo("Alpha Sheet Web Test");
              assertThat(row.get("accountName")).isEqualTo("Broker Sheet Web Test");
              assertThat(row.get("productId")).isNotNull();
              assertThat(row.get("accountId")).isEqualTo(brokerId.toString());
              assertThat(row.get("ticker")).isNull();
              assertThat(row.get("latestSnapshot"))
                  .isEqualTo(Map.of("date", "2026-05-31", "balance", 120.5));
            });
    List<Map<String, Object>> noSnapshotRows =
        JsonPath.read(json, "$[?(@.holdingId=='" + without + "')]");
    assertThat(noSnapshotRows)
        .singleElement()
        .satisfies(row -> assertThat(row.get("latestSnapshot")).isNull());
    List<Map<String, Object>> closedRows =
        JsonPath.read(json, "$[?(@.holdingId=='" + closed + "')]");
    assertThat(closedRows).isEmpty();
  }

  // ---- POST: success ----

  @Test
  void postRecordsEveryEntryAndReportsCreatedAndReplacedCounts() throws Exception {
    UUID first = newHolding("First Sheet Web Test");
    UUID second = newHolding("Second Sheet Web Test");
    postSheet(body("2026-05-31", entryJson(second, "50.00"))).andExpect(status().isOk());

    postSheet(body("2026-05-31", entryJson(first, "10.00"), entryJson(second, "60.00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.replaced").value(1));

    mockMvc
        .perform(get("/api/investment-holdings/" + second + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].balance").value(60.0));
  }

  // ---- POST: 400 ----

  @Test
  void aNegativeBalanceIsRejectedWithTheIndexedFieldAndNothingIsRecorded() throws Exception {
    UUID first = newHolding("First Sheet Web Test");
    UUID second = newHolding("Second Sheet Web Test");

    postSheet(body("2026-05-31", entryJson(first, "10.00"), entryJson(second, "-0.01")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:method-argument-not-valid"))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("entries[1].balance"))
        .andExpect(jsonPath("$.errors[0].code").value("positive-or-zero"));

    mockMvc
        .perform(get("/api/investment-holdings/" + first + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void moreThanTwoDecimalsAreRejectedWithTheIndexedField() throws Exception {
    UUID first = newHolding("First Sheet Web Test");

    postSheet(body("2026-05-31", entryJson(first, "10.001")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("entries[0].balance"))
        .andExpect(jsonPath("$.errors[0].code").value("digits"));
  }

  @Test
  void aMissingBalanceOrHoldingIdIsRejectedWithTheIndexedFields() throws Exception {
    UUID first = newHolding("First Sheet Web Test");

    postSheet(body("2026-05-31", "{\"holdingId\":\"" + first + "\"}", "{\"balance\":1.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[*].code", everyItem(is("not-null"))))
        .andExpect(
            jsonPath(
                "$.errors[*].field",
                containsInAnyOrder("entries[0].balance", "entries[1].holdingId")));
  }

  @Test
  void anEmptyEntryListIsRejected() throws Exception {
    postSheet(body("2026-05-31"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("entries"))
        .andExpect(jsonPath("$.errors[0].code").value("not-empty"));
  }

  @Test
  void aMissingDateIsRejected() throws Exception {
    UUID first = newHolding("First Sheet Web Test");

    postSheet("{\"entries\":[" + entryJson(first, "1.00") + "]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("date"))
        .andExpect(jsonPath("$.errors[0].code").value("not-null"));
  }

  @Test
  void moreThanTwoHundredEntriesAreRejected() throws Exception {
    List<String> entries = new ArrayList<>();
    for (int i = 0; i < 201; i++) {
      entries.add(entryJson(UUID.randomUUID(), "1.00"));
    }

    postSheet(body("2026-05-31", entries.toArray(String[]::new)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("entries"))
        .andExpect(jsonPath("$.errors[0].code").value("size"));
  }

  @Test
  void aRepeatedHoldingIsRejectedAsTheUniquenessInvariantWithANullField() throws Exception {
    UUID first = newHolding("First Sheet Web Test");

    postSheet(body("2026-05-31", entryJson(first, "1.00"), entryJson(first, "2.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value(nullValue()))
        .andExpect(jsonPath("$.errors[0].code").value("holding-ids-unique"));
  }

  // ---- POST: 404 ----

  @Test
  void unknownHoldingsAreRejectedWithEveryOffendingIndexAndNothingIsRecorded() throws Exception {
    UUID first = newHolding("First Sheet Web Test");
    UUID second = newHolding("Second Sheet Web Test");
    UUID gone1 = UUID.randomUUID();
    UUID gone2 = UUID.randomUUID();

    postSheet(
            body(
                "2026-05-31",
                entryJson(first, "1.00"),
                entryJson(gone1, "2.00"),
                entryJson(second, "3.00"),
                entryJson(gone2, "4.00")))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type")
                .value("urn:my-finances:error:investment-snapshot-sheet-holdings-not-found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").doesNotExist())
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[0].field").value("entries[1].holdingId"))
        .andExpect(jsonPath("$.errors[0].code").value("investment-holding-not-found"))
        .andExpect(jsonPath("$.errors[1].field").value("entries[3].holdingId"))
        .andExpect(jsonPath("$.errors[1].code").value("investment-holding-not-found"));

    mockMvc
        .perform(get("/api/investment-holdings/" + first + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(get("/api/investment-holdings/" + second + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(0));
  }
}
