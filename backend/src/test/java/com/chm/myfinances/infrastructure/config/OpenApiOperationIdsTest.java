package com.chm.myfinances.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pins the OpenAPI operation ids that {@code npm run generate-api-types} turns into the keys of
 * {@code operations} in the frontend's generated {@code schema.ts}. Springdoc's default numbers
 * duplicate method names by position ({@code delete_8}), so two PRs that each add a controller
 * method regenerate the same id and merge without a conflict.
 */
@WebIntegrationTest
class OpenApiOperationIdsTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  @Test
  void operationIdsAreUniqueAndNameTheControllerAndMethod() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> ids = new ArrayList<>();
    for (JsonNode pathItem : JsonSupport.MAPPER.readTree(body).get("paths")) {
      for (JsonNode operation : pathItem) {
        ids.add(operation.get("operationId").asText());
      }
    }

    assertThat(ids).isNotEmpty().doesNotHaveDuplicates();
    assertThat(ids).allMatch(id -> id.matches("[a-z][A-Za-z0-9]*_[a-z][A-Za-z0-9]*"));
    assertThat(ids).contains("account_delete", "investmentSnapshot_delete");
  }
}
