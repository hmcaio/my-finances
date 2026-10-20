package com.chm.myfinances.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Direct test for {@link HealthController}. Built standalone via {@link
 * MockMvcBuilders#standaloneSetup} rather than the usual {@code @WebIntegrationTest} full context -
 * the controller has no dependencies, so there is nothing for a real app context/Testcontainers
 * Postgres to add here.
 */
class HealthControllerTest {

  private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

  @Test
  void healthReturnsUpWithATimestamp() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.timestamp").exists());
  }
}
