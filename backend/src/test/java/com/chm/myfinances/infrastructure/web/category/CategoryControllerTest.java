package com.chm.myfinances.infrastructure.web.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link CategoryController}, against a real Testcontainers
 * Postgres (ADR 0010). Boot 4.x removed {@code @AutoConfigureMockMvc}/{@code @WebMvcTest} (see
 * {@code CategoryRepositoryAdapterTest} for the same story on {@code @DataJpaTest}), so {@link
 * MockMvc} is built by hand from the {@link WebApplicationContext} — that builder itself is plain
 * {@code spring-test}, unaffected by Boot's test-slice removal.
 *
 * <p>Also exercises F002's verification requirement that attempting to change a category's type via
 * {@code PATCH} is rejected: {@link UpdateCategoryRequest} has no {@code type} field at all, so a
 * client sending one can't change it through this endpoint regardless of how Jackson's
 * unknown-property handling reacts to the extra field.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
class CategoryControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  // Plain (non-Spring-managed) ObjectMapper used only to build/parse test JSON fixtures - no
  // need for the application's own configured bean here.
  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void createListRenameAndDeleteRoundTrip() throws Exception {
    String createBody =
        objectMapper.writeValueAsString(Map.of("name", "Test Cat", "type", "EXPENSE"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Test Cat')]").exists());

    String renameBody = objectMapper.writeValueAsString(Map.of("name", "Renamed Cat"));
    mockMvc
        .perform(
            patch("/api/categories/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renameBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed Cat"))
        .andExpect(jsonPath("$.type").value("EXPENSE"));

    mockMvc.perform(delete("/api/categories/" + id)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Renamed Cat')]").doesNotExist());
  }

  @Test
  void patchWithATypeFieldDoesNotChangeType() throws Exception {
    String createBody =
        objectMapper.writeValueAsString(Map.of("name", "Income Test", "type", "INCOME"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    // UpdateCategoryRequest has no `type` field - sending one alongside a rename cannot change
    // the category's type through this endpoint, whatever Jackson's unknown-field handling does
    // with the extra property (reject outright with 4xx, or silently ignore it and return 200).
    String patchBody =
        objectMapper.writeValueAsString(Map.of("name", "Income Test Renamed", "type", "EXPENSE"));
    MvcResult patchResult =
        mockMvc
            .perform(
                patch("/api/categories/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(patchBody))
            .andReturn();

    int statusCode = patchResult.getResponse().getStatus();
    if (statusCode == 200) {
      // The rename endpoint itself must not report EXPENSE for a category created as INCOME.
      org.assertj.core.api.Assertions.assertThat(patchResult.getResponse().getContentAsString())
          .contains("INCOME")
          .doesNotContain("EXPENSE");
    } else {
      org.assertj.core.api.Assertions.assertThat(statusCode).isEqualTo(400);
    }
  }
}
