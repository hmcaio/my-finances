package com.chm.myfinances.infrastructure.web.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.MutableClock;
import com.chm.myfinances.testsupport.TestInstitutions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link RecurringTemplateController}, against a real
 * Testcontainers Postgres (ADR 0010). Hand-built {@link MockMvc} - same pattern as F006's {@code
 * BudgetControllerTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import({
  TestcontainersConfiguration.class,
  RecurringTemplateControllerTest.FixedClockTestConfig.class
})
@Transactional
class RecurringTemplateControllerTest {

  /**
   * Overrides the app's {@code Clock} bean (issue #31, B1) with a {@link MutableClock},
   * {@code @Primary} so every constructor-injected {@code Clock} in this context (including {@code
   * RecurringOccurrenceCatchUpService}'s and {@code RecurringTemplateService}'s) resolves to it
   * instead of the real one - only {@link
   * #setCapForANewMonthUpdatesTheAmountShownOnAnAlreadyGeneratedPendingOccurrenceButNotOlderOnes}
   * pins it; every other test in this class leaves it at its default (a plain system clock), so
   * they behave exactly as before this change.
   */
  @TestConfiguration(proxyBeanMethods = false)
  public static class FixedClockTestConfig {
    @Bean
    @Primary
    public MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private MutableClock testClock;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  private UUID categoryId;
  private UUID accountId;
  private UUID paymentMethodId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

    categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent Test", CategoryType.EXPENSE))
            .getId();
    accountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    TestInstitutions.builtInId(institutionRepository),
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();
    paymentMethodId =
        paymentMethodRepository
            .save(PaymentMethod.create(UUID.randomUUID(), "Debit Card Test"))
            .getId();
  }

  /** Undoes any {@link MutableClock#set} so a fixed clock never leaks into another test. */
  @AfterEach
  void tearDown() {
    testClock.reset();
  }

  private String createRequestBody(String effectiveFrom) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("categoryId", categoryId.toString());
    body.put("accountId", accountId.toString());
    body.put("description", "Rent");
    body.put("amount", "1500.00");
    body.put("dayOfMonth", 5);
    body.put("effectiveFrom", effectiveFrom);
    return objectMapper.writeValueAsString(body);
  }

  private String createTemplate(String effectiveFrom) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/recurring-templates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestBody(effectiveFrom)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void createRejectsAnInvestmentAccountWith409() throws Exception {
    UUID investmentAccountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Broker Test",
                    TestInstitutions.builtInId(institutionRepository),
                    AccountType.INVESTMENT,
                    null,
                    null))
            .getId();
    Map<String, Object> body = new HashMap<>();
    body.put("categoryId", categoryId.toString());
    body.put("accountId", investmentAccountId.toString());
    body.put("description", "Rent");
    body.put("amount", "1500.00");
    body.put("dayOfMonth", 5);
    body.put("effectiveFrom", YearMonth.now().toString());

    mockMvc
        .perform(
            post("/api/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
  }

  @Test
  void createReturnsTheCreatedTemplateWithItsCurrentVersion() throws Exception {
    mockMvc
        .perform(
            post("/api/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody(YearMonth.now().toString())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
        .andExpect(jsonPath("$.accountId").value(accountId.toString()))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.currentAmount").value(1500.00))
        .andExpect(jsonPath("$.currentDayOfMonth").value(5));
  }

  @Test
  void createRejectsDescriptionOverLimitWith400() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("categoryId", categoryId.toString());
    body.put("accountId", accountId.toString());
    body.put("description", "a".repeat(TextFieldConstraints.MAX_DESCRIPTION_LENGTH + 1));
    body.put("amount", "1500.00");
    body.put("dayOfMonth", 5);
    body.put("effectiveFrom", YearMonth.now().toString());

    mockMvc
        .perform(
            post("/api/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsAnUnknownCategoryWith404() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("categoryId", UUID.randomUUID().toString());
    body.put("accountId", accountId.toString());
    body.put("description", "Rent");
    body.put("amount", "1500.00");
    body.put("dayOfMonth", 5);
    body.put("effectiveFrom", YearMonth.now().toString());

    mockMvc
        .perform(
            post("/api/recurring-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());
  }

  @Test
  void listReturnsEveryTemplate() throws Exception {
    createTemplate(YearMonth.now().toString());

    mockMvc
        .perform(get("/api/recurring-templates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].categoryId").value(categoryId.toString()));
  }

  @Test
  void setCapForANewMonthCreatesAnAdditionalVersion() throws Exception {
    String id = createTemplate("2020-01");
    String patchBody =
        objectMapper.writeValueAsString(
            Map.of(
                "amount",
                "1600.00",
                "dayOfMonth",
                10,
                "effectiveFrom",
                YearMonth.now().toString()));

    mockMvc
        .perform(
            patch("/api/recurring-templates/" + id + "/cap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentAmount").value(1600.00))
        .andExpect(jsonPath("$.currentDayOfMonth").value(10));
  }

  @Test
  void setCapForANewMonthUpdatesTheAmountShownOnAnAlreadyGeneratedPendingOccurrenceButNotOlderOnes()
      throws Exception {
    // A fixed clock (issue #31, B1) - decoupled from the real wall clock, so this no longer needs
    // "today is at least the 6th whenever CI runs this": day 15 of a synthetic month, well past the
    // template's day-5 cycle in both this month and last.
    LocalDate today = LocalDate.of(2024, 6, 15);
    testClock.set(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    YearMonth thisMonth = YearMonth.from(today);
    YearMonth lastMonth = thisMonth.minusMonths(1);
    String lastMonthDueDate = lastMonth.atDay(5).toString();
    String thisMonthDueDate = thisMonth.atDay(5).toString();

    // effectiveFrom last month, day 5 - the catch-up job (triggered below by GET pending)
    // generates one pending occurrence for last month's cycle and one for this month's, both
    // initially resolving to the same (only) version.
    String id = createTemplate(lastMonth.toString());
    mockMvc.perform(get("/api/recurring-templates/pending")).andExpect(status().isOk());

    String patchBody =
        objectMapper.writeValueAsString(
            Map.of("amount", "1600.00", "dayOfMonth", 10, "effectiveFrom", thisMonth.toString()));
    mockMvc
        .perform(
            patch("/api/recurring-templates/" + id + "/cap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk());

    MvcResult pendingResult = mockMvc.perform(get("/api/recurring-templates/pending")).andReturn();
    var pending = objectMapper.readTree(pendingResult.getResponse().getContentAsString());

    assertThat(pending.size()).isEqualTo(2);
    Map<String, Double> amountByDueDate = new HashMap<>();
    for (var occurrence : pending) {
      amountByDueDate.put(occurrence.get("dueDate").asText(), occurrence.get("amount").asDouble());
    }
    // Last month's cycle is still correctly covered by the old version (the new one is only
    // effective from this month onward) - must NOT be rewritten (F007 spec: a cap change never
    // rewrites what a past month showed). This month's cycle was generated before the edit under
    // the old version, but is now covered by the new one - must reflect the new amount (1600.00),
    // not the stale one it was generated with (1500.00). A bug that rewrote history, or one that
    // failed to realign the still-pending occurrence to the new version, would each flip exactly
    // one of these two entries and fail this assertion.
    assertThat(amountByDueDate)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                lastMonthDueDate, 1500.00,
                thisMonthDueDate, 1600.00));
  }

  @Test
  void stopDeactivatesTheTemplate() throws Exception {
    String id = createTemplate(YearMonth.now().toString());

    mockMvc
        .perform(post("/api/recurring-templates/" + id + "/stop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void reactivateReactivatesAStoppedTemplate() throws Exception {
    String id = createTemplate(YearMonth.now().toString());
    mockMvc.perform(post("/api/recurring-templates/" + id + "/stop")).andExpect(status().isOk());

    mockMvc
        .perform(post("/api/recurring-templates/" + id + "/reactivate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void pendingReturnsAGeneratedOccurrenceAfterCatchUp() throws Exception {
    // effectiveFrom this month with day 5 (today is at least the 6th whenever CI runs this) -
    // generates exactly one pending occurrence for the current cycle only.
    createTemplate(YearMonth.now().toString());

    mockMvc
        .perform(get("/api/recurring-templates/pending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].amount").value(1500.00));
  }

  @Test
  void confirmCreatesATransactionAndRemovesThePendingOccurrence() throws Exception {
    createTemplate(YearMonth.now().toString());
    MvcResult pendingResult = mockMvc.perform(get("/api/recurring-templates/pending")).andReturn();
    String pendingId =
        objectMapper
            .readTree(pendingResult.getResponse().getContentAsString())
            .get(0)
            .get("id")
            .asText();

    String confirmBody =
        objectMapper.writeValueAsString(Map.of("paymentMethodId", paymentMethodId.toString()));

    mockMvc
        .perform(
            post("/api/recurring-templates/pending/" + pendingId + "/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(1500.00))
        .andExpect(jsonPath("$.accountId").value(accountId.toString()));

    mockMvc
        .perform(get("/api/recurring-templates/pending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void confirmWithAnOverriddenAmountUsesTheOverrideNotTheTemplatesAmount() throws Exception {
    createTemplate(YearMonth.now().toString());
    MvcResult pendingResult = mockMvc.perform(get("/api/recurring-templates/pending")).andReturn();
    String pendingId =
        objectMapper
            .readTree(pendingResult.getResponse().getContentAsString())
            .get(0)
            .get("id")
            .asText();

    String confirmBody =
        objectMapper.writeValueAsString(
            Map.of("paymentMethodId", paymentMethodId.toString(), "amount", "1650.00"));

    mockMvc
        .perform(
            post("/api/recurring-templates/pending/" + pendingId + "/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(1650.00));
  }

  @Test
  void confirmOfUnknownPendingIdReturns404() throws Exception {
    String confirmBody =
        objectMapper.writeValueAsString(Map.of("paymentMethodId", paymentMethodId.toString()));

    mockMvc
        .perform(
            post("/api/recurring-templates/pending/" + UUID.randomUUID() + "/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
        .andExpect(status().isNotFound());
  }

  @Test
  void dismissDeletesThePendingOccurrenceWithoutCreatingATransaction() throws Exception {
    createTemplate(YearMonth.now().toString());
    MvcResult pendingResult = mockMvc.perform(get("/api/recurring-templates/pending")).andReturn();
    String pendingId =
        objectMapper
            .readTree(pendingResult.getResponse().getContentAsString())
            .get(0)
            .get("id")
            .asText();

    mockMvc
        .perform(delete("/api/recurring-templates/pending/" + pendingId))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/recurring-templates/pending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }
}
