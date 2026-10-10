package com.chm.myfinances.infrastructure.web.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chm.myfinances.testsupport.web.JsonSupport;
import com.chm.myfinances.testsupport.web.MockMvcSupport;
import com.chm.myfinances.testsupport.web.WebIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST-layer tests for {@link ReportController} (F030 spec): a single selected type returns a bare
 * file with the right content type/filename, several selected types return a zip, and an
 * empty/missing {@code types} list is {@code 400}.
 */
@WebIntegrationTest
class ReportControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcSupport.build(webApplicationContext);
  }

  private String body(List<String> types) throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("types", types);
    return JsonSupport.toJson(request);
  }

  @Test
  void aSingleTypeReturnsABareCsvFile() throws Exception {
    mockMvc
        .perform(
            post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of("ANNUAL_SUMMARY"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/csv"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    Matchers.startsWith("attachment; filename=\"my-finances-annual-summary-")));
  }

  @Test
  void twoTypesReturnAZip() throws Exception {
    mockMvc
        .perform(
            post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of("ANNUAL_SUMMARY", "DIVIDEND_INCOME"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/zip"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    Matchers.startsWith("attachment; filename=\"my-finances-reports-")));
  }

  @Test
  void aTypeWhoseGeneratorYieldsCsvAndPdfIsAZipEvenAlone() throws Exception {
    mockMvc
        .perform(
            post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of("FII_PORTFOLIO"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/zip"));
  }

  @Test
  void anEmptyOrMissingTypesListIs400() throws Exception {
    mockMvc
        .perform(
            post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body(List.of())))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anUnknownTypeIs400() throws Exception {
    mockMvc
        .perform(
            post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of("NOT_A_TYPE"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getApiExportNoLongerExists() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/export"))
        .andExpect(status().isNotFound());
  }
}
