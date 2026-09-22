package com.chm.myfinances.infrastructure.web.investmentproduct;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.chm.myfinances.domain.investmentproduct.HasInvestmentHistoryChecker;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.JsonSupport;
import com.chm.myfinances.testsupport.MockMvcSupport;
import com.chm.myfinances.testsupport.TestFixtures;
import com.chm.myfinances.testsupport.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link InvestmentProductController}, against a real
 * Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc}. The history checker is mocked
 * (default {@code false}, like F008's placeholder) so the history-dependent {@code 409} on delete
 * can be exercised before F009 supplies real history.
 */
@WebIntegrationTest
class InvestmentProductControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;

  /** F009 supplies the real answer; a mock stands in for products with snapshots or trades. */
  @MockitoBean private HasInvestmentHistoryChecker historyChecker;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;
  private UUID accountId;
  private UUID otherAccountId;
  private UUID checkingId;
  private UUID fixedIncomeId;
  private UUID cryptoId;
  private UUID cdbId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    accountId = saveAccount("XP Product Test", AccountType.INVESTMENT);
    otherAccountId = saveAccount("Nubank Product Test", AccountType.INVESTMENT);
    checkingId = saveAccount("Checking Product Test", AccountType.CHECKING);
    fixedIncomeId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Fixed Income Product Test"))
            .getId();
    cryptoId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Crypto Product Test"))
            .getId();
    cdbId =
        subcategoryRepository
            .save(
                InvestmentSubcategory.create(UUID.randomUUID(), fixedIncomeId, "CDB Product Test"))
            .getId();
  }

  private UUID saveAccount(String name, AccountType type) {
    return TestFixtures.account(accountRepository, institutionRepository, name, type).getId();
  }

  private String body(UUID account, UUID category, UUID subcategory, String name) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("accountId", account == null ? null : account.toString());
    body.put("investmentCategoryId", category == null ? null : category.toString());
    body.put("investmentSubcategoryId", subcategory == null ? null : subcategory.toString());
    body.put("name", name);
    return objectMapper.writeValueAsString(body);
  }

  private String createProduct(UUID account, UUID category, UUID subcategory, String name)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/investment-products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(account, category, subcategory, name)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonSupport.idOf(result);
  }

  @Test
  void createReturnsTheProductWithHasHistoryFalse() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, fixedIncomeId, cdbId, "CDB 110% Test")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(accountId.toString()))
        .andExpect(jsonPath("$.investmentCategoryId").value(fixedIncomeId.toString()))
        .andExpect(jsonPath("$.investmentSubcategoryId").value(cdbId.toString()))
        .andExpect(jsonPath("$.name").value("CDB 110% Test"))
        .andExpect(jsonPath("$.closed").value(false))
        .andExpect(jsonPath("$.closedDate").doesNotExist())
        .andExpect(jsonPath("$.hasHistory").value(false));
  }

  @Test
  void aCategoryOnlyProductSaves() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, cryptoId, null, "Bitcoin Test")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.investmentSubcategoryId").doesNotExist());
  }

  private static Stream<String> missingRequiredFieldsAndOverlongNameCases() {
    return Stream.of(
        "missing accountId",
        "missing investmentCategoryId",
        "blank name",
        "name over the length limit",
        "empty body");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("missingRequiredFieldsAndOverlongNameCases")
  void createRejectsMissingRequiredFieldsAndOverlongNameWith400(String caseName) throws Exception {
    String content =
        switch (caseName) {
          case "missing accountId" -> body(null, cryptoId, null, "Bitcoin Test");
          case "missing investmentCategoryId" -> body(accountId, null, null, "Bitcoin Test");
          case "blank name" -> body(accountId, cryptoId, null, " ");
          case "name over the length limit" ->
              body(accountId, cryptoId, null, "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1));
          case "empty body" -> "{}";
          default -> throw new IllegalArgumentException(caseName);
        };

    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().isBadRequest());
  }

  private static Stream<String> unknownReferenceCases() {
    return Stream.of(
        "unknown accountId", "unknown investmentCategoryId", "unknown investmentSubcategoryId");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unknownReferenceCases")
  void createRejectsUnknownReferencesWith404(String caseName) throws Exception {
    String content =
        switch (caseName) {
          case "unknown accountId" -> body(UUID.randomUUID(), cryptoId, null, "Bitcoin Test");
          case "unknown investmentCategoryId" ->
              body(accountId, UUID.randomUUID(), null, "Bitcoin Test");
          case "unknown investmentSubcategoryId" ->
              body(accountId, fixedIncomeId, UUID.randomUUID(), "Bitcoin Test");
          default -> throw new IllegalArgumentException(caseName);
        };

    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRejectsANonInvestmentAccountWith409() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(checkingId, cryptoId, null, "Bitcoin Test")))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsASubcategoryOfAnotherCategoryWith409() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, cryptoId, cdbId, "Bitcoin Test")))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsADuplicateNameInTheSameAccountButNotInAnother() throws Exception {
    createProduct(accountId, cryptoId, null, "Bitcoin Test");

    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, cryptoId, null, "Bitcoin Test")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/investment-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(otherAccountId, cryptoId, null, "Bitcoin Test")))
        .andExpect(status().isCreated());
  }

  @Test
  void listFiltersByAccountId() throws Exception {
    String atXp = createProduct(accountId, cryptoId, null, "Bitcoin Test");
    String atNu = createProduct(otherAccountId, cryptoId, null, "Bitcoin Test");

    mockMvc
        .perform(get("/api/investment-products").param("accountId", accountId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + atXp + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + atNu + "')]").doesNotExist())
        .andExpect(jsonPath("$[0].hasHistory").value(false));
    mockMvc
        .perform(get("/api/investment-products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + atXp + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + atNu + "')]").exists());
  }

  @Test
  void listWithAMalformedAccountIdReturns400() throws Exception {
    mockMvc
        .perform(get("/api/investment-products").param("accountId", "not-a-uuid"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getReturnsTheDetailWithHasHistory() throws Exception {
    String id = createProduct(accountId, fixedIncomeId, cdbId, "CDB Test");

    mockMvc
        .perform(get("/api/investment-products/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.hasHistory").value(false));
  }

  @Test
  void getOfUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(get("/api/investment-products/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void patchReclassifiesAndRenames() throws Exception {
    String id = createProduct(accountId, fixedIncomeId, cdbId, "CDB Test");

    mockMvc
        .perform(
            patch("/api/investment-products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, cryptoId, null, "Bitcoin Test")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.investmentCategoryId").value(cryptoId.toString()))
        .andExpect(jsonPath("$.investmentSubcategoryId").doesNotExist())
        .andExpect(jsonPath("$.name").value("Bitcoin Test"));
  }

  @Test
  void patchRunsTheSameChecksAsCreate() throws Exception {
    String id = createProduct(accountId, fixedIncomeId, cdbId, "CDB Test");
    createProduct(accountId, cryptoId, null, "Bitcoin Test");

    mockMvc
        .perform(
            patch("/api/investment-products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, cryptoId, cdbId, "CDB Test")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            patch("/api/investment-products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, fixedIncomeId, cdbId, "Bitcoin Test")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            patch("/api/investment-products/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, cryptoId, null, "Anything Test")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            patch("/api/investment-products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accountId, null, null, "CDB Test")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void closeSetsTheClosedDateAndASecondCloseIs409() throws Exception {
    String id = createProduct(accountId, cryptoId, null, "Bitcoin Test");

    mockMvc
        .perform(post("/api/investment-products/" + id + "/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true))
        .andExpect(jsonPath("$.closedDate").exists());
    mockMvc
        .perform(post("/api/investment-products/" + id + "/close"))
        .andExpect(status().isConflict());
    mockMvc
        .perform(post("/api/investment-products/" + UUID.randomUUID() + "/close"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteSucceedsWithZeroHistoryAndThenTheProductIsGone() throws Exception {
    String id = createProduct(accountId, cryptoId, null, "Bitcoin Test");

    mockMvc.perform(delete("/api/investment-products/" + id)).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/investment-products/" + id)).andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/investment-products/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void aProductWithHistoryReportsItAndRefusesDeleteButCanStillBeClosed() throws Exception {
    String id = createProduct(accountId, cryptoId, null, "Bitcoin Test");
    given(historyChecker.hasHistory(UUID.fromString(id))).willReturn(true);

    mockMvc
        .perform(get("/api/investment-products/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasHistory").value(true));
    mockMvc.perform(delete("/api/investment-products/" + id)).andExpect(status().isConflict());
    mockMvc
        .perform(post("/api/investment-products/" + id + "/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true))
        .andExpect(jsonPath("$.hasHistory").value(true));
  }
}
