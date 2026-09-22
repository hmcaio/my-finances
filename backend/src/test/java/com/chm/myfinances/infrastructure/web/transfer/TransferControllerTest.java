package com.chm.myfinances.infrastructure.web.transfer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.JsonSupport;
import com.chm.myfinances.testsupport.MockMvcSupport;
import com.chm.myfinances.testsupport.TestFixtures;
import com.chm.myfinances.testsupport.TestInstitutions;
import com.chm.myfinances.testsupport.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * REST-layer integration test for {@link TransferController}, against a real Testcontainers
 * Postgres (ADR 0010). Hand-built {@link MockMvc} - Spring Boot 4.x removed
 * {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} - same pattern as F004's {@code
 * TransactionControllerTest}.
 */
@WebIntegrationTest
class TransferControllerTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  private UUID checkingId;
  private UUID savingsId;
  private UUID creditCardId;
  private UUID closedAccountId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);

    checkingId = persistAccount("Checking", AccountType.CHECKING).getId();
    savingsId = persistAccount("Savings", AccountType.SAVINGS).getId();
    creditCardId = persistAccount("Credit Card", AccountType.CREDIT_CARD).getId();
    Account closed =
        Account.create(
            UUID.randomUUID(),
            "Old",
            TestInstitutions.builtInId(institutionRepository),
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    closed.close(LocalDate.now());
    closedAccountId = accountRepository.save(closed).getId();
  }

  private Account persistAccount(String name, AccountType type) {
    return TestFixtures.account(accountRepository, institutionRepository, name, type);
  }

  private String createTransferBody(
      String date, String amount, UUID fromAccountId, UUID toAccountId, String description)
      throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "date",
            date,
            "amount",
            amount,
            "fromAccountId",
            fromAccountId.toString(),
            "toAccountId",
            toAccountId.toString(),
            "description",
            description != null ? description : "Test description"));
  }

  private String createTransfer(String date, String amount, UUID fromAccountId, UUID toAccountId)
      throws Exception {
    String body = createTransferBody(date, amount, fromAccountId, toAccountId, "Test description");
    MvcResult result =
        mockMvc
            .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonSupport.idOf(result);
  }

  @Test
  void createReturnsTheCreatedTransfer() throws Exception {
    String body =
        createTransferBody("2026-03-15", "42.50", checkingId, creditCardId, "Credit card payment");

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.date").value("2026-03-15"))
        .andExpect(jsonPath("$.amount").value(42.50))
        .andExpect(jsonPath("$.fromAccountId").value(checkingId.toString()))
        .andExpect(jsonPath("$.toAccountId").value(creditCardId.toString()))
        .andExpect(jsonPath("$.description").value("Credit card payment"));
  }

  @Test
  void createRejectsBlankDescriptionWith400() throws Exception {
    String body = createTransferBody("2026-03-15", "10.00", checkingId, savingsId, "  ");

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsDescriptionOverLimitWith400() throws Exception {
    String body =
        createTransferBody(
            "2026-03-15",
            "10.00",
            checkingId,
            savingsId,
            "a".repeat(TextFieldConstraints.MAX_DESCRIPTION_LENGTH + 1));

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsAdditionalNotesOverLimitWith400() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-03-15",
                "amount",
                "10.00",
                "fromAccountId",
                checkingId.toString(),
                "toAccountId",
                savingsId.toString(),
                "description",
                "Test description",
                "additionalNotes",
                "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1)));

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsNonPositiveAmountWith400() throws Exception {
    String body = createTransferBody("2026-03-15", "0.00", checkingId, savingsId, null);

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsTheSameAccountOnBothSidesWith400() throws Exception {
    String body = createTransferBody("2026-03-15", "10.00", checkingId, checkingId, null);

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsAClosedAccountWith409() throws Exception {
    String body = createTransferBody("2026-03-15", "10.00", closedAccountId, savingsId, null);

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsAnUnknownAccountWith404() throws Exception {
    String body = createTransferBody("2026-03-15", "10.00", UUID.randomUUID(), savingsId, null);

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRejectsMissingRequiredFields() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("description", "Missing everything else"));

    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getReturnsTransferDetail() throws Exception {
    String id = createTransfer("2026-01-10", "20.00", checkingId, savingsId);

    mockMvc
        .perform(get("/api/transfers/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
  }

  @Test
  void getOfUnknownIdReturns404() throws Exception {
    mockMvc.perform(get("/api/transfers/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void editUpdatesEveryField() throws Exception {
    String id = createTransfer("2026-01-10", "20.00", checkingId, savingsId);

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-02-20",
                "amount",
                "35.00",
                "fromAccountId",
                savingsId.toString(),
                "toAccountId",
                checkingId.toString(),
                "description",
                "Edited",
                "additionalNotes",
                "Edited notes"));

    mockMvc
        .perform(
            patch("/api/transfers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-02-20"))
        .andExpect(jsonPath("$.amount").value(35.00))
        .andExpect(jsonPath("$.fromAccountId").value(savingsId.toString()))
        .andExpect(jsonPath("$.toAccountId").value(checkingId.toString()))
        .andExpect(jsonPath("$.description").value("Edited"))
        .andExpect(jsonPath("$.additionalNotes").value("Edited notes"));
  }

  @Test
  void editRejectsDescriptionOverLimitWith400() throws Exception {
    String id = createTransfer("2026-01-10", "20.00", checkingId, savingsId);

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-01-10",
                "amount",
                "20.00",
                "fromAccountId",
                checkingId.toString(),
                "toAccountId",
                savingsId.toString(),
                "description",
                "a".repeat(TextFieldConstraints.MAX_DESCRIPTION_LENGTH + 1)));

    mockMvc
        .perform(
            patch("/api/transfers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editRejectsAdditionalNotesOverLimitWith400() throws Exception {
    String id = createTransfer("2026-01-10", "20.00", checkingId, savingsId);

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-01-10",
                "amount",
                "20.00",
                "fromAccountId",
                checkingId.toString(),
                "toAccountId",
                savingsId.toString(),
                "description",
                "Test description",
                "additionalNotes",
                "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1)));

    mockMvc
        .perform(
            patch("/api/transfers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editRejectsMovingATransferOntoAClosedAccountWith409() throws Exception {
    String id = createTransfer("2026-01-10", "20.00", checkingId, savingsId);

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-01-10",
                "amount",
                "20.00",
                "fromAccountId",
                closedAccountId.toString(),
                "toAccountId",
                savingsId.toString(),
                "description",
                "Test description"));

    mockMvc
        .perform(
            patch("/api/transfers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteRemovesTheTransfer() throws Exception {
    String id = createTransfer("2026-01-10", "20.00", checkingId, savingsId);

    mockMvc.perform(delete("/api/transfers/" + id)).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/transfers/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void listReturnsPagedContentEnvelope() throws Exception {
    createTransfer("2026-01-10", "20.00", checkingId, savingsId);
    createTransfer("2026-01-15", "30.00", checkingId, creditCardId);

    mockMvc
        .perform(get("/api/transfers").param("accountId", checkingId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void listMatchesAccountIdOnEitherSide() throws Exception {
    String asSource = createTransfer("2026-01-10", "20.00", checkingId, savingsId);
    String asDestination = createTransfer("2026-01-15", "30.00", savingsId, checkingId);
    createTransfer("2026-01-20", "40.00", savingsId, creditCardId);

    mockMvc
        .perform(get("/api/transfers").param("accountId", checkingId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].id").value(asDestination))
        .andExpect(jsonPath("$.content[1].id").value(asSource));
  }

  @Test
  void listFiltersByDateRange() throws Exception {
    createTransfer("2020-06-15", "5.00", checkingId, savingsId);
    String inRange = createTransfer("2026-01-05", "5.00", checkingId, savingsId);

    mockMvc
        .perform(
            get("/api/transfers").param("dateFrom", "2026-01-01").param("dateTo", "2026-01-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(inRange));
  }

  @Test
  void listDefaultsToTwentyPerPageMostRecentFirst() throws Exception {
    createTransfer("2026-01-01", "1.00", checkingId, savingsId);
    createTransfer("2026-01-31", "2.00", checkingId, savingsId);

    mockMvc
        .perform(get("/api/transfers").param("accountId", checkingId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.content[0].date").value("2026-01-31"));
  }
}
