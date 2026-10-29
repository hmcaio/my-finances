package com.chm.myfinances.infrastructure.web.investmentopeningposition;

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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
 * REST-layer integration test for {@link InvestmentOpeningPositionController} (F038, ADR 0033),
 * against a real Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc}. The business rules
 * are the service test's job ({@code InvestmentOpeningPositionServiceTest}); this adds what only
 * the HTTP layer shows: the {@code 201}/{@code 200}/{@code 204} statuses, the DTO {@code 400}s, the
 * response shape, one case per rejection exception class (problem {@code type}), and wiring to the
 * real database (the snapshot, the delete-safety checker, the trade-confirmation guard).
 */
@WebIntegrationTest
class InvestmentOpeningPositionControllerTest {

  private static final String TYPE_PREFIX = "urn:my-finances:error:";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;

  private MockMvc mockMvc;
  private UUID checkingId;
  private UUID brokerId;
  private UUID productId;
  private UUID holdingId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    checkingId =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Cash Position Web")
            .getId();
    brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Position Web Test",
                AccountType.INVESTMENT)
            .getId();
    UUID categoryId =
        categoryRepository
            .save(InvestmentCategory.reconstitute(UUID.randomUUID(), "Category Position Web Test"))
            .getId();
    productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), categoryId, null, "Product Position Web Test", null))
            .getId();
    holdingId =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
            .getId();
  }

  private static String url(UUID holding) {
    return "/api/investment-holdings/" + holding + "/opening-position";
  }

  private ResultActions putPosition(UUID holding, Map<String, Object> body) throws Exception {
    return mockMvc.perform(
        put(url(holding))
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(body)));
  }

  private static Map<String, Object> body(String asOfDate, String quantity, String unitPrice) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("asOfDate", asOfDate);
    body.put("quantity", quantity);
    body.put("unitPrice", unitPrice);
    return body;
  }

  private static Map<String, Object> validBody() {
    return body("2026-03-01", "40", "150.00");
  }

  @Test
  void putCreatesWith201AndReturnsThePositionWithItsContributedAmount() throws Exception {
    putPosition(holdingId, validBody())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.holdingId").value(holdingId.toString()))
        .andExpect(jsonPath("$.asOfDate").value("2026-03-01"))
        .andExpect(jsonPath("$.quantity").value(40))
        .andExpect(jsonPath("$.unitPrice").value(150.00))
        .andExpect(jsonPath("$.amountContributed").value(6000.00));
  }

  @Test
  void aSecondPutReplacesWith200AndGetReturnsTheReplacement() throws Exception {
    putPosition(holdingId, validBody()).andExpect(status().isCreated());

    putPosition(holdingId, body("2026-02-01", "50", "140.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asOfDate").value("2026-02-01"))
        .andExpect(jsonPath("$.quantity").value(50));

    mockMvc
        .perform(get(url(holdingId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(50))
        .andExpect(jsonPath("$.amountContributed").value(7000.00));
  }

  @Test
  void aCurrentValueRecordsTheSnapshotDatedAsOfAndIsNotPartOfThePosition() throws Exception {
    Map<String, Object> withValue = validBody();
    withValue.put("currentValue", "6400.00");

    putPosition(holdingId, withValue)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currentValue").doesNotExist());

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2026-03-01"))
        .andExpect(jsonPath("$[0].balance").value(6400.00));
  }

  @Test
  void deleteRemovesThePositionWith204AndTheNextGetIsTheEmptyState() throws Exception {
    putPosition(holdingId, validBody()).andExpect(status().isCreated());

    mockMvc.perform(delete(url(holdingId))).andExpect(status().isNoContent());

    mockMvc
        .perform(get(url(holdingId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-opening-position-not-found"));
  }

  @Test
  void anUnknownHoldingIs404WithItsOwnProblemTypeOnEveryVerb() throws Exception {
    UUID unknown = UUID.randomUUID();

    mockMvc
        .perform(get(url(unknown)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-holding-not-found"));
    putPosition(unknown, validBody())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-holding-not-found"));
    mockMvc
        .perform(delete(url(unknown)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-holding-not-found"));
  }

  @Test
  void deletingWhenThereIsNoPositionIs404() throws Exception {
    mockMvc
        .perform(delete(url(holdingId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-opening-position-not-found"));
  }

  @Test
  void aNonPositiveOrMissingFieldIs400WithTheViolationCodes() throws Exception {
    putPosition(holdingId, body("2026-03-01", "0", "150.00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "method-argument-not-valid"))
        .andExpect(jsonPath("$.errors[0].field").value("quantity"))
        .andExpect(jsonPath("$.errors[0].code").value("positive"));
    putPosition(holdingId, body("2026-03-01", "40", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("unitPrice"));
    mockMvc
        .perform(
            put(url(holdingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 1, \"unitPrice\": 1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("asOfDate"))
        .andExpect(jsonPath("$.errors[0].code").value("not-null"));
  }

  @Test
  void moreThanEightDecimalsOrANegativeCurrentValueIs400() throws Exception {
    putPosition(holdingId, body("2026-03-01", "1.123456789", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("digits"));
    Map<String, Object> negativeValue = validBody();
    negativeValue.put("currentValue", "-1");
    putPosition(holdingId, negativeValue)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("currentValue"))
        .andExpect(jsonPath("$.errors[0].code").value("positive-or-zero"));
  }

  @Test
  void aFutureAsOfDateIs400InvalidOpeningPosition() throws Exception {
    putPosition(holdingId, body(LocalDate.now().plusDays(1).toString(), "40", "150.00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "invalid-opening-position"))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @Test
  void aClosedHoldingIs409OnPutAndDelete() throws Exception {
    putPosition(holdingId, validBody()).andExpect(status().isCreated());
    InvestmentHolding holding = holdingRepository.findById(holdingId).orElseThrow();
    holding.close(LocalDate.now());
    holdingRepository.save(holding);

    putPosition(holdingId, validBody())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-holding-closed"));
    mockMvc
        .perform(delete(url(holdingId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-holding-closed"));
  }

  // --- the date order against trades, over the real database ---

  private Map<String, Object> tradeBody(String date) {
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("productId", productId.toString());
    line.put("side", "BUY");
    line.put("quantity", "10");
    line.put("unitPrice", "100.00");
    Map<String, Object> confirmation = new LinkedHashMap<>();
    confirmation.put("taxes", "0.00");
    confirmation.put("lines", List.of(line));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date);
    body.put("description", "Trade");
    body.put("cashAccountId", checkingId.toString());
    body.put("investmentAccountId", brokerId.toString());
    body.put("tradeConfirmation", confirmation);
    return body;
  }

  private ResultActions trade(String date) throws Exception {
    return mockMvc.perform(
        post("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(tradeBody(date))));
  }

  @Test
  void aPositionDatedAfterTheHoldingsEarliestTradeIs409AndTheSameDayIsAccepted() throws Exception {
    trade("2026-02-10").andExpect(status().isCreated());

    putPosition(holdingId, body("2026-02-11", "40", "150.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "opening-position-after-trades"));
    putPosition(holdingId, body("2026-02-10", "40", "150.00")).andExpect(status().isCreated());
  }

  @Test
  void aTradeDatedBeforeThePositionIs409OnPostTransfersAndTheSameDayIsAccepted() throws Exception {
    putPosition(holdingId, body("2026-02-10", "40", "150.00")).andExpect(status().isCreated());

    trade("2026-02-09")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "trade-before-opening-position"));
    trade("2026-02-10").andExpect(status().isCreated());
    trade("2026-03-01").andExpect(status().isCreated());
  }

  @Test
  void aHoldingWithOnlyAPositionCannotBeHardDeletedUntilThePositionIsDeleted() throws Exception {
    putPosition(holdingId, validBody()).andExpect(status().isCreated());

    mockMvc
        .perform(delete("/api/investment-holdings/" + holdingId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "investment-holding-has-history"));

    mockMvc.perform(delete(url(holdingId))).andExpect(status().isNoContent());
    mockMvc
        .perform(delete("/api/investment-holdings/" + holdingId))
        .andExpect(status().isNoContent());
  }
}
