package com.chm.myfinances.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Under the default {@code dev} profile the OpenAPI document and Swagger UI stay available: {@code
 * npm run generate-api-types} reads {@code /v3/api-docs} from a dev backend. The {@code prod}
 * counterpart is {@link ProdOpenApiDisabledTest}.
 */
@WebIntegrationTest
class DevOpenApiEnabledTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void theOpenApiDocumentIsServed() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void swaggerUiIsServed() throws Exception {
    int status = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus();

    assertThat(status).isNotEqualTo(404);
  }
}
