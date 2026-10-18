package com.chm.myfinances.infrastructure.web.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link LedgerController} (F037), against a real Testcontainers
 * Postgres (ADR 0010), hand-built {@link MockMvc} ({@code docs/conventions/backend-testing.md}).
 * Every request is scoped by the test's own freshly created account so rows committed by other
 * tests never leak in.
 */
@WebIntegrationTest
class LedgerControllerTest {

  private static final LocalDate DAY = LocalDate.of(2094, 5, 10);

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private TransferRepository transferRepository;
  @Autowired private InvestmentProductRepository investmentProductRepository;
  @Autowired private InvestmentCategoryRepository investmentCategoryRepository;

  private MockMvc mockMvc;

  private UUID checkingId;
  private UUID savingsId;
  private UUID expenseCategoryId;
  private UUID incomeCategoryId;
  private UUID paymentMethodId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    checkingId = persistAccount("Checking Ledger Test", AccountType.CHECKING).getId();
    savingsId = persistAccount("Savings Ledger Test", AccountType.SAVINGS).getId();
    expenseCategoryId =
        TestFixtures.category(categoryRepository, "Groceries Ledger Test", CategoryType.EXPENSE)
            .getId();
    incomeCategoryId =
        TestFixtures.category(categoryRepository, "Salary Ledger Test", CategoryType.INCOME)
            .getId();
    paymentMethodId =
        TestFixtures.paymentMethod(paymentMethodRepository, "Debit Card Ledger Test").getId();
  }

  private Account persistAccount(String name, AccountType type) {
    return TestFixtures.account(accountRepository, institutionRepository, name, type);
  }

  private Transaction saveExpense(String description, String amount, LocalDate date) {
    return transactionRepository.save(
        TransactionMother.expense()
            .withDate(date)
            .withAmount(new BigDecimal(amount))
            .withCategoryId(expenseCategoryId)
            .withAccountId(checkingId)
            .withPaymentMethodId(paymentMethodId)
            .withDescription(description)
            .build());
  }

  private Transaction saveIncome(String amount, LocalDate date) {
    return transactionRepository.save(
        TransactionMother.income()
            .withDate(date)
            .withAmount(new BigDecimal(amount))
            .withCategoryId(incomeCategoryId)
            .withAccountId(checkingId)
            .withPaymentMethodId(paymentMethodId)
            .build());
  }

  private Transfer saveTransfer(String description, String amount, LocalDate date) {
    return transferRepository.save(
        TransferMother.transfer()
            .withDate(date)
            .withFromAccountId(checkingId)
            .withToAccountId(savingsId)
            .withAmount(new BigDecimal(amount))
            .withDescription(description)
            .build());
  }

  private MockHttpServletRequestBuilder ledger() {
    return get("/api/ledger").param("accountId", checkingId.toString());
  }

  @Test
  void returnsEachRowTypeInterleavedNewestFirstWithTheSummary() throws Exception {
    Transaction expense = saveExpense("Supermarket", "50.00", DAY);
    Transaction income = saveIncome("1000.00", DAY.plusDays(1));
    Transfer transfer = saveTransfer("Move to savings", "200.00", DAY.minusDays(1));

    mockMvc
        .perform(ledger())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(3))
        .andExpect(jsonPath("$.truncated").value(false))
        .andExpect(jsonPath("$.entries[0].id").value(income.getId().toString()))
        .andExpect(jsonPath("$.entries[0].type").value("INCOME"))
        .andExpect(jsonPath("$.entries[0].amount").value(1000.00))
        .andExpect(jsonPath("$.entries[0].categoryId").value(incomeCategoryId.toString()))
        .andExpect(jsonPath("$.entries[0].accountId").value(checkingId.toString()))
        .andExpect(jsonPath("$.entries[0].paymentMethodId").value(paymentMethodId.toString()))
        .andExpect(jsonPath("$.entries[0].fromAccountId").doesNotExist())
        .andExpect(jsonPath("$.entries[1].id").value(expense.getId().toString()))
        .andExpect(jsonPath("$.entries[1].type").value("EXPENSE"))
        .andExpect(jsonPath("$.entries[1].description").value("Supermarket"))
        .andExpect(jsonPath("$.entries[2].id").value(transfer.getId().toString()))
        .andExpect(jsonPath("$.entries[2].type").value("TRANSFER"))
        .andExpect(jsonPath("$.entries[2].amount").value(200.00))
        .andExpect(jsonPath("$.entries[2].fromAccountId").value(checkingId.toString()))
        .andExpect(jsonPath("$.entries[2].toAccountId").value(savingsId.toString()))
        .andExpect(jsonPath("$.entries[2].categoryId").doesNotExist())
        .andExpect(jsonPath("$.entries[2].paymentMethodId").doesNotExist())
        .andExpect(jsonPath("$.entries[2].tradeLines.length()").value(0))
        .andExpect(jsonPath("$.summary.income").value(1000.00))
        .andExpect(jsonPath("$.summary.expense").value(50.00))
        .andExpect(jsonPath("$.summary.net").value(950.00));
  }

  @Test
  void aTradeConfirmationCarriesItsTradeLines() throws Exception {
    Account broker = persistAccount("Broker Ledger Test", AccountType.INVESTMENT);
    InvestmentProduct product =
        investmentProductRepository.save(
            InvestmentProduct.create(
                UUID.randomUUID(),
                // any seeded investment category will do; the API only echoes the product id
                investmentCategoryRepository.findAll().get(0).getId(),
                null,
                "Product Ledger Test",
                null));
    Transfer trade =
        transferRepository.save(
            TransferMother.transfer()
                .withDate(DAY)
                .withTradeConfirmation(
                    checkingId,
                    broker.getId(),
                    BigDecimal.ZERO,
                    new TradeConfirmationLine(
                        product.getId(),
                        TradeSide.BUY,
                        new BigDecimal("10"),
                        new BigDecimal("5.00"),
                        null,
                        false))
                .build());

    mockMvc
        .perform(get("/api/ledger").param("accountId", broker.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.entries[0].id").value(trade.getId().toString()))
        .andExpect(jsonPath("$.entries[0].type").value("TRANSFER"))
        .andExpect(jsonPath("$.entries[0].tradeLines.length()").value(1))
        .andExpect(jsonPath("$.entries[0].tradeLines[0].side").value("BUY"))
        .andExpect(
            jsonPath("$.entries[0].tradeLines[0].productId").value(product.getId().toString()));
  }

  @Test
  void typeFilterAndDateRangeNarrowTheEntries() throws Exception {
    saveExpense("Inside", "10.00", DAY);
    saveExpense("Outside", "99.00", DAY.plusMonths(2));
    saveIncome("500.00", DAY);
    saveTransfer("A transfer", "5.00", DAY);

    mockMvc
        .perform(
            ledger()
                .param("type", "EXPENSE")
                .param("dateFrom", DAY.withDayOfMonth(1).toString())
                .param("dateTo", DAY.withDayOfMonth(28).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.entries[0].description").value("Inside"))
        .andExpect(jsonPath("$.summary.expense").value(10.00))
        .andExpect(jsonPath("$.summary.income").value(0));

    mockMvc
        .perform(ledger().param("type", "TRANSFER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.entries[0].type").value("TRANSFER"));
  }

  @Test
  void aCategoryFilterExcludesTransfersAndTransferTypeWithACategoryIsEmptyNot400()
      throws Exception {
    saveExpense("Groceries", "10.00", DAY);
    saveTransfer("A transfer", "5.00", DAY);

    mockMvc
        .perform(ledger().param("categoryId", expenseCategoryId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.entries[0].type").value("EXPENSE"));

    mockMvc
        .perform(
            ledger().param("type", "TRANSFER").param("categoryId", expenseCategoryId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(0))
        .andExpect(jsonPath("$.truncated").value(false));
  }

  @Test
  void anAccountFilterMatchesEitherSideOfATransfer() throws Exception {
    saveTransfer("A transfer", "5.00", DAY);

    mockMvc
        .perform(get("/api/ledger").param("accountId", savingsId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(1));
  }

  @Test
  void aBadDateEnumOrUuidIs400() throws Exception {
    List<String[]> badParams =
        List.of(
            new String[] {"dateFrom", "not-a-date"},
            new String[] {"dateTo", "2026-13-45"},
            new String[] {"type", "BOGUS"},
            new String[] {"accountId", "not-a-uuid"},
            new String[] {"categoryId", "nope"},
            new String[] {"paymentMethodId", "nope"});
    for (String[] param : badParams) {
      mockMvc
          .perform(get("/api/ledger").param(param[0], param[1]))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.type").value("urn:my-finances:error:method-argument-type-mismatch"));
    }
  }

  @Test
  void qSearchesBothSidesIgnoringCaseAndAccentsAndTheSummaryFollows() throws Exception {
    saveExpense("Café da manhã", "12.00", DAY);
    saveExpense("Aluguel", "900.00", DAY);
    Transfer transfer = saveTransfer("Fatura do cafe", "40.00", DAY);

    mockMvc
        .perform(ledger().param("q", "  CAFÉ "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(2))
        .andExpect(jsonPath("$.entries[?(@.id == '" + transfer.getId() + "')]").exists())
        .andExpect(jsonPath("$.summary.expense").value(12.00));
  }

  @Test
  void aBlankQIsIgnored() throws Exception {
    saveExpense("One", "1.00", DAY);
    saveExpense("Two", "1.00", DAY);

    mockMvc
        .perform(ledger().param("q", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(2));
  }

  /**
   * F037 open question, confirmed: a {@code @Size} violation directly on the {@code q} parameter
   * surfaces as {@code HandlerMethodValidationException}, which the single advice maps to a 400
   * with that exact kebab-cased slug (the frontend maps the same slug).
   */
  @Test
  void aQLongerThanTheLimitIs400WithTheHandlerMethodValidationType() throws Exception {
    mockMvc
        .perform(ledger().param("q", "a".repeat(TextFieldConstraints.MAX_SEARCH_TEXT_LENGTH + 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:my-finances:error:handler-method-validation"))
        .andExpect(jsonPath("$.detail").doesNotExist());

    mockMvc
        .perform(ledger().param("q", "a".repeat(TextFieldConstraints.MAX_SEARCH_TEXT_LENGTH)))
        .andExpect(status().isOk());
  }

  @Test
  void anUnauthenticatedRequestIs401() throws Exception {
    MockMvc loggedOut = MockMvcSupport.buildUnauthenticated(webApplicationContext);

    loggedOut.perform(get("/api/ledger")).andExpect(status().isUnauthorized());
  }
}
