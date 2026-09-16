package com.chm.myfinances.infrastructure.web.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
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
 * REST-layer integration test for {@link TransactionController}, against a real Testcontainers
 * Postgres (ADR 0010). Hand-built {@link MockMvc} - Spring Boot 4.x removed
 * {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} - same pattern as F002/F003's controller tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
class TransactionControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  private UUID expenseCategoryId;
  private UUID incomeCategoryId;
  private UUID accountId;
  private UUID otherAccountId;
  private UUID closedAccountId;
  private UUID paymentMethodId;
  private UUID otherPaymentMethodId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

    expenseCategoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE))
            .getId();
    incomeCategoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Salary", CategoryType.INCOME))
            .getId();
    accountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    null,
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();
    otherAccountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Savings",
                    null,
                    AccountType.SAVINGS,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();
    Account closed =
        Account.create(
            UUID.randomUUID(), "Old", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    closed.close();
    closedAccountId = accountRepository.save(closed).getId();
    paymentMethodId =
        paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Debit Card")).getId();
    otherPaymentMethodId =
        paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Cash")).getId();
  }

  private String createTransactionBody(
      String date, String amount, UUID categoryId, UUID accountId, String description)
      throws Exception {
    return createTransactionBody(date, amount, categoryId, accountId, paymentMethodId, description);
  }

  private String createTransactionBody(
      String date,
      String amount,
      UUID categoryId,
      UUID accountId,
      UUID paymentMethodId,
      String description)
      throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "date",
            date,
            "amount",
            amount,
            "categoryId",
            categoryId.toString(),
            "accountId",
            accountId.toString(),
            "paymentMethodId",
            paymentMethodId.toString(),
            "description",
            description != null ? description : "Test description"));
  }

  private String createTransaction(String date, String amount, UUID categoryId, UUID accountId)
      throws Exception {
    return createTransaction(date, amount, categoryId, accountId, paymentMethodId);
  }

  private String createTransaction(
      String date, String amount, UUID categoryId, UUID accountId, UUID paymentMethodId)
      throws Exception {
    String body =
        createTransactionBody(
            date, amount, categoryId, accountId, paymentMethodId, "Test description");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void createReturnsTransactionWithDerivedType() throws Exception {
    String body =
        createTransactionBody(
            "2026-03-15", "42.50", expenseCategoryId, accountId, "Weekly groceries");

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.date").value("2026-03-15"))
        .andExpect(jsonPath("$.amount").value(42.50))
        .andExpect(jsonPath("$.categoryId").value(expenseCategoryId.toString()))
        .andExpect(jsonPath("$.type").value("EXPENSE"))
        .andExpect(jsonPath("$.accountId").value(accountId.toString()))
        .andExpect(jsonPath("$.paymentMethodId").value(paymentMethodId.toString()))
        .andExpect(jsonPath("$.description").value("Weekly groceries"));
  }

  @Test
  void createRejectsBlankDescriptionWith400() throws Exception {
    String body = createTransactionBody("2026-03-15", "10.00", expenseCategoryId, accountId, "  ");

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsAClosedAccountWith409() throws Exception {
    String body =
        createTransactionBody("2026-03-15", "10.00", expenseCategoryId, closedAccountId, null);

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsAnUnknownCategoryWith404() throws Exception {
    String body = createTransactionBody("2026-03-15", "10.00", UUID.randomUUID(), accountId, null);

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRejectsNonPositiveAmountWith400() throws Exception {
    String body = createTransactionBody("2026-03-15", "0.00", expenseCategoryId, accountId, null);

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsMissingRequiredFields() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("description", "Missing everything else"));

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getReturnsTransactionDetail() throws Exception {
    String id = createTransaction("2026-01-10", "20.00", expenseCategoryId, accountId);

    mockMvc
        .perform(get("/api/transactions/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
  }

  @Test
  void getOfUnknownIdReturns404() throws Exception {
    mockMvc.perform(get("/api/transactions/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void editUpdatesEveryField() throws Exception {
    String id = createTransaction("2026-01-10", "20.00", expenseCategoryId, accountId);

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-02-20",
                "amount",
                "35.00",
                "categoryId",
                incomeCategoryId.toString(),
                "accountId",
                accountId.toString(),
                "paymentMethodId",
                paymentMethodId.toString(),
                "description",
                "Edited",
                "additionalNotes",
                "Edited notes"));

    mockMvc
        .perform(
            patch("/api/transactions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-02-20"))
        .andExpect(jsonPath("$.amount").value(35.00))
        .andExpect(jsonPath("$.categoryId").value(incomeCategoryId.toString()))
        .andExpect(jsonPath("$.type").value("INCOME"))
        .andExpect(jsonPath("$.description").value("Edited"))
        .andExpect(jsonPath("$.additionalNotes").value("Edited notes"));
  }

  @Test
  void editRejectsMovingATransactionOntoAClosedAccountWith409() throws Exception {
    String id = createTransaction("2026-01-10", "20.00", expenseCategoryId, accountId);

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-01-10",
                "amount",
                "20.00",
                "categoryId",
                expenseCategoryId.toString(),
                "accountId",
                closedAccountId.toString(),
                "paymentMethodId",
                paymentMethodId.toString(),
                "description",
                "Test description"));

    mockMvc
        .perform(
            patch("/api/transactions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteRemovesTheTransaction() throws Exception {
    String id = createTransaction("2026-01-10", "20.00", expenseCategoryId, accountId);

    mockMvc.perform(delete("/api/transactions/" + id)).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/transactions/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void listReturnsPagedContentEnvelope() throws Exception {
    createTransaction("2026-01-10", "20.00", expenseCategoryId, accountId);
    createTransaction("2026-01-15", "30.00", expenseCategoryId, accountId);

    mockMvc
        .perform(get("/api/transactions").param("accountId", accountId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void listFiltersByCategoryAccountAndDateRange() throws Exception {
    createTransaction("2026-01-05", "10.00", expenseCategoryId, accountId);
    createTransaction("2026-01-20", "15.00", incomeCategoryId, accountId);

    mockMvc
        .perform(
            get("/api/transactions")
                .param("categoryId", expenseCategoryId.toString())
                .param("dateFrom", "2026-01-01")
                .param("dateTo", "2026-01-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].categoryId").value(expenseCategoryId.toString()));
  }

  @Test
  void listDefaultsToTwentyPerPageMostRecentFirst() throws Exception {
    createTransaction("2026-01-01", "1.00", expenseCategoryId, accountId);
    createTransaction("2026-01-31", "2.00", expenseCategoryId, accountId);

    mockMvc
        .perform(get("/api/transactions").param("accountId", accountId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.content[0].date").value("2026-01-31"));
  }

  @Test
  void listFiltersByEachDimensionIndependently() throws Exception {
    // Plan.md's verification bullet: "Filter by each dimension (date range, category, account,
    // payment method) independently". One transaction unique on every dimension, so filtering by
    // just that one dimension isolates it from every other transaction created here.
    String uniqueByDate =
        createTransaction("2020-06-15", "5.00", expenseCategoryId, accountId, paymentMethodId);
    createTransaction("2026-01-01", "5.00", expenseCategoryId, accountId, paymentMethodId);
    String uniqueByCategory =
        createTransaction("2026-01-01", "5.00", incomeCategoryId, accountId, paymentMethodId);
    String uniqueByAccount =
        createTransaction("2026-01-01", "5.00", expenseCategoryId, otherAccountId, paymentMethodId);
    String uniqueByPaymentMethod =
        createTransaction("2026-01-01", "5.00", expenseCategoryId, accountId, otherPaymentMethodId);

    mockMvc
        .perform(
            get("/api/transactions").param("dateFrom", "2020-01-01").param("dateTo", "2020-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(uniqueByDate));

    mockMvc
        .perform(get("/api/transactions").param("categoryId", incomeCategoryId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(uniqueByCategory));

    mockMvc
        .perform(get("/api/transactions").param("accountId", otherAccountId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(uniqueByAccount));

    mockMvc
        .perform(get("/api/transactions").param("paymentMethodId", otherPaymentMethodId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(uniqueByPaymentMethod));
  }
}
