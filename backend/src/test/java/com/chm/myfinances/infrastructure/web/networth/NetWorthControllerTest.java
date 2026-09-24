package com.chm.myfinances.infrastructure.web.networth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer tests for {@link NetWorthController} (F010), against a real Testcontainers Postgres
 * (ADR 0010). The shared database holds accounts other tests commit, so this test's accounts are
 * opened in 2001 and every assertion looks at 2001 dates only, before any other test's data.
 */
@WebIntegrationTest
class NetWorthControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    account("NW Checking Test", AccountType.CHECKING, "1000.00", LocalDate.of(2001, 1, 15));
    account("NW Card Test", AccountType.CREDIT_CARD, "250.00", LocalDate.of(2001, 2, 10));
    Account closed =
        account("NW Closed Test", AccountType.SAVINGS, "400.00", LocalDate.of(2001, 1, 1));
    closed.close(LocalDate.of(2001, 3, 1));
    accountRepository.save(closed);
  }

  private Account account(String name, AccountType type, String balance, LocalDate opened) {
    return accountRepository.save(
        Account.create(
            UUID.randomUUID(),
            name,
            TestInstitutions.builtInId(institutionRepository),
            type,
            new BigDecimal(balance),
            opened));
  }

  @Test
  void pointReturnsTheThreePartsAndTheNet() throws Exception {
    mockMvc
        .perform(get("/api/net-worth").param("asOf", "2001-02-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2001-02-28"))
        .andExpect(jsonPath("$.assets").value(1400.00))
        .andExpect(jsonPath("$.liabilities").value(250.00))
        .andExpect(jsonPath("$.investments").value(0.00))
        .andExpect(jsonPath("$.netWorth").value(1150.00));
  }

  @Test
  void pointHonoursTheClosedDateAndTheOpeningDate() throws Exception {
    mockMvc
        .perform(get("/api/net-worth").param("asOf", "2001-01-10"))
        .andExpect(jsonPath("$.netWorth").value(400.00));
    mockMvc
        .perform(get("/api/net-worth").param("asOf", "2001-03-01"))
        .andExpect(jsonPath("$.netWorth").value(750.00));
  }

  @Test
  void pointDefaultsToTodayAndRejectsABadDate() throws Exception {
    mockMvc
        .perform(get("/api/net-worth"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value(LocalDate.now().toString()));
    mockMvc
        .perform(get("/api/net-worth").param("asOf", "not-a-date"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void monthlyTrendHasOneMonthEndPointPerMonth() throws Exception {
    mockMvc
        .perform(
            get("/api/net-worth/trend")
                .param("from", "2001-01-01")
                .param("to", "2001-03-31")
                .param("granularity", "MONTH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[*].date").value(contains("2001-01-31", "2001-02-28", "2001-03-31")))
        .andExpect(jsonPath("$[*].netWorth").value(contains(1400.00, 1150.00, 750.00)));
  }

  @Test
  void trendDefaultsToChangeDateGranularity() throws Exception {
    mockMvc
        .perform(get("/api/net-worth/trend").param("from", "2001-01-01").param("to", "2001-12-31"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].date")
                .value(contains("2001-01-01", "2001-01-15", "2001-02-10", "2001-03-01")));
  }

  @Test
  void trendRejectsAReversedRangeAnUnknownGranularityAndABadDateWith400() throws Exception {
    mockMvc
        .perform(get("/api/net-worth/trend").param("from", "2001-03-01").param("to", "2001-01-01"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/net-worth/trend").param("granularity", "WEEK"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/net-worth/trend").param("from", "2001-13-01"))
        .andExpect(status().isBadRequest());
  }
}
