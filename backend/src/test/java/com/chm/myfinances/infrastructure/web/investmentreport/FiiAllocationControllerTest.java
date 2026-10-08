package com.chm.myfinances.infrastructure.web.investmentreport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link FiiAllocationController}, against a real Testcontainers
 * Postgres (ADR 0010), hand-built {@link MockMvc} (F026 spec). The underlying computation is
 * covered thoroughly by {@code FiiAllocationQueryTest}; this just proves the four basis/groupBy
 * combinations are wired up and return 200.
 */
@WebIntegrationTest
class FiiAllocationControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void actualByTickerReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "ACTUAL").param("groupBy", "TICKER"))
        .andExpect(status().isOk());
  }

  @Test
  void actualBySegmentReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "ACTUAL").param("groupBy", "SEGMENT"))
        .andExpect(status().isOk());
  }

  @Test
  void plannedByTickerReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "PLANNED").param("groupBy", "TICKER"))
        .andExpect(status().isOk());
  }

  @Test
  void plannedBySegmentReturns200() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("basis", "PLANNED").param("groupBy", "SEGMENT"))
        .andExpect(status().isOk());
  }

  @Test
  void missingBasisReturns400() throws Exception {
    mockMvc
        .perform(get("/api/fii/allocation").param("groupBy", "TICKER"))
        .andExpect(status().isBadRequest());
  }
}
