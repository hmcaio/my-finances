package com.chm.myfinances.infrastructure.web.investmentcategory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.InvestmentProductMother;
import com.chm.myfinances.testsupport.JsonSupport;
import com.chm.myfinances.testsupport.MockMvcSupport;
import com.chm.myfinances.testsupport.TestFixtures;
import com.chm.myfinances.testsupport.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link InvestmentCategoryController}, against a real
 * Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc} (Spring Boot 4.x removed the
 * test-slice annotations). The migration's seeded categories are always present, so fixtures use a
 * {@code " Test"} suffix and no test assumes the list is empty.
 */
@WebIntegrationTest
class InvestmentCategoryControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  private String createCategory(String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/investment-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonSupport.idOf(result);
  }

  @Test
  void createReturnsTheCategoryWithNoSubcategories() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Real Estate Test"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Real Estate Test"))
        .andExpect(jsonPath("$.subcategories").isArray())
        .andExpect(jsonPath("$.subcategories").isEmpty());
  }

  @Test
  void listNestsSubcategoriesAndIncludesTheSeed() throws Exception {
    String id = createCategory("Nested Test");
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), UUID.fromString(id), "Child B Test"));
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), UUID.fromString(id), "Child A Test"));

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
  void renameReturnsTheCategoryWithItsSubcategories() throws Exception {
    String id = createCategory("Rename Me Test");
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), UUID.fromString(id), "Child Test"));

    mockMvc
        .perform(
            patch("/api/investment-categories/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.name").value("Renamed Test"))
        .andExpect(jsonPath("$.subcategories[0].name").value("Child Test"));
  }

  @Test
  void renameOfUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(
            patch("/api/investment-categories/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Anything Test"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createAndRenameRejectADuplicateNameWith409() throws Exception {
    createCategory("Taken Test");
    String other = createCategory("Other Test");

    mockMvc
        .perform(
            post("/api/investment-categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Taken Test"))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            patch("/api/investment-categories/" + other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Taken Test"))))
        .andExpect(status().isConflict());
  }

  @Test
  void createRejectsBlankAndTooLongNamesWith400() throws Exception {
    for (String name : new String[] {" ", "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1)}) {
      mockMvc
          .perform(
              post("/api/investment-categories")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("name", name))))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(
            post("/api/investment-categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteRemovesAnUnreferencedCategory() throws Exception {
    String id = createCategory("Delete Me Test");

    mockMvc.perform(delete("/api/investment-categories/" + id)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/investment-categories"))
        .andExpect(jsonPath("$[?(@.id=='" + id + "')]").doesNotExist());
  }

  @Test
  void deleteOfUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(delete("/api/investment-categories/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteIsRejectedWith409WhileItHasSubcategories() throws Exception {
    String id = createCategory("Has Children Test");
    subcategoryRepository.save(
        InvestmentSubcategory.create(UUID.randomUUID(), UUID.fromString(id), "Child Test"));

    mockMvc.perform(delete("/api/investment-categories/" + id)).andExpect(status().isConflict());
  }

  @Test
  void deleteIsRejectedWith409WhileAProductUsesIt() throws Exception {
    UUID categoryId =
        categoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), "Used Test")).getId();
    UUID accountId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Category Test",
                AccountType.INVESTMENT)
            .getId();
    productRepository.save(
        InvestmentProductMother.product()
            .withAccountId(accountId)
            .withInvestmentCategoryId(categoryId)
            .withInvestmentSubcategoryId(null)
            .withName("Bitcoin Test")
            .build());

    mockMvc
        .perform(delete("/api/investment-categories/" + categoryId))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteWithAMalformedIdReturns400() throws Exception {
    mockMvc
        .perform(delete("/api/investment-categories/not-a-uuid"))
        .andExpect(status().isBadRequest());
  }
}
