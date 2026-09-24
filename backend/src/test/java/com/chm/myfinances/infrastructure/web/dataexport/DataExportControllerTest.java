package com.chm.myfinances.infrastructure.web.dataexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer tests for {@link DataExportController} (F013) against the real Testcontainers
 * Postgres, which also proves the JPA adapters honour {@code Pageable.unpaged()}. The shared
 * database holds rows other tests commit, so assertions look for this test's own uniquely named
 * rows and the account filter narrows to this test's account.
 */
@WebIntegrationTest
class DataExportControllerTest {

  private static final List<String> FILES =
      List.of(
          "categories.csv",
          "payment_methods.csv",
          "institutions.csv",
          "accounts.csv",
          "transactions.csv",
          "transfers.csv",
          "budgets.csv",
          "recurring_templates.csv",
          "investment_categories.csv",
          "investment_subcategories.csv",
          "investment_products.csv",
          "investment_snapshots.csv");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private TransactionRepository transactionRepository;

  private MockMvc mockMvc;
  private Account account;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    account =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Export Checking Test",
                TestInstitutions.builtInId(institutionRepository),
                AccountType.CHECKING,
                new BigDecimal("10.00"),
                LocalDate.of(2002, 1, 1)));
    Category category =
        categoryRepository.save(
            Category.create(UUID.randomUUID(), "Export Food Test", CategoryType.EXPENSE));
    PaymentMethod method =
        paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Export Card Test"));
    transactionRepository.save(
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.of(2002, 2, 3),
            new BigDecimal("12.34"),
            category.getId(),
            CategoryType.EXPENSE,
            account.getId(),
            method.getId(),
            null,
            "=HYPERLINK(\"x\")",
            "a, b"));
  }

  private Map<String, String> exportFiles(String... queryParams) throws Exception {
    var request = get("/api/export");
    for (int i = 0; i < queryParams.length; i += 2) {
      request = request.param(queryParams[i], queryParams[i + 1]);
    }
    byte[] body =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.startsWith(
                            "attachment; filename=\"my-finances-export-")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    Map<String, String> files = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
      for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
        files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return files;
  }

  @Test
  void fullExportContainsAllTwelveFilesWithDenormalizedNamesAndSafeText() throws Exception {
    Map<String, String> files = exportFiles();

    assertThat(new ArrayList<>(files.keySet())).isEqualTo(FILES);
    assertThat(files.get("accounts.csv")).contains("Export Checking Test", "No institution");
    assertThat(files.get("transactions.csv"))
        .contains(
            "Export Food Test",
            "Export Card Test",
            "Export Checking Test",
            "'=HYPERLINK(\"\"x\"\")",
            "\"a, b\"");
  }

  @Test
  void accountAndDateFiltersNarrowTransactionsButNotReferenceFiles() throws Exception {
    Map<String, String> files =
        exportFiles("accountId", account.getId().toString(), "dateFrom", "2002-02-01");

    assertThat(files.get("transactions.csv").split("\r\n")).hasSize(2);
    assertThat(files.get("accounts.csv")).contains("Export Checking Test");

    Map<String, String> outside =
        exportFiles("accountId", account.getId().toString(), "dateFrom", "2003-01-01");
    assertThat(outside.get("transactions.csv").split("\r\n")).hasSize(1);
    assertThat(outside.get("accounts.csv")).contains("Export Checking Test");
  }

  @Test
  void aReversedRangeOrABadParameterIsA400() throws Exception {
    mockMvc
        .perform(get("/api/export").param("dateFrom", "2002-03-01").param("dateTo", "2002-01-01"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/export").param("accountId", "not-a-uuid"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/export").param("dateFrom", "nope"))
        .andExpect(status().isBadRequest());
  }
}
