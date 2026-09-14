package com.chm.myfinances.infrastructure.web.paymentmethod;

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
 * REST-layer integration test for {@link PaymentMethodController}, against a real Testcontainers
 * Postgres (ADR 0010). See {@code CategoryControllerTest} for why {@link MockMvc} is built by hand
 * here instead of via {@code @AutoConfigureMockMvc} (removed in Boot 4.x).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentMethodControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void createListRenameAndDeleteRoundTrip() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "Test PM"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/payment-methods"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Test PM')]").exists());

    String renameBody = objectMapper.writeValueAsString(Map.of("name", "Renamed PM"));
    mockMvc
        .perform(
            patch("/api/payment-methods/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renameBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed PM"));

    mockMvc.perform(delete("/api/payment-methods/" + id)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/payment-methods"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Renamed PM')]").doesNotExist());
  }

  @Test
  void createRejectsNameOverMaxLength() throws Exception {
    String tooLongName = "a".repeat(101);
    String createBody = objectMapper.writeValueAsString(Map.of("name", tooLongName));

    mockMvc
        .perform(
            post("/api/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void renameRejectsNameOverMaxLength() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of("name", "Test PM 2"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    String tooLongName = "a".repeat(101);
    String renameBody = objectMapper.writeValueAsString(Map.of("name", tooLongName));
    mockMvc
        .perform(
            patch("/api/payment-methods/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renameBody))
        .andExpect(status().isBadRequest());
  }
}
