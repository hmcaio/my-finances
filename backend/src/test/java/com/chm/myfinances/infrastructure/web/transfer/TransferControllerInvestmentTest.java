package com.chm.myfinances.infrastructure.web.transfer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
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
 * REST-layer tests for the F027 trade-confirmation additions to {@link TransferController} (ADR
 * 0024, superseding F009/F022's single-product shape): a confirmation's {@code cashAccountId}/
 * {@code investmentAccountId}/{@code tradeConfirmation} request shape, the create/edit
 * mutual-exclusivity 400 against the plain {@code fromAccountId}/{@code toAccountId}/{@code amount}
 * shape, per-line holding 404/409s, the net-zero-settlement 400, the optional per-line {@code
 * resultingBalance}/{@code closeHolding} side effects, and the {@code investmentProductId} list
 * filter (now a join to {@code transfer_trade_lines}).
 */
@WebIntegrationTest
class TransferControllerInvestmentTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;

  private MockMvc mockMvc;
  private UUID checkingId;
  private UUID savingsId;
  private UUID brokerId;
  private UUID otherBrokerId;
  private UUID productId;
  private UUID holdingId;
  private UUID otherProductId;
  private UUID otherHoldingId;
  private UUID otherBrokerProductId;
  private UUID categoryId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    checkingId = account("Checking Trade Web Test", AccountType.CHECKING);
    savingsId = account("Savings Trade Web Test", AccountType.SAVINGS);
    brokerId = account("Broker Trade Web Test", AccountType.INVESTMENT);
    otherBrokerId = account("Other Broker Trade Web Test", AccountType.INVESTMENT);
    categoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Category Trade Web Test"))
            .getId();
    productId = product("Product Trade Web Test");
    holdingId = holding(productId, brokerId);
    otherProductId = product("Other Product Trade Web Test");
    otherHoldingId = holding(otherProductId, brokerId);
    otherBrokerProductId = product("Other Broker Product Trade Web Test");
    holding(otherBrokerProductId, otherBrokerId);
  }

  private UUID account(String name, AccountType type) {
    return TestFixtures.account(accountRepository, institutionRepository, name, type).getId();
  }

  private UUID product(String name) {
    return productRepository
        .save(InvestmentProduct.create(UUID.randomUUID(), categoryId, null, name, null))
        .getId();
  }

  private UUID holding(UUID productId, UUID accountId) {
    return holdingRepository
        .save(InvestmentHolding.create(UUID.randomUUID(), productId, accountId, null))
        .getId();
  }

  private Map<String, Object> line(Object... keysAndValues) {
    Map<String, Object> line = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      line.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return line;
  }

  private Map<String, Object> confirmation(String taxes, Map<String, Object>... lines) {
    Map<String, Object> confirmation = new LinkedHashMap<>();
    confirmation.put("taxes", taxes);
    confirmation.put("lines", List.of(lines));
    return confirmation;
  }

  /** A confirmation body; {@code cashAccountId}/{@code investmentAccountId} unlabeled by F027. */
  private Map<String, Object> body(
      UUID cashAccountId, UUID investmentAccountId, Map<String, Object> tradeConfirmation) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", "2026-03-15");
    body.put("description", "Trade");
    body.put("cashAccountId", cashAccountId.toString());
    body.put("investmentAccountId", investmentAccountId.toString());
    body.put("tradeConfirmation", tradeConfirmation);
    return body;
  }

  private Map<String, Object> plainBody(UUID from, UUID to, String amount) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", "2026-03-15");
    body.put("amount", amount);
    body.put("fromAccountId", from.toString());
    body.put("toAccountId", to.toString());
    body.put("description", "Transfer");
    return body;
  }

  private ResultActions create(Map<String, Object> body) throws Exception {
    return mockMvc.perform(
        post("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(body)));
  }

  private ResultActions edit(String id, Map<String, Object> body) throws Exception {
    return mockMvc.perform(
        patch("/api/transfers/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(body)));
  }

  // --- create -----------------------------------------------------------------------------------

  @Test
  void aSingleLineBuyDerivesAmountAndDirectionAndReturnsTheConfirmation() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "5.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "10",
                        "unitPrice", "100.00"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fromAccountId").value(checkingId.toString()))
        .andExpect(jsonPath("$.toAccountId").value(brokerId.toString()))
        .andExpect(jsonPath("$.amount").value(1005.00))
        .andExpect(jsonPath("$.taxes").value(5.00))
        .andExpect(jsonPath("$.tradeConfirmation.lines[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.tradeConfirmation.lines[0].side").value("BUY"))
        .andExpect(jsonPath("$.tradeConfirmation.lines[0].quantity").value(10))
        .andExpect(jsonPath("$.tradeConfirmation.lines[0].unitPrice").value(100.00));
  }

  @Test
  void aSingleLineSellDerivesTheOppositeDirection() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "SELL",
                        "quantity", "10",
                        "unitPrice", "100.00"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fromAccountId").value(brokerId.toString()))
        .andExpect(jsonPath("$.toAccountId").value(checkingId.toString()))
        .andExpect(jsonPath("$.amount").value(1000.00));
  }

  @Test
  void mixedBuyAndSellLinesAcrossTwoProductsNetTogether() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "10.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "10",
                        "unitPrice", "100.00"),
                    line(
                        "productId", otherProductId.toString(),
                        "side", "SELL",
                        "quantity", "5",
                        "unitPrice", "50.00"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(760.00))
        .andExpect(jsonPath("$.tradeConfirmation.lines.length()").value(2));
  }

  @Test
  void aPlainTransferStillWorksUnchanged() throws Exception {
    create(plainBody(checkingId, savingsId, "10.00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tradeConfirmation").doesNotExist())
        .andExpect(jsonPath("$.taxes").doesNotExist());
  }

  @Test
  void neitherShapeNorBothShapesIsRejectedWith400() throws Exception {
    Map<String, Object> neither = new LinkedHashMap<>();
    neither.put("date", "2026-03-15");
    neither.put("description", "Nothing");
    create(neither).andExpect(status().isBadRequest());

    Map<String, Object> both = body(checkingId, brokerId, confirmation("0.00"));
    both.put("fromAccountId", checkingId.toString());
    both.put("toAccountId", brokerId.toString());
    both.put("amount", "10.00");
    create(both).andExpect(status().isBadRequest());
  }

  @Test
  void aNetZeroSettlementIsRejectedWith400() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "10",
                        "unitPrice", "100.00"),
                    line(
                        "productId", otherProductId.toString(),
                        "side", "SELL",
                        "quantity", "10",
                        "unitPrice", "100.00"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void closeHoldingOnABuyLineIsRejectedWith400() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "10",
                        "unitPrice", "100.00",
                        "closeHolding", true))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void twoLinesOfTheSameProductBothCarryingAResultingBalanceIsRejectedWith400() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "5",
                        "unitPrice", "10.00",
                        "resultingBalance", "50.00"),
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "5",
                        "unitPrice", "10.00",
                        "resultingBalance", "100.00"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aProductWithNoHoldingInThatAccountIsRejectedWith404() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", otherBrokerProductId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void aClosedHoldingIsRejectedWith409() throws Exception {
    mockMvc
        .perform(post("/api/investment-holdings/" + holdingId + "/close"))
        .andExpect(status().isOk());

    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isConflict());
  }

  @Test
  void anInvestmentAccountThatIsNotActuallyInvestmentIsRejectedWith409() throws Exception {
    create(
            body(
                checkingId,
                savingsId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isConflict());
  }

  @Test
  void resultingBalanceOnOneLineRecordsASnapshotForItsHoldingOnly() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1000.00",
                        "resultingBalance", "1000.00"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2026-03-15"))
        .andExpect(jsonPath("$[0].balance").value(1000.00));
    mockMvc
        .perform(get("/api/investment-holdings/" + otherHoldingId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void closeHoldingOnASellLineClosesItWhenItsLatestSnapshotIsZero() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "SELL",
                        "quantity", "1",
                        "unitPrice", "1.00",
                        "resultingBalance", "0",
                        "closeHolding", true))))
        .andExpect(status().isCreated());

    // Closing always uses "today" (InvestmentHoldingService's Clock), not the trade's own date.
    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.closedDate").exists());
  }

  @Test
  void aBuyWithoutAResultingBalanceLeavesTheHoldingNeedingASnapshot() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.needsSnapshot").value(true));
  }

  // --- edit, filter ---------------------------------------------------------------------------

  @Test
  void editReplacesTheLinesAndRecomputesAmount() throws Exception {
    String id =
        JsonSupport.idOf(
            create(
                    body(
                        checkingId,
                        brokerId,
                        confirmation(
                            "0.00",
                            line(
                                "productId", productId.toString(),
                                "side", "BUY",
                                "quantity", "10",
                                "unitPrice", "100.00"))))
                .andReturn());

    edit(
            id,
            body(
                checkingId,
                brokerId,
                confirmation(
                    "5.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "2.5",
                        "unitPrice", "400.00"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(1005.00))
        .andExpect(jsonPath("$.tradeConfirmation.lines[0].quantity").value(2.5));
  }

  @Test
  void editToAnUnknownHoldingIsRejectedWith404() throws Exception {
    String id =
        JsonSupport.idOf(
            create(
                    body(
                        checkingId,
                        brokerId,
                        confirmation(
                            "0.00",
                            line(
                                "productId", productId.toString(),
                                "side", "BUY",
                                "quantity", "1",
                                "unitPrice", "1.00"))))
                .andReturn());

    edit(
            id,
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", otherBrokerProductId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void editCanClearATradeConfirmationBackToAPlainTransfer() throws Exception {
    String id =
        JsonSupport.idOf(
            create(
                    body(
                        checkingId,
                        brokerId,
                        confirmation(
                            "0.00",
                            line(
                                "productId", productId.toString(),
                                "side", "BUY",
                                "quantity", "1",
                                "unitPrice", "1.00"))))
                .andReturn());

    edit(id, plainBody(checkingId, savingsId, "10.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tradeConfirmation").doesNotExist());
  }

  @Test
  void listFiltersByInvestmentProductIdViaTheLinesJoin() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isCreated());
    create(
            body(
                checkingId,
                brokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", productId.toString(),
                        "side", "SELL",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isCreated());
    create(plainBody(checkingId, savingsId, "10.00")).andExpect(status().isCreated());
    create(
            body(
                checkingId,
                otherBrokerId,
                confirmation(
                    "0.00",
                    line(
                        "productId", otherBrokerProductId.toString(),
                        "side", "BUY",
                        "quantity", "1",
                        "unitPrice", "1.00"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/transfers").param("investmentProductId", productId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }
}
