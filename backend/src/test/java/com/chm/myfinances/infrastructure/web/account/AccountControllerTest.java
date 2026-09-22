package com.chm.myfinances.infrastructure.web.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.JsonSupport;
import com.chm.myfinances.testsupport.MockMvcSupport;
import com.chm.myfinances.testsupport.TestInstitutions;
import com.chm.myfinances.testsupport.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link AccountController}, against a real Testcontainers Postgres
 * (ADR 0010). Hand-built {@link MockMvc} - Spring Boot 4.x removed
 * {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} - same pattern as F002's {@code
 * CategoryControllerTest}.
 */
@WebIntegrationTest
class AccountControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;
  @Autowired private InvestmentProductRepository investmentProductRepository;

  /** Sentinel: leave the institutionId field out of the request body entirely. */
  private static final Object OMIT = new Object();

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;
  private UUID institutionId;
  private UUID otherInstitutionId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    institutionId =
        institutionRepository.save(Institution.create(UUID.randomUUID(), "Itau Test")).getId();
    otherInstitutionId =
        institutionRepository.save(Institution.create(UUID.randomUUID(), "New Bank Test")).getId();
  }

  private String createAccount(
      String name, String type, String openingBalance, String openingBalanceDate) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", name,
                "institutionId", institutionId.toString(),
                "type", type,
                "openingBalance", openingBalance,
                "openingBalanceDate", openingBalanceDate));
    MvcResult result =
        mockMvc
            .perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonSupport.idOf(result);
  }

  @Test
  void createReturnsAccountWithOpeningBalanceAsBalance() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", "Itau Checking",
                "institutionId", institutionId.toString(),
                "type", "CHECKING",
                "openingBalance", "150.75",
                "openingBalanceDate", "2026-01-01"));

    mockMvc
        .perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Itau Checking"))
        .andExpect(jsonPath("$.institutionId").value(institutionId.toString()))
        .andExpect(jsonPath("$.type").value("CHECKING"))
        .andExpect(jsonPath("$.openingBalance").value(150.75))
        .andExpect(jsonPath("$.balance").value(150.75))
        .andExpect(jsonPath("$.closed").value(false))
        .andExpect(jsonPath("$.closedDate").doesNotExist());
  }

  @Test
  void listExcludesClosedAccountsByDefaultButIncludesWhenRequested() throws Exception {
    String openId = createAccount("Open Acct", "CHECKING", "10.00", "2026-01-01");
    String closedId = createAccount("Closed Acct", "SAVINGS", "20.00", "2026-01-01");
    mockMvc.perform(post("/api/accounts/" + closedId + "/close")).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/accounts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + openId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + closedId + "')]").doesNotExist());

    mockMvc
        .perform(get("/api/accounts").param("includeClosed", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + openId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + closedId + "')]").exists());
  }

  @Test
  void getReturnsAccountDetailWithBalance() throws Exception {
    String id = createAccount("Savings", "SAVINGS", "500.00", "2026-02-01");

    mockMvc
        .perform(get("/api/accounts/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.balance").value(500.00));
  }

  @Test
  void getOfUnknownIdReturns404() throws Exception {
    mockMvc.perform(get("/api/accounts/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void editUpdatesNameAndInstitutionOnly() throws Exception {
    String id = createAccount("Original", "CHECKING", "10.00", "2026-01-01");

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of("name", "Renamed", "institutionId", otherInstitutionId.toString()));
    mockMvc
        .perform(
            patch("/api/accounts/" + id).contentType(MediaType.APPLICATION_JSON).content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.institutionId").value(otherInstitutionId.toString()))
        .andExpect(jsonPath("$.openingBalance").value(10.00));
  }

  @Test
  void editWithTypeOrOpeningBalanceFieldsDoesNotChangeThem() throws Exception {
    String id = createAccount("Original", "CHECKING", "10.00", "2026-01-01");

    // UpdateAccountRequest has no type/openingBalance fields - sending them can't change the
    // account through this endpoint, whatever Jackson's unknown-property handling does.
    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "name",
                "Renamed",
                "institutionId",
                institutionId.toString(),
                "type",
                "SAVINGS",
                "openingBalance",
                "999.00"));
    mockMvc
        .perform(
            patch("/api/accounts/" + id).contentType(MediaType.APPLICATION_JSON).content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("CHECKING"))
        .andExpect(jsonPath("$.openingBalance").value(10.00));
  }

  @Test
  void closeSetsClosedDate() throws Exception {
    String id = createAccount("To Close", "CHECKING", "10.00", "2026-01-01");

    mockMvc
        .perform(post("/api/accounts/" + id + "/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true))
        .andExpect(jsonPath("$.closedDate").exists());
  }

  @Test
  void closedAccountStaysViewableAndBrowsableAfterClosing() throws Exception {
    // F003 plan.md's verification bullet: closing an account removes it from the default list
    // but it must stay viewable/browsable with its history-to-date preserved.
    String id = createAccount("To Close", "CHECKING", "10.00", "2026-01-01");
    mockMvc.perform(post("/api/accounts/" + id + "/close")).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/accounts/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true))
        .andExpect(jsonPath("$.openingBalance").value(10.00));
  }

  @Test
  void closingAnAlreadyClosedAccountReturns409() throws Exception {
    String id = createAccount("To Close", "CHECKING", "10.00", "2026-01-01");
    mockMvc.perform(post("/api/accounts/" + id + "/close")).andExpect(status().isOk());

    mockMvc.perform(post("/api/accounts/" + id + "/close")).andExpect(status().isConflict());
  }

  @Test
  void createRejectsADuplicateNameWith409() throws Exception {
    createAccount("Duplicate Account", "CHECKING", "10.00", "2026-01-01");
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", "Duplicate Account",
                "institutionId", institutionId.toString(),
                "type", "SAVINGS",
                "openingBalance", "20.00",
                "openingBalanceDate", "2026-01-01"));

    mockMvc
        .perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void editRejectsADuplicateNameWith409() throws Exception {
    createAccount("Original Account", "CHECKING", "10.00", "2026-01-01");
    String id = createAccount("Account To Rename", "SAVINGS", "10.00", "2026-01-01");

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of("name", "Original Account", "institutionId", institutionId.toString()));
    mockMvc
        .perform(
            patch("/api/accounts/" + id).contentType(MediaType.APPLICATION_JSON).content(patchBody))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsNameOverMaxLength() throws Exception {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", tooLongName,
                "institutionId", institutionId.toString(),
                "type", "CHECKING",
                "openingBalance", "10.00",
                "openingBalanceDate", "2026-01-01"));

    mockMvc
        .perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsMissingRequiredFields() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("name", "Missing Fields"));

    mockMvc
        .perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  private String createAccountBody(Object institutionId) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Institution Check Account");
    body.put("type", "CHECKING");
    body.put("openingBalance", "10.00");
    body.put("openingBalanceDate", "2026-01-01");
    if (institutionId != OMIT) {
      body.put("institutionId", institutionId);
    }
    return objectMapper.writeValueAsString(body);
  }

  @Test
  void createAcceptsTheBuiltInInstitution() throws Exception {
    UUID builtInId = TestInstitutions.builtInId(institutionRepository);

    mockMvc
        .perform(
            post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAccountBody(builtInId.toString())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.institutionId").value(builtInId.toString()));
  }

  @Test
  void createRejectsAMissingInstitutionIdWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAccountBody(OMIT)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsANullInstitutionIdWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAccountBody(null)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsAMalformedInstitutionIdWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAccountBody("not-a-uuid")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsAnUnknownInstitutionIdWith404() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createAccountBody(UUID.randomUUID().toString())))
        .andExpect(status().isNotFound());
  }

  @Test
  void editRejectsAMissingOrNullInstitutionIdWith400() throws Exception {
    String id = createAccount("Edit Institution Check", "CHECKING", "10.00", "2026-01-01");

    mockMvc
        .perform(
            patch("/api/accounts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed"))))
        .andExpect(status().isBadRequest());

    Map<String, Object> withNull = new HashMap<>();
    withNull.put("name", "Renamed");
    withNull.put("institutionId", null);
    mockMvc
        .perform(
            patch("/api/accounts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withNull)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editRejectsAMalformedInstitutionIdWith400() throws Exception {
    String id = createAccount("Edit Malformed Check", "CHECKING", "10.00", "2026-01-01");

    mockMvc
        .perform(
            patch("/api/accounts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Renamed", "institutionId", "not-a-uuid"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editRejectsAnUnknownInstitutionIdWith404() throws Exception {
    String id = createAccount("Edit Unknown Check", "CHECKING", "10.00", "2026-01-01");

    mockMvc
        .perform(
            patch("/api/accounts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Renamed", "institutionId", UUID.randomUUID().toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void responsesCarryTheInstitutionIdOnListAndGet() throws Exception {
    String id = createAccount("Institution On Get", "CHECKING", "10.00", "2026-01-01");

    mockMvc
        .perform(get("/api/accounts/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.institutionId").value(institutionId.toString()));
    mockMvc
        .perform(get("/api/accounts"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id=='" + id + "')].institutionId").value(institutionId.toString()));
  }

  // --- F008: INVESTMENT accounts ---

  private String investmentAccountBody(String name, Map<String, Object> extra) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("institutionId", institutionId.toString());
    body.put("type", "INVESTMENT");
    body.putAll(extra);
    return objectMapper.writeValueAsString(body);
  }

  private String createInvestmentAccount(String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(investmentAccountBody(name, Map.of())))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonSupport.idOf(result);
  }

  @Test
  void createsAnInvestmentAccountWithoutOpeningFieldsAndABalanceOfZero() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(investmentAccountBody("XP Investimentos", Map.of())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("INVESTMENT"))
        .andExpect(jsonPath("$.openingBalance").doesNotExist())
        .andExpect(jsonPath("$.openingBalanceDate").doesNotExist())
        .andExpect(jsonPath("$.balance").value(0));
  }

  @Test
  void createRejectsOpeningFieldsOnAnInvestmentAccountWith400() throws Exception {
    for (Map<String, Object> extra :
        List.<Map<String, Object>>of(
            Map.of("openingBalance", "10.00", "openingBalanceDate", "2026-01-01"),
            Map.of("openingBalance", "10.00"),
            Map.of("openingBalanceDate", "2026-01-01"))) {
      mockMvc
          .perform(
              post("/api/accounts")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(investmentAccountBody("Broker With Values", extra)))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void createRejectsMissingOpeningFieldsOnANonInvestmentAccountWith400() throws Exception {
    for (Map<String, Object> extra :
        List.<Map<String, Object>>of(
            Map.of(),
            Map.of("openingBalance", "10.00"),
            Map.of("openingBalanceDate", "2026-01-01"))) {
      Map<String, Object> body = new HashMap<>();
      body.put("name", "Checking Without Values");
      body.put("institutionId", institutionId.toString());
      body.put("type", "CHECKING");
      body.putAll(extra);
      mockMvc
          .perform(
              post("/api/accounts")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void anInvestmentAccountListsAndFetchesWithNullOpeningFields() throws Exception {
    String id = createInvestmentAccount("XP List Test");

    mockMvc
        .perform(get("/api/accounts/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingBalance").doesNotExist())
        .andExpect(jsonPath("$.balance").value(0));
    mockMvc
        .perform(get("/api/accounts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + id + "')].type").value("INVESTMENT"));
  }

  @Test
  void closingAnInvestmentAccountWithAnOpenProductIsRejectedUntilTheProductIsClosed()
      throws Exception {
    String accountId = createInvestmentAccount("XP Close Test");
    UUID categoryId =
        investmentCategoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Crypto Close Test"))
            .getId();
    UUID productId =
        investmentProductRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(),
                    UUID.fromString(accountId),
                    categoryId,
                    null,
                    "Bitcoin Test"))
            .getId();

    mockMvc.perform(post("/api/accounts/" + accountId + "/close")).andExpect(status().isConflict());

    mockMvc
        .perform(post("/api/investment-products/" + productId + "/close"))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/accounts/" + accountId + "/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true));
  }
}
