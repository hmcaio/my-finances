package com.chm.myfinances.infrastructure.web.paymentmethod;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
 * REST-layer integration test for {@link PaymentMethodController}, against a real Testcontainers
 * Postgres (ADR 0010). See {@code CategoryControllerTest} for why {@link MockMvc} is built by hand
 * here instead of via {@code @AutoConfigureMockMvc} (removed in Boot 4.x).
 */
@WebIntegrationTest
class PaymentMethodControllerTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private CategoryRepository categoryRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void createListRenameAndDeleteRoundTrip() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "Test PM"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    mockMvc
        .perform(get("/api/payment-methods"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Test PM')]").exists());

    String renameBody = objectMapper.writeValueAsString(Map.of("name", "Renamed PM"));
    mockMvc
        .perform(
            patch("/api/payment-methods/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renameBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed PM"));

    mockMvc.perform(delete("/api/payment-methods/" + id)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/payment-methods"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Renamed PM')]").doesNotExist());
  }

  @Test
  void deleteRejectsAPaymentMethodReferencedByATransactionWith409() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "In Use"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    Account account =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Checking");
    Category category =
        TestFixtures.category(categoryRepository, "Groceries Test", CategoryType.EXPENSE);
    transactionRepository.save(
        TransactionMother.expense()
            .withAmount(BigDecimal.TEN)
            .withCategoryId(category.getId())
            .withAccountId(account.getId())
            .withPaymentMethodId(UUID.fromString(id))
            .withDescription("In-use transaction")
            .build());

    mockMvc.perform(delete("/api/payment-methods/" + id)).andExpect(status().isConflict());
  }

  @Test
  void createRejectsADuplicateNameWith409() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "Duplicate PM"));
    mockMvc
        .perform(
            post("/api/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isConflict());
  }

  @Test
  void renameRejectsADuplicateNameWith409() throws Exception {
    String firstBody = objectMapper.writeValueAsString(Map.of("name", "Original PM"));
    mockMvc
        .perform(
            post("/api/payment-methods").contentType(MediaType.APPLICATION_JSON).content(firstBody))
        .andExpect(status().isCreated());

    String secondBody = objectMapper.writeValueAsString(Map.of("name", "PM To Rename"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    String renameBody = objectMapper.writeValueAsString(Map.of("name", "Original PM"));
    mockMvc
        .perform(
            patch("/api/payment-methods/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renameBody))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsNameOverMaxLength() throws Exception {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);
    String createBody = objectMapper.writeValueAsString(Map.of("name", tooLongName));

    mockMvc
        .perform(
            post("/api/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void renameRejectsNameOverMaxLength() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "Test PM 2"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);
    String renameBody = objectMapper.writeValueAsString(Map.of("name", tooLongName));
    mockMvc
        .perform(
            patch("/api/payment-methods/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renameBody))
        .andExpect(status().isBadRequest());
  }
}
