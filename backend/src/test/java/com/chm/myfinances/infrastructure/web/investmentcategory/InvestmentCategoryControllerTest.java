package com.chm.myfinances.infrastructure.web.investmentcategory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
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
 * REST-layer integration test for {@link InvestmentCategoryController}, against a real
 * Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc} (Spring Boot 4.x removed the
 * test-slice annotations). The migration's seeded categories are always present, so fixtures use a
 * {@code " Test"} suffix and no test assumes the list is empty.
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were removed - every row is now
 * fixed, migration-seeded content, reachable only by {@code GET}. Fixtures are inserted directly
 * through the repository rather than via a (now nonexistent) {@code POST}.
 */
@WebIntegrationTest
class InvestmentCategoryControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  private InvestmentCategory saveCategory(String name) {
    return categoryRepository.save(InvestmentCategory.reconstitute(UUID.randomUUID(), name));
  }

  @Test
  void listNestsSubcategoriesAndIncludesTheSeed() throws Exception {
    InvestmentCategory category = saveCategory("Nested Test");
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), category.getId(), "Child B Test"));
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), category.getId(), "Child A Test"));
    String id = category.getId().toString();

    mockMvc
        .perform(get("/api/investment-categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + id + "')].subcategories[0].name").value("Child A Test"))
        .andExpect(jsonPath("$[?(@.id=='" + id + "')].subcategories[1].name").value("Child B Test"))
        // The seed: Fixed Income exists and carries CDB; Crypto exists with no sub-categories.
        .andExpect(
            jsonPath("$[?(@.name=='Fixed Income')].subcategories[?(@.name=='CDB')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Crypto')]").exists())
        .andExpect(jsonPath("$[?(@.name=='Crypto')].subcategories[0]").doesNotExist());
  }

  @Test
  void listReturnsACategoryWithNoSubcategoriesAsAnEmptyArray() throws Exception {
    InvestmentCategory category = saveCategory("No Children Test");

    mockMvc
        .perform(get("/api/investment-categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + category.getId() + "')].subcategories").isArray())
        .andExpect(
            jsonPath("$[?(@.id=='" + category.getId() + "')].subcategories[0]").doesNotExist());
  }

  @Test
  void postIsNoLongerSupported() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Nope Test"))))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void patchIsNoLongerSupported() throws Exception {
    InvestmentCategory category = saveCategory("Immutable Test");

    mockMvc
        .perform(
            patch("/api/investment-categories/" + category.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Test"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteIsNoLongerSupported() throws Exception {
    InvestmentCategory category = saveCategory("Undeletable Test");

    mockMvc
        .perform(delete("/api/investment-categories/" + category.getId()))
        .andExpect(status().isNotFound());
  }
}
