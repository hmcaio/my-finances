package com.chm.myfinances.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * F036: dev's cross-origin cookie flow. The Vite dev server's exact origin may make credentialed
 * calls (preflight included - a preflight carries no cookie, so it must pass before
 * authentication); any other origin may not, which is the reason the list is exact rather than a
 * {@code localhost:*} wildcard.
 */
@WebIntegrationTest
class CorsConfigurationTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.buildUnauthenticated(webApplicationContext);
  }

  @Test
  void theViteDevOriginGetsACredentialedPreflightAnswerWithoutASession() throws Exception {
    mockMvc
        .perform(
            options("/api/auth/login")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  void anotherLocalhostPortIsNotAllowed() throws Exception {
    mockMvc
        .perform(
            options("/api/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }
}
