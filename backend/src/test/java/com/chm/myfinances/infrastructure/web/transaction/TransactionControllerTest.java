package com.chm.myfinances.infrastructure.web.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
 * REST-layer integration test for {@link TransactionController}, against a real Testcontainers
 * Postgres (ADR 0010). Hand-built {@link MockMvc} - Spring Boot 4.x removed
 * {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} - same pattern as F002/F003's controller tests.
 */
@WebIntegrationTest
class TransactionControllerTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private VehicleRepository vehicleRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  private UUID expenseCategoryId;
  private UUID incomeCategoryId;
  private UUID fuelCategoryId;
  private UUID accountId;
  private UUID otherAccountId;
  private UUID closedAccountId;
  private UUID paymentMethodId;
  private UUID otherPaymentMethodId;
  private UUID vehicleId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);

    expenseCategoryId =
        TestFixtures.category(categoryRepository, "Groceries Test", CategoryType.EXPENSE).getId();
    incomeCategoryId =
        TestFixtures.category(categoryRepository, "Salary Test", CategoryType.INCOME).getId();
    // V18 seeds exactly one fuel category (adopt-or-insert "Fuel") - never assume none exists.
    fuelCategoryId =
        categoryRepository.findAll().stream()
            .filter(Category::isFuelCategory)
            .findFirst()
            .orElseThrow()
            .getId();
    vehicleId = vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Civic Test")).getId();
    accountId =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Checking").getId();
    otherAccountId =
        TestFixtures.account(
                accountRepository, institutionRepository, "Savings", AccountType.SAVINGS)
            .getId();
    // A closed account is a different shape than the fixture provides (open only), so this one
    // keeps its own inline Account.create(...) + close(...).
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
    paymentMethodId =
        TestFixtures.paymentMethod(paymentMethodRepository, "Debit Card Test").getId();
    otherPaymentMethodId = TestFixtures.paymentMethod(paymentMethodRepository, "Cash Test").getId();
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
    return JsonSupport.idOf(result);
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
  void createRejectsDescriptionOverLimitWith400() throws Exception {
    String body =
        createTransactionBody(
            "2026-03-15",
            "10.00",
            expenseCategoryId,
            accountId,
            "a".repeat(TextFieldConstraints.MAX_DESCRIPTION_LENGTH + 1));

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
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
                "categoryId",
                expenseCategoryId.toString(),
                "accountId",
                accountId.toString(),
                "paymentMethodId",
                paymentMethodId.toString(),
                "description",
                "Test description",
                "additionalNotes",
                "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1)));

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
  void createRejectsAnInvestmentAccountWith409() throws Exception {
    UUID investmentAccountId =
        TestFixtures.account(
                accountRepository, institutionRepository, "Broker Test", AccountType.INVESTMENT)
            .getId();
    String body =
        createTransactionBody("2026-03-15", "10.00", expenseCategoryId, investmentAccountId, null);

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
  void editRejectsDescriptionOverLimitWith400() throws Exception {
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
                accountId.toString(),
                "paymentMethodId",
                paymentMethodId.toString(),
                "description",
                "a".repeat(TextFieldConstraints.MAX_DESCRIPTION_LENGTH + 1)));

    mockMvc
        .perform(
            patch("/api/transactions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editRejectsAdditionalNotesOverLimitWith400() throws Exception {
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
                accountId.toString(),
                "paymentMethodId",
                paymentMethodId.toString(),
                "description",
                "Test description",
                "additionalNotes",
                "a".repeat(TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH + 1)));

    mockMvc
        .perform(
            patch("/api/transactions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isBadRequest());
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

  @Test
  void spendByCategorySumsExpensesOfTheMonthOnly() throws Exception {
    createTransaction("2001-05-03", "10.00", expenseCategoryId, accountId);
    createTransaction("2001-05-20", "15.50", expenseCategoryId, otherAccountId);
    createTransaction("2001-06-01", "999.00", expenseCategoryId, accountId);
    createTransaction("2001-05-10", "3000.00", incomeCategoryId, accountId);

    mockMvc
        .perform(get("/api/transactions/spend-by-category").param("month", "2001-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].categoryId").value(expenseCategoryId.toString()))
        .andExpect(jsonPath("$[0].total").value(25.50));
  }

  @Test
  void spendByCategoryRejectsAMalformedMonthWith400() throws Exception {
    mockMvc
        .perform(get("/api/transactions/spend-by-category").param("month", "nope"))
        .andExpect(status().isBadRequest());
  }

  // F024 (ADR 0021): fuel fields on create/edit, and the computed ratios in the response.

  private Map<String, Object> fuelFields(UUID vehicleId) {
    Map<String, Object> fields = new HashMap<>();
    fields.put("vehicleId", vehicleId.toString());
    fields.put("fuelType", "GASOLINA");
    fields.put("liters", "40.500");
    fields.put("pricePerLiter", "5.799");
    return fields;
  }

  @Test
  void createWithFuelCategoryAndFuelFieldsReturnsFuelFieldsAndNullRatiosOnFirstFill()
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("date", "2026-03-15");
    body.put("amount", "234.85");
    body.put("categoryId", fuelCategoryId.toString());
    body.put("accountId", accountId.toString());
    body.put("paymentMethodId", paymentMethodId.toString());
    body.put("description", "Fill up");
    body.putAll(fuelFields(vehicleId));

    mockMvc
        .perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
        .andExpect(jsonPath("$.fuelType").value("GASOLINA"))
        .andExpect(jsonPath("$.liters").value(40.5))
        .andExpect(jsonPath("$.pricePerLiter").value(5.799))
        .andExpect(jsonPath("$.kmSinceLastFill").doesNotExist())
        .andExpect(jsonPath("$.kmPerLiter").doesNotExist())
        .andExpect(jsonPath("$.amountPerKm").doesNotExist())
        .andExpect(jsonPath("$.litersPerKm").doesNotExist());
  }

  @Test
  void createWithKmSinceLastFillReturnsComputedRatios() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("date", "2026-03-15");
    body.put("amount", "200.00");
    body.put("categoryId", fuelCategoryId.toString());
    body.put("accountId", accountId.toString());
    body.put("paymentMethodId", paymentMethodId.toString());
    body.put("description", "Fill up");
    body.putAll(fuelFields(vehicleId));
    body.put("liters", "40");
    body.put("kmSinceLastFill", "400.0");

    mockMvc
        .perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kmPerLiter").value(10.0))
        .andExpect(jsonPath("$.amountPerKm").value(0.5))
        .andExpect(jsonPath("$.litersPerKm").value(0.1));
  }

  @Test
  void createRejectsFuelCategoryWithoutFuelFieldsWith400() throws Exception {
    String body =
        createTransactionBody("2026-03-15", "10.00", fuelCategoryId, accountId, "No fuel fields");

    mockMvc
        .perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsNonFuelCategoryWithFuelFieldsWith400() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("date", "2026-03-15");
    body.put("amount", "10.00");
    body.put("categoryId", expenseCategoryId.toString());
    body.put("accountId", accountId.toString());
    body.put("paymentMethodId", paymentMethodId.toString());
    body.put("description", "Not fuel category");
    body.putAll(fuelFields(vehicleId));

    mockMvc
        .perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsPartialFuelFieldsWith400() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("date", "2026-03-15");
    body.put("amount", "10.00");
    body.put("categoryId", fuelCategoryId.toString());
    body.put("accountId", accountId.toString());
    body.put("paymentMethodId", paymentMethodId.toString());
    body.put("description", "Partial fuel fields");
    body.put("vehicleId", vehicleId.toString());
    // fuelType/liters/pricePerLiter deliberately missing.

    mockMvc
        .perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsAnUnknownVehicleIdWith404() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("date", "2026-03-15");
    body.put("amount", "10.00");
    body.put("categoryId", fuelCategoryId.toString());
    body.put("accountId", accountId.toString());
    body.put("paymentMethodId", paymentMethodId.toString());
    body.put("description", "Unknown vehicle");
    body.putAll(fuelFields(UUID.randomUUID()));

    mockMvc
        .perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());
  }

  @Test
  void editRejectsMovingAFuelTransactionsCategoryAwayWithoutClearingFuelFieldsWith400()
      throws Exception {
    Map<String, Object> createBody = new HashMap<>();
    createBody.put("date", "2026-03-15");
    createBody.put("amount", "200.00");
    createBody.put("categoryId", fuelCategoryId.toString());
    createBody.put("accountId", accountId.toString());
    createBody.put("paymentMethodId", paymentMethodId.toString());
    createBody.put("description", "Fill up");
    createBody.putAll(fuelFields(vehicleId));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createBody)))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    Map<String, Object> patchBody = new HashMap<>();
    patchBody.put("date", "2026-03-15");
    patchBody.put("amount", "200.00");
    patchBody.put("categoryId", expenseCategoryId.toString());
    patchBody.put("accountId", accountId.toString());
    patchBody.put("paymentMethodId", paymentMethodId.toString());
    patchBody.put("description", "Moved away without clearing fuel fields");
    patchBody.putAll(fuelFields(vehicleId));

    mockMvc
        .perform(
            patch("/api/transactions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editMovingAFuelTransactionsCategoryAwayAfterClearingFuelFieldsSucceeds() throws Exception {
    Map<String, Object> createBody = new HashMap<>();
    createBody.put("date", "2026-03-15");
    createBody.put("amount", "200.00");
    createBody.put("categoryId", fuelCategoryId.toString());
    createBody.put("accountId", accountId.toString());
    createBody.put("paymentMethodId", paymentMethodId.toString());
    createBody.put("description", "Fill up");
    createBody.putAll(fuelFields(vehicleId));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createBody)))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonSupport.idOf(createResult);

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "date",
                "2026-03-15",
                "amount",
                "200.00",
                "categoryId",
                expenseCategoryId.toString(),
                "accountId",
                accountId.toString(),
                "paymentMethodId",
                paymentMethodId.toString(),
                "description",
                "Fuel fields cleared"));

    mockMvc
        .perform(
            patch("/api/transactions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categoryId").value(expenseCategoryId.toString()))
        .andExpect(jsonPath("$.vehicleId").doesNotExist());
  }
}
