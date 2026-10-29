package com.chm.myfinances.infrastructure.web.investmentsegment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link InvestmentSegmentController}, against a real
 * Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc} (F026 spec).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were removed - every row is now
 * fixed, migration-seeded content (the seeded B3 FII sectors), reachable only by {@code GET}.
 */
@WebIntegrationTest
class InvestmentSegmentControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentSegmentRepository segmentRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void listIncludesTheSeededSegments() throws Exception {
    mockMvc
        .perform(get("/api/investment-segments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Shoppings')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Logística')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Lajes Corporativas')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Fundo de Fundos')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Outros')]").exists());
  }

  @Test
  void postIsNoLongerSupported() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-segments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nope Test"))))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void patchIsNoLongerSupported() throws Exception {
    InvestmentSegment segment =
        segmentRepository.save(InvestmentSegment.reconstitute(UUID.randomUUID(), "Immutable Test"));

    mockMvc
        .perform(
            patch("/api/investment-segments/" + segment.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Test"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteIsNoLongerSupported() throws Exception {
    InvestmentSegment segment =
        segmentRepository.save(
            InvestmentSegment.reconstitute(UUID.randomUUID(), "Undeletable Test"));

    mockMvc
        .perform(delete("/api/investment-segments/" + segment.getId()))
        .andExpect(status().isNotFound());
  }
}
