package com.chm.myfinances.infrastructure.web.investmentsegment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link InvestmentSegmentController}, against a real
 * Testcontainers Postgres (ADR 0010), hand-built {@link MockMvc} (F026 spec).
 */
@WebIntegrationTest
class InvestmentSegmentControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  private String createSegment(String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/investment-segments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonSupport.idOf(result);
  }

  @Test
  void createReturnsTheSegment() throws Exception {
    mockMvc
        .perform(
            post("/api/investment-segments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Shoppings Test"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Shoppings Test"));
  }

  @Test
  void listIncludesCreatedSegments() throws Exception {
    String id = createSegment("Logistica Test");

    mockMvc
        .perform(get("/api/investment-segments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());
  }

  @Test
  void renameReturnsTheUpdatedSegment() throws Exception {
    String id = createSegment("Rename Me Test");

    mockMvc
        .perform(
            patch("/api/investment-segments/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed Test"));
  }

  @Test
  void renameOfUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(
            patch("/api/investment-segments/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Anything Test"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createAndRenameRejectADuplicateNameWith409() throws Exception {
    createSegment("Taken Test");
    String other = createSegment("Other Test");

    mockMvc
        .perform(
            post("/api/investment-segments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Taken Test"))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            patch("/api/investment-segments/" + other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Taken Test"))))
        .andExpect(status().isConflict());
  }

  private static Stream<Arguments> blankAndTooLongNames() {
    return Stream.of(
        Arguments.of("blank", " "),
        Arguments.of("too long", "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("blankAndTooLongNames")
  void createRejectsBlankAndTooLongNamesWith400(String label, String name) throws Exception {
    mockMvc
        .perform(
            post("/api/investment-segments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteRemovesAnUnreferencedSegment() throws Exception {
    String id = createSegment("Delete Me Test");

    mockMvc.perform(delete("/api/investment-segments/" + id)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/investment-segments"))
        .andExpect(jsonPath("$[?(@.id=='" + id + "')]").doesNotExist());
  }

  @Test
  void deleteOfUnknownIdReturns404() throws Exception {
    mockMvc
        .perform(delete("/api/investment-segments/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteIsRejectedWith409WhileAProductUsesIt() throws Exception {
    UUID categoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Variable Income Segment Test"))
            .getId();
    String segmentId = createSegment("In Use Test");
    productRepository.save(
        InvestmentProductMother.product()
            .withInvestmentCategoryId(categoryId)
            .withInvestmentSubcategoryId(null)
            .withName("KNRI11 Segment Test")
            .withSegmentId(UUID.fromString(segmentId))
            .build());

    mockMvc
        .perform(delete("/api/investment-segments/" + segmentId))
        .andExpect(status().isConflict());
  }
}
