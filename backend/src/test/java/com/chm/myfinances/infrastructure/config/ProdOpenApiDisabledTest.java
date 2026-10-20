package com.chm.myfinances.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Under the {@code prod} profile the OpenAPI document and Swagger UI are not served at all, not
 * merely login-gated: the production stack has no use for them, and an endpoint that is not there
 * cannot leak the API surface. The dev counterpart is {@link DevOpenApiEnabledTest}.
 *
 * <p>The {@code DB_*} placeholders in {@code application-prod.yml} must resolve for the context to
 * start, so this test supplies dummy values; {@code @ServiceConnection} then points the datasource
 * at the Testcontainers Postgres regardless.
 */
@WebIntegrationTest
@ActiveProfiles("prod")
@TestPropertySource(
    properties = {"DB_HOST=unused", "DB_PORT=5432", "DB_NAME=unused", "DB_USER=u", "DB_PASSWORD=p"})
class ProdOpenApiDisabledTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void theOpenApiDocumentIsNotServedEvenToALoggedInSession() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
  }

  @Test
  void swaggerUiIsNotServedEvenToALoggedInSession() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
  }
}
