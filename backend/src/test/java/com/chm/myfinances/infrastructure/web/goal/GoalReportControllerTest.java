package com.chm.myfinances.infrastructure.web.goal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link GoalReportController} (F039, ADR 0034), against a real
 * Testcontainers Postgres (ADR 0010). The projection maths is {@code GoalProjectionQueryTest}'s
 * job; this adds the HTTP shape, the problem types and wiring to the real database.
 */
@WebIntegrationTest
class GoalReportControllerTest {

  private static final String TYPE_PREFIX = "urn:my-finances:error:";

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  private String createGoal(LocalDate targetDate, String contribution) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Projection Web Test");
    body.put("targetAmount", "1000.00");
    if (targetDate != null) {
      body.put("targetDate", targetDate.toString());
    }
    body.put("plannedMonthlyContribution", contribution);
    body.put("returnPessimistic", "0");
    body.put("returnBase", "0");
    body.put("returnOptimistic", "0");
    return JsonSupport.idOf(
        mockMvc
            .perform(
                post("/api/goals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JsonSupport.toJson(body)))
            .andExpect(status().isCreated())
            .andReturn());
  }

  @Test
  void aDatedGoalReturnsThreeScenariosWithTheShortfallAndRequiredContribution() throws Exception {
    YearMonth in12 = YearMonth.now().plusMonths(12);
    String id = createGoal(in12.atDay(15), "50");

    mockMvc
        .perform(get("/api/goals/" + id + "/projection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allocatedValue").value(0))
        .andExpect(jsonPath("$.latestSnapshotDate").doesNotExist())
        .andExpect(jsonPath("$.scenarios.length()").value(3))
        .andExpect(jsonPath("$.scenarios[0].scenario").value("PESSIMISTIC"))
        .andExpect(jsonPath("$.scenarios[1].scenario").value("BASE"))
        .andExpect(jsonPath("$.scenarios[2].scenario").value("OPTIMISTIC"))
        .andExpect(jsonPath("$.scenarios[1].points.length()").value(13))
        .andExpect(jsonPath("$.scenarios[1].points[0].month").value(YearMonth.now().toString()))
        .andExpect(jsonPath("$.scenarios[1].projectedValueAtTargetDate").value(600.00))
        .andExpect(jsonPath("$.scenarios[1].shortfallAtTargetDate").value(400.00))
        .andExpect(jsonPath("$.scenarios[1].requiredMonthlyContribution").value(83.34))
        .andExpect(jsonPath("$.scenarios[1].monthsToTarget").doesNotExist());
  }

  @Test
  void aDatelessGoalReturnsMonthsToTargetInsteadOfTheDatedFigures() throws Exception {
    String id = createGoal(null, "100");

    mockMvc
        .perform(get("/api/goals/" + id + "/projection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenarios[1].monthsToTarget").value(10))
        .andExpect(jsonPath("$.scenarios[1].points.length()").value(11))
        .andExpect(jsonPath("$.scenarios[1].projectedValueAtTargetDate").doesNotExist())
        .andExpect(jsonPath("$.scenarios[1].requiredMonthlyContribution").doesNotExist());
  }

  @Test
  void anUnknownGoalIs404() throws Exception {
    mockMvc
        .perform(get("/api/goals/" + UUID.randomUUID() + "/projection"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_PREFIX + "goal-not-found"));
  }
}
