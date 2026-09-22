package com.chm.myfinances.infrastructure.web.investmentsubcategory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.InvestmentProductMother;
import com.chm.myfinances.testsupport.JsonSupport;
import com.chm.myfinances.testsupport.MockMvcSupport;
import com.chm.myfinances.testsupport.TestFixtures;
import com.chm.myfinances.testsupport.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link InvestmentSubcategoryController}, against a real
 * Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc}.
 */
@WebIntegrationTest
class InvestmentSubcategoryControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;
  private UUID categoryId;
  private UUID otherCategoryId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    categoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Parent Test"))
            .getId();
    otherCategoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Other Parent Test"))
            .getId();
  }

  private String body(UUID parentId, String name) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("investmentCategoryId", parentId == null ? null : parentId.toString());
    body.put("name", name);
    return objectMapper.writeValueAsString(body);
  }

  private String createSubcategory(UUID parentId, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/investment-subcategories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(parentId, name)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonSupport.idOf(result);
  }

  private String nameBody(String name) throws Exception {
    return objectMapper.writeValueAsString(Map.of("name", name));
  }

  @Test
  void createReturnsTheSubcategoryUnderItsParent() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-subcategories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(categoryId, "CDB Test")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.investmentCategoryId").value(categoryId.toString()))
        .andExpect(jsonPath("$.name").value("CDB Test"));
  }

  @Test
  void createUnderAnUnknownParentReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-subcategories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "CDB Test")))
        .andExpect(status().isNotFound());
  }

  private static Stream<String> missingParentBlankAndTooLongNameCases() {
    return Stream.of("missing parent", "blank name", "name over the length limit", "empty body");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("missingParentBlankAndTooLongNameCases")
  void createRejectsAMissingParentBlankAndTooLongNamesWith400(String caseName) throws Exception {
    String content =
        switch (caseName) {
          case "missing parent" -> body(null, "CDB Test");
          case "blank name" -> body(categoryId, " ");
          case "name over the length limit" ->
              body(categoryId, "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1));
          case "empty body" -> "{}";
          default -> throw new IllegalArgumentException(caseName);
        };

    mockMvc
        .perform(
            post("/api/investment-subcategories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aDuplicateNameIsRejectedWithinAParentButAllowedUnderAnother() throws Exception {
    createSubcategory(categoryId, "ETFs Test");

    mockMvc
        .perform(
            post("/api/investment-subcategories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(categoryId, "ETFs Test")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/investment-subcategories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(otherCategoryId, "ETFs Test")))
        .andExpect(status().isCreated());
  }

  @Test
  void renameChangesTheNameOnly() throws Exception {
    String id = createSubcategory(categoryId, "Original Test");

    mockMvc
        .perform(
            patch("/api/investment-subcategories/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(nameBody("Renamed Test")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed Test"))
        .andExpect(jsonPath("$.investmentCategoryId").value(categoryId.toString()));
  }

  @Test
  void renameCannotReparentEvenIfTheBodyCarriesAnotherCategory() throws Exception {
    String id = createSubcategory(categoryId, "Stay Put Test");

    mockMvc
        .perform(
            patch("/api/investment-subcategories/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(otherCategoryId, "Stay Put Test")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.investmentCategoryId").value(categoryId.toString()));
  }

  @Test
  void renameRejectsASiblingsNameWith409AndUnknownIdWith404() throws Exception {
    createSubcategory(categoryId, "Taken Test");
    String id = createSubcategory(categoryId, "Free Test");

    mockMvc
        .perform(
            patch("/api/investment-subcategories/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(nameBody("Taken Test")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            patch("/api/investment-subcategories/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(nameBody("Anything Test")))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRemovesAnUnreferencedSubcategory() throws Exception {
    String id = createSubcategory(categoryId, "Delete Me Test");

    mockMvc
        .perform(delete("/api/investment-subcategories/" + id))
        .andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/investment-subcategories/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void deleteIsRejectedWith409WhileAProductUsesIt() throws Exception {
    String id = createSubcategory(categoryId, "Used Test");
    UUID accountId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Subcategory Test",
                AccountType.INVESTMENT)
            .getId();
    productRepository.save(
        InvestmentProductMother.product()
            .withAccountId(accountId)
            .withInvestmentCategoryId(categoryId)
            .withInvestmentSubcategoryId(UUID.fromString(id))
            .withName("CDB Product Test")
            .build());

    mockMvc.perform(delete("/api/investment-subcategories/" + id)).andExpect(status().isConflict());
  }
}
