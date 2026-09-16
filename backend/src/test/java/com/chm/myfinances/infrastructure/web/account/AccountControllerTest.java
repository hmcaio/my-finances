package com.chm.myfinances.infrastructure.web.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link AccountController}, against a real Testcontainers Postgres
 * (ADR 0010). Hand-built {@link MockMvc} - Spring Boot 4.x removed
 * {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} - same pattern as F002's {@code
 * CategoryControllerTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  private String createAccount(
      String name, String type, String openingBalance, String openingBalanceDate) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", name,
                "institution", "Some Bank",
                "type", type,
                "openingBalance", openingBalance,
                "openingBalanceDate", openingBalanceDate));
    MvcResult result =
        mockMvc
            .perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void createReturnsAccountWithOpeningBalanceAsBalance() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", "Itau Checking",
                "institution", "Itau",
                "type", "CHECKING",
                "openingBalance", "150.75",
                "openingBalanceDate", "2026-01-01"));

    mockMvc
        .perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Itau Checking"))
        .andExpect(jsonPath("$.institution").value("Itau"))
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
    mockMvc
        .perform(get("/api/accounts/" + java.util.UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void editUpdatesNameAndInstitutionOnly() throws Exception {
    String id = createAccount("Original", "CHECKING", "10.00", "2026-01-01");

    String patchBody =
        objectMapper.writeValueAsString(Map.of("name", "Renamed", "institution", "New Bank"));
    mockMvc
        .perform(
            patch("/api/accounts/" + id).contentType(MediaType.APPLICATION_JSON).content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.institution").value("New Bank"))
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
                "institution",
                "Bank",
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
  void createRejectsNameOverMaxLength() throws Exception {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", tooLongName,
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
}
