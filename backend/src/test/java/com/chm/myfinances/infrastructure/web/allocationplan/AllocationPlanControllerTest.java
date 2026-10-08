package com.chm.myfinances.infrastructure.web.allocationplan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer integration test for {@link AllocationPlanController}, against a real Testcontainers
 * Postgres (ADR 0010), hand-built {@link MockMvc} (F026 spec). Uses the FII sub-category the
 * taxonomy migration (V13) already seeds ("REITs (FIIs)" under "Variable Income").
 */
@WebIntegrationTest
class AllocationPlanControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private InvestmentProductRepository productRepository;

  private final ObjectMapper objectMapper = JsonSupport.MAPPER;

  private MockMvc mockMvc;
  private UUID fiiSubcategoryId;
  private UUID variableIncomeId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
    InvestmentSubcategory fii =
        subcategoryRepository.findAll().stream()
            .filter(s -> AllocationPlanService.FII_SUBCATEGORY_NAME.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    fiiSubcategoryId = fii.getId();
    variableIncomeId = fii.getInvestmentCategoryId();
  }

  private UUID fiiProduct(String name) {
    return productRepository
        .save(
            InvestmentProduct.create(
                UUID.randomUUID(), variableIncomeId, fiiSubcategoryId, name, null))
        .getId();
  }

  private String putBody(List<Map<String, Object>> entries, YearMonth effectiveFrom)
      throws Exception {
    return objectMapper.writeValueAsString(
        Map.of("entries", entries, "effectiveFrom", effectiveFrom.toString()));
  }

  private Map<String, Object> entry(UUID productId, String percentage) {
    return Map.of("investmentProductId", productId.toString(), "targetPercentage", percentage);
  }

  @Test
  void getCurrentReturns204WhenNoAllocationHasEverBeenSet() throws Exception {
    mockMvc.perform(get("/api/fii/allocation-plan")).andExpect(status().isNoContent());
  }

  @Test
  void setAllocationSummingTo100IsAccepted() throws Exception {
    UUID knri = fiiProduct("KNRI11 Plan Controller Test");
    UUID hglg = fiiProduct("HGLG11 Plan Controller Test");

    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    putBody(
                        List.of(entry(knri, "60.00"), entry(hglg, "40.00")),
                        YearMonth.of(2026, 3))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(2));

    mockMvc.perform(get("/api/fii/allocation-plan")).andExpect(status().isOk());
  }

  @Test
  void setAllocationSummingTo99IsRejectedWith400() throws Exception {
    UUID knri = fiiProduct("KNRI11 Sum Test");

    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(putBody(List.of(entry(knri, "99.00")), YearMonth.of(2026, 3))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setAllocationWithANonFiiProductIsRejectedWith409() throws Exception {
    UUID nonFii =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), variableIncomeId, null, "PETR4 Plan Controller Test", null))
            .getId();

    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(putBody(List.of(entry(nonFii, "100.00")), YearMonth.of(2026, 3))))
        .andExpect(status().isConflict());
  }

  @Test
  void settingTheSameMonthTwiceReplacesTheSameVersion() throws Exception {
    UUID knri = fiiProduct("KNRI11 Replace Test");
    UUID hglg = fiiProduct("HGLG11 Replace Test");

    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(putBody(List.of(entry(knri, "100.00")), YearMonth.of(2026, 5))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    putBody(
                        List.of(entry(knri, "60.00"), entry(hglg, "40.00")),
                        YearMonth.of(2026, 5))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/fii/allocation-plan/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].entries.length()").value(2));
  }

  @Test
  void aFutureMonthCreatesASeparateVersion() throws Exception {
    UUID knri = fiiProduct("KNRI11 Future Test");
    UUID hglg = fiiProduct("HGLG11 Future Test");

    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(putBody(List.of(entry(knri, "100.00")), YearMonth.of(2026, 1))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(putBody(List.of(entry(hglg, "100.00")), YearMonth.of(2099, 1))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/fii/allocation-plan/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void setAllocationWithADuplicateProductIsRejectedWith400() throws Exception {
    UUID knri = fiiProduct("KNRI11 Duplicate Test");

    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    putBody(
                        List.of(entry(knri, "60.00"), entry(knri, "40.00")),
                        YearMonth.of(2026, 3))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setAllocationWithAnUnknownProductIsRejectedWith404() throws Exception {
    mockMvc
        .perform(
            put("/api/fii/allocation-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    putBody(List.of(entry(UUID.randomUUID(), "100.00")), YearMonth.of(2026, 3))))
        .andExpect(status().isNotFound());
  }
}
