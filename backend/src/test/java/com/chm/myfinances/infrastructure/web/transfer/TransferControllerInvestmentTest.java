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
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * REST-layer tests for the F009 additions to {@link TransferController}, rewired onto holdings by
 * F022/ADR 0020: buys/sells as tagged transfers with record-only trade details (including an
 * 8-decimal quantity/price round trip), the optional {@code resultingBalance} snapshot, the {@code
 * 400}/{@code 409}/{@code 404} split between request shape, closed-holding and missing-holding
 * rules, and the {@code investmentProductId} list filter. Snapshot/close/needsSnapshot now live on
 * the holding endpoints.
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
    otherBrokerProductId = product("Other Product Trade Web Test");
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

  /** A transfer body; {@code extra} entries (trade fields, resultingBalance) are merged in. */
  private Map<String, Object> body(UUID from, UUID to, Map<String, Object> extra) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", "2026-03-15");
    body.put("amount", "1005.00");
    body.put("fromAccountId", from.toString());
    body.put("toAccountId", to.toString());
    body.put("description", "Trade");
    body.putAll(extra);
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

  private Map<String, Object> trade(Object... keysAndValues) {
    Map<String, Object> extra = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      extra.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return extra;
  }

  // --- create ---------------------------------------------------------------------------------

  @Test
  void aBuyReturnsTheProductAndTradeDetails() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                trade(
                    "investmentProductId", productId.toString(),
                    "quantity", "10",
                    "unitPrice", "100.00",
                    "taxes", "5.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.investmentProductId").value(productId.toString()))
        .andExpect(jsonPath("$.quantity").value(10))
        .andExpect(jsonPath("$.unitPrice").value(100.00))
        .andExpect(jsonPath("$.taxes").value(5.00))
        .andExpect(jsonPath("$.amount").value(1005.00));
  }

  @Test
  void aSellBackToCheckingIsATaggedTransferOutOfTheInvestmentAccount() throws Exception {
    create(body(brokerId, checkingId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fromAccountId").value(brokerId.toString()))
        .andExpect(jsonPath("$.investmentProductId").value(productId.toString()))
        .andExpect(jsonPath("$.quantity").doesNotExist());
  }

  @Test
  void aPlainTransferHasNullTradeFields() throws Exception {
    create(body(checkingId, savingsId, Map.of()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.investmentProductId").doesNotExist())
        .andExpect(jsonPath("$.quantity").doesNotExist())
        .andExpect(jsonPath("$.unitPrice").doesNotExist())
        .andExpect(jsonPath("$.taxes").doesNotExist());
  }

  @Test
  void eightDecimalQuantityAndPriceRoundTripThroughCreateAndGet() throws Exception {
    String id =
        JsonSupport.idOf(
            create(
                    body(
                        checkingId,
                        brokerId,
                        trade(
                            "investmentProductId", productId.toString(),
                            "quantity", "0.12345678",
                            "unitPrice", "250000.87654321")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(0.12345678))
                .andExpect(jsonPath("$.unitPrice").value(250000.87654321))
                .andReturn());

    mockMvc
        .perform(get("/api/transfers/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(0.12345678))
        .andExpect(jsonPath("$.unitPrice").value(250000.87654321));
  }

  @Test
  void resultingBalanceRecordsASnapshotDatedTheTransferDate() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", productId.toString(), "resultingBalance", "1000.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2026-03-15"))
        .andExpect(jsonPath("$[0].balance").value(1000.00));
    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.needsSnapshot").value(false));
  }

  @Test
  void resultingBalanceReplacesASameDaySnapshotAndZeroRecordsAFullSell() throws Exception {
    mockMvc.perform(
        post("/api/investment-holdings/" + holdingId + "/snapshots")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonSupport.toJson(Map.of("date", "2026-03-15", "balance", "50.00"))));

    create(
            body(
                brokerId,
                checkingId,
                trade("investmentProductId", productId.toString(), "resultingBalance", "0")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].balance").value(0));
    // The position is now empty, so the holding can be closed.
    mockMvc
        .perform(post("/api/investment-holdings/" + holdingId + "/close"))
        .andExpect(status().isOk());
  }

  @Test
  void aBuyWithoutAResultingBalanceLeavesTheHoldingNeedingASnapshot() throws Exception {
    create(body(checkingId, brokerId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.needsSnapshot").value(true));
    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void tradeFieldsWithoutAProductAreRejectedWith400() throws Exception {
    create(body(checkingId, savingsId, trade("quantity", "1", "unitPrice", "1")))
        .andExpect(status().isBadRequest());
    create(body(checkingId, savingsId, trade("taxes", "1.00"))).andExpect(status().isBadRequest());
    create(body(checkingId, savingsId, trade("resultingBalance", "1.00")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void quantityWithoutUnitPriceOrTheReverseIsRejectedWith400() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", productId.toString(), "quantity", "1")))
        .andExpect(status().isBadRequest());
    create(
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", productId.toString(), "unitPrice", "1")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonPositiveQuantityOrPriceAndNegativeTaxesOrBalanceAreRejectedWith400() throws Exception {
    String product = productId.toString();
    create(
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", product, "quantity", "0", "unitPrice", "1")))
        .andExpect(status().isBadRequest());
    create(
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", product, "quantity", "1", "unitPrice", "-1")))
        .andExpect(status().isBadRequest());
    create(body(checkingId, brokerId, trade("investmentProductId", product, "taxes", "-0.01")))
        .andExpect(status().isBadRequest());
    create(
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", product, "resultingBalance", "-1")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void moreThanEightDecimalsOnQuantityIsRejectedWith400() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                trade(
                    "investmentProductId", productId.toString(),
                    "quantity", "0.123456789",
                    "unitPrice", "1")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anInvestmentEndpointWithoutAProductIsRejectedWith409() throws Exception {
    create(body(checkingId, brokerId, Map.of())).andExpect(status().isConflict());
  }

  @Test
  void aProductWithNoHoldingInThatAccountIsRejectedWith404() throws Exception {
    create(
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", otherBrokerProductId.toString())))
        .andExpect(status().isNotFound());
  }

  @Test
  void aProductWithoutAnInvestmentEndpointIsRejectedWith409() throws Exception {
    create(body(checkingId, savingsId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isConflict());
  }

  @Test
  void twoInvestmentEndpointsAreRejectedWith409() throws Exception {
    create(body(brokerId, otherBrokerId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isConflict());
  }

  @Test
  void aClosedHoldingIsRejectedWith409AndAnUnknownProductWith404() throws Exception {
    mockMvc
        .perform(post("/api/investment-holdings/" + holdingId + "/close"))
        .andExpect(status().isOk());

    create(body(checkingId, brokerId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isConflict());
    create(body(checkingId, brokerId, trade("investmentProductId", UUID.randomUUID().toString())))
        .andExpect(status().isNotFound());
  }

  // --- edit, filter ---------------------------------------------------------------------------

  @Test
  void editReplacesTheTagAndTradeDetailsAndNeverTouchesSnapshots() throws Exception {
    String id =
        JsonSupport.idOf(
            create(
                    body(
                        checkingId,
                        brokerId,
                        trade(
                            "investmentProductId",
                            productId.toString(),
                            "resultingBalance",
                            "1000.00")))
                .andReturn());

    edit(
            id,
            body(
                checkingId,
                brokerId,
                trade(
                    "investmentProductId", productId.toString(),
                    "quantity", "2.5",
                    "unitPrice", "400.00",
                    "taxes", "5.00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(2.5))
        .andExpect(jsonPath("$.unitPrice").value(400.00));

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId + "/snapshots"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].balance").value(1000.00));
  }

  @Test
  void editRejectsTheSameCasesAsCreate() throws Exception {
    String id = JsonSupport.idOf(create(body(checkingId, savingsId, Map.of())).andReturn());

    edit(id, body(checkingId, savingsId, trade("quantity", "1", "unitPrice", "1")))
        .andExpect(status().isBadRequest());
    edit(id, body(checkingId, brokerId, Map.of())).andExpect(status().isConflict());
    edit(
            id,
            body(
                checkingId,
                brokerId,
                trade("investmentProductId", otherBrokerProductId.toString())))
        .andExpect(status().isNotFound());
    edit(id, body(checkingId, savingsId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isConflict());
  }

  @Test
  void listFiltersByInvestmentProductId() throws Exception {
    create(body(checkingId, brokerId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isCreated());
    create(body(brokerId, checkingId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isCreated());
    create(body(checkingId, savingsId, Map.of())).andExpect(status().isCreated());
    create(
            body(
                checkingId,
                otherBrokerId,
                trade("investmentProductId", otherBrokerProductId.toString())))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/transfers").param("investmentProductId", productId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].investmentProductId").value(productId.toString()))
        .andExpect(jsonPath("$.content[1].investmentProductId").value(productId.toString()));
  }

  @Test
  void aTradeMakesTheHoldingNeedASnapshotUntilOneIsRecordedOnOrAfterTheTradeDate()
      throws Exception {
    create(body(checkingId, brokerId, trade("investmentProductId", productId.toString())))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.needsSnapshot").value(true));

    mockMvc.perform(
        post("/api/investment-holdings/" + holdingId + "/snapshots")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                JsonSupport.toJson(
                    Map.of("date", LocalDate.of(2026, 3, 16).toString(), "balance", "1005.00"))));

    mockMvc
        .perform(get("/api/investment-holdings/" + holdingId))
        .andExpect(jsonPath("$.needsSnapshot").value(false));
  }
}
