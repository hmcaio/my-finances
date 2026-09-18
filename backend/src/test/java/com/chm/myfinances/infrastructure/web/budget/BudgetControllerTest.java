package com.chm.myfinances.infrastructure.web.budget;

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
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
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
 * REST-layer integration test for {@link BudgetController}, against a real Testcontainers Postgres
 * (ADR 0010). Hand-built {@link MockMvc} - Spring Boot 4.x removed
 * {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} - same pattern as F004/F005's controller tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
class BudgetControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private TransactionRepository transactionRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  private UUID groceriesCategoryId;
  private UUID salaryCategoryId;
  private UUID accountId;
  private UUID paymentMethodId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

    groceriesCategoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Groceries Test", CategoryType.EXPENSE))
            .getId();
    salaryCategoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Salary Test", CategoryType.INCOME))
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
    paymentMethodId =
        paymentMethodRepository
            .save(PaymentMethod.create(UUID.randomUUID(), "Debit Card Test"))
            .getId();
  }

  private String createBudgetBody(UUID categoryId, String monthlyCap, String effectiveFrom)
      throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "categoryId", categoryId.toString(),
            "monthlyCap", monthlyCap,
            "effectiveFrom", effectiveFrom));
  }

  private String createBudget(UUID categoryId, String monthlyCap, String effectiveFrom)
      throws Exception {
    String body = createBudgetBody(categoryId, monthlyCap, effectiveFrom);
    MvcResult result =
        mockMvc
            .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void persistExpenseTransaction(UUID categoryId, LocalDate date, String amount) {
    transactionRepository.save(
        Transaction.create(
            UUID.randomUUID(),
            date,
            new BigDecimal(amount),
            categoryId,
            CategoryType.EXPENSE,
            accountId,
            paymentMethodId,
            null,
            "Test transaction",
            null));
  }

  @Test
  void createReturnsTheCreatedBudgetWithItsCurrentCap() throws Exception {
    String currentMonth = YearMonthNow.currentMonth();
    String body = createBudgetBody(groceriesCategoryId, "500.00", currentMonth);

    mockMvc
        .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categoryId").value(groceriesCategoryId.toString()))
        .andExpect(jsonPath("$.currentCap").value(500.00))
        .andExpect(jsonPath("$.currentCapEffectiveFrom").value(currentMonth));
  }

  @Test
  void createRejectsAnIncomeCategoryWith409() throws Exception {
    String body = createBudgetBody(salaryCategoryId, "500.00", YearMonthNow.currentMonth());

    mockMvc
        .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsAnUnknownCategoryWith404() throws Exception {
    String body = createBudgetBody(UUID.randomUUID(), "500.00", YearMonthNow.currentMonth());

    mockMvc
        .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRejectsADuplicateBudgetForTheSameCategoryWith409() throws Exception {
    createBudget(groceriesCategoryId, "500.00", YearMonthNow.currentMonth());
    String body = createBudgetBody(groceriesCategoryId, "999.00", YearMonthNow.currentMonth());

    mockMvc
        .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsNonPositiveCapWith400() throws Exception {
    String body = createBudgetBody(groceriesCategoryId, "0.00", YearMonthNow.currentMonth());

    mockMvc
        .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsEveryBudget() throws Exception {
    createBudget(groceriesCategoryId, "500.00", YearMonthNow.currentMonth());

    mockMvc
        .perform(get("/api/budgets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].categoryId").value(groceriesCategoryId.toString()))
        .andExpect(jsonPath("$[0].currentCap").value(500.00));
  }

  @Test
  void setCapForANewFutureMonthCreatesAnAdditionalVersion() throws Exception {
    String id = createBudget(groceriesCategoryId, "500.00", "2020-01");

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of("monthlyCap", "600.00", "effectiveFrom", YearMonthNow.currentMonth()));

    mockMvc
        .perform(
            patch("/api/budgets/" + id + "/cap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentCap").value(600.00));
  }

  @Test
  void setCapOfUnknownBudgetReturns404() throws Exception {
    String patchBody =
        objectMapper.writeValueAsString(
            Map.of("monthlyCap", "600.00", "effectiveFrom", YearMonthNow.currentMonth()));

    mockMvc
        .perform(
            patch("/api/budgets/" + UUID.randomUUID() + "/cap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isNotFound());
  }

  @Test
  void reportReturnsCapAndActualForTheRequestedMonth() throws Exception {
    createBudget(groceriesCategoryId, "300.00", "2026-01");
    persistExpenseTransaction(groceriesCategoryId, LocalDate.of(2026, 3, 5), "40.00");
    persistExpenseTransaction(groceriesCategoryId, LocalDate.of(2026, 2, 5), "999.00");

    mockMvc
        .perform(get("/api/budgets/report").param("month", "2026-03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].categoryId").value(groceriesCategoryId.toString()))
        .andExpect(jsonPath("$[0].cap").value(300.00))
        .andExpect(jsonPath("$[0].actual").value(40.00));
  }

  /** Small helper so tests always compare against "now" the same way the controller does. */
  private static final class YearMonthNow {
    static String currentMonth() {
      return java.time.YearMonth.now().toString();
    }
  }
}
